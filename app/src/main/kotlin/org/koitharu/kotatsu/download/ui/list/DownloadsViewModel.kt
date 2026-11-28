package org.koitharu.kotatsu.download.ui.list

import androidx.collection.ArrayMap
import androidx.collection.LongSet
import androidx.collection.LongSparseArray
import androidx.collection.getOrElse
import androidx.collection.set
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.ui.model.DateTimeAgo
import org.koitharu.kotatsu.core.ui.util.ReversibleAction
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.calculateTimeAgo
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.isEmpty
import org.koitharu.kotatsu.download.domain.DownloadState
import org.koitharu.kotatsu.download.ui.list.chapters.DownloadChapter
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.list.ui.model.EmptyState
import org.koitharu.kotatsu.list.ui.model.ListHeader
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.LoadingState
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.LinkedList
import java.util.UUID
import javax.inject.Inject

enum class DownloadFilter {
	ALL,
	ACTIVE,
	PAUSED,
	FAILED,
	COMPLETED,
}

data class DownloadsSummary(
	val activeCount: Int = 0,
	val queuedCount: Int = 0,
	val pausedCount: Int = 0,
	val failedCount: Int = 0,
	val completedCount: Int = 0,
	val overallProgress: Float = 0f,
	val hasIndeterminate: Boolean = false,
	val eta: Long = -1L,
) {
	val hasOngoing: Boolean
		get() = activeCount > 0 || queuedCount > 0 || pausedCount > 0 || failedCount > 0
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
	private val workScheduler: DownloadWorker.Scheduler,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
	private val localMangaRepository: LocalMangaRepository,
) : BaseViewModel() {

	private val mangaCache = LongSparseArray<Manga>()
	private val cacheMutex = Mutex()
	private val expanded = MutableStateFlow(emptySet<UUID>())
	private val chaptersCache = ArrayMap<UUID, StateFlow<List<DownloadChapter>?>>()
	val selectedFilter = MutableStateFlow(DownloadFilter.ALL)

	private val works = combine(
		workScheduler.observeWorks(),
		expanded,
	) { list, exp ->
		list.toDownloadsList(exp)
	}.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val onActionDone = MutableEventFlow<ReversibleAction>()

	val items = works.map {
		it?.toUiList() ?: listOf(LoadingState())
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState()))

	val hasPausedWorks = works.map {
		it?.any { x -> x.canResume } == true
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), false)

	val hasFailedWorks = works.map {
		it?.any { x -> x.canRetry } == true
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), false)

	val hasActiveWorks = works.map {
		it?.any { x -> x.canPause } == true
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), false)

	val hasCancellableWorks = works.map {
		it?.any { x -> !x.workState.isFinished } == true
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), false)

	val allDownloads: StateFlow<List<DownloadItemModel>?> = works

	val filterCounts: StateFlow<Map<DownloadFilter, Int>> = works.map { list ->
		if (list == null) {
			emptyMap()
		} else {
			mapOf(
				DownloadFilter.ALL to list.size,
				DownloadFilter.ACTIVE to list.count { it.canPause || it.workState == WorkInfo.State.ENQUEUED || it.workState == WorkInfo.State.BLOCKED },
				DownloadFilter.PAUSED to list.count { it.canResume },
				DownloadFilter.FAILED to list.count { it.canRetry },
				DownloadFilter.COMPLETED to list.count { it.workState == WorkInfo.State.SUCCEEDED || it.workState == WorkInfo.State.CANCELLED },
			)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), emptyMap())

	val summary: StateFlow<DownloadsSummary> = works.map { list ->
		if (list.isNullOrEmpty()) {
			DownloadsSummary()
		} else {
			var active = 0
			var queued = 0
			var paused = 0
			var failed = 0
			var completed = 0
			var totalMax = 0L
			var totalProgress = 0L
			var anyIndeterminate = false
			var maxEta = -1L
			for (item in list) {
				when {
					item.canPause -> {
						active++
						if (item.isIndeterminate || item.max <= 0) {
							anyIndeterminate = true
						} else {
							totalMax += item.max
							totalProgress += item.progress.coerceIn(0, item.max)
						}
						if (item.eta > maxEta) maxEta = item.eta
					}
					item.workState == WorkInfo.State.ENQUEUED || item.workState == WorkInfo.State.BLOCKED -> queued++
					item.canResume -> paused++
					item.canRetry -> failed++
					else -> completed++
				}
			}
			val overall = if (totalMax > 0L) (totalProgress.toFloat() / totalMax.toFloat()).coerceIn(0f, 1f) else 0f
			DownloadsSummary(
				activeCount = active,
				queuedCount = queued,
				pausedCount = paused,
				failedCount = failed,
				completedCount = completed,
				overallProgress = overall,
				hasIndeterminate = anyIndeterminate && totalMax == 0L,
				eta = maxEta,
			)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), DownloadsSummary())

	val filteredUiItems: StateFlow<List<ListModel>?> = combine(works, selectedFilter) { list, filter ->
		list?.filter { item ->
			when (filter) {
				DownloadFilter.ALL -> true
				DownloadFilter.ACTIVE -> item.canPause || item.workState == WorkInfo.State.ENQUEUED || item.workState == WorkInfo.State.BLOCKED
				DownloadFilter.PAUSED -> item.canResume
				DownloadFilter.FAILED -> item.canRetry
				DownloadFilter.COMPLETED -> item.workState == WorkInfo.State.SUCCEEDED || item.workState == WorkInfo.State.CANCELLED
			}
		}?.toUiList()
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), null)

	fun setFilter(filter: DownloadFilter) {
		selectedFilter.value = filter
	}

	fun togglePriority(item: DownloadItemModel) {
		launchJob(Dispatchers.Default) {
			val newPriority = if (item.isHighPriority) DownloadTask.PRIORITY_NORMAL else DownloadTask.PRIORITY_HIGH
			workScheduler.setPriority(item.id, newPriority)
		}
	}

	fun setPriority(ids: Set<Long>, isHigh: Boolean) {
		launchJob(Dispatchers.Default) {
			val snapshot = works.value ?: return@launchJob
			val targetPriority = if (isHigh) DownloadTask.PRIORITY_HIGH else DownloadTask.PRIORITY_NORMAL
			for (work in snapshot) {
				if (work.id.mostSignificantBits in ids && work.canPrioritize) {
					workScheduler.setPriority(work.id, targetPriority)
				}
			}
		}
	}

	fun cancel(id: UUID) {
		launchJob(Dispatchers.Default) {
			workScheduler.cancel(id)
		}
	}

	fun retry(id: UUID) {
		launchJob(Dispatchers.Default) {
			workScheduler.resume(id)
		}
	}

	fun retryAllFailed() {
		launchJob(Dispatchers.Default) {
			works.value?.forEach { work ->
				if (work.canRetry) {
					workScheduler.resume(work.id)
				}
			}
		}
	}

	fun cancel(ids: Set<Long>) {
		launchJob(Dispatchers.Default) {
			val snapshot = works.value ?: return@launchJob
			for (work in snapshot) {
				if (work.id.mostSignificantBits in ids) {
					workScheduler.cancel(work.id)
				}
			}
			onActionDone.call(ReversibleAction(R.string.downloads_cancelled, null))
		}
	}

	fun cancelAll() {
		launchJob(Dispatchers.Default) {
			workScheduler.cancelAll()
			onActionDone.call(ReversibleAction(R.string.downloads_cancelled, null))
		}
	}

	fun pause(ids: Set<Long>) {
		val snapshot = works.value ?: return
		for (work in snapshot) {
			if (work.id.mostSignificantBits in ids) {
				workScheduler.pause(work.id)
			}
		}
		onActionDone.call(ReversibleAction(R.string.downloads_paused, null))
	}

	fun pauseAll() {
		val snapshot = works.value ?: return
		var isPaused = false
		for (work in snapshot) {
			if (work.canPause) {
				workScheduler.pause(work.id)
				isPaused = true
			}
		}
		if (isPaused) {
			onActionDone.call(ReversibleAction(R.string.downloads_paused, null))
		}
	}

	fun resumeAll() {
		val snapshot = works.value ?: return
		var isResumed = false
		for (work in snapshot) {
			if (work.workState == WorkInfo.State.RUNNING && work.isPaused) {
				workScheduler.resume(work.id)
				isResumed = true
			}
		}
		if (isResumed) {
			onActionDone.call(ReversibleAction(R.string.downloads_resumed, null))
		}
	}

	fun resume(ids: Set<Long>) {
		val snapshot = works.value ?: return
		for (work in snapshot) {
			if (work.id.mostSignificantBits in ids) {
				workScheduler.resume(work.id)
			}
		}
		onActionDone.call(ReversibleAction(R.string.downloads_resumed, null))
	}

	fun remove(ids: Set<Long>) {
		launchJob(Dispatchers.Default) {
			val snapshot = works.value ?: return@launchJob
			val uuids = HashSet<UUID>(ids.size)
			for (work in snapshot) {
				if (work.id.mostSignificantBits in ids) {
					uuids.add(work.id)
				}
			}
			workScheduler.delete(uuids)
			onActionDone.call(ReversibleAction(R.string.downloads_removed, null))
		}
	}

	fun removeCompleted() {
		launchJob(Dispatchers.Default) {
			workScheduler.removeCompleted()
			onActionDone.call(ReversibleAction(R.string.downloads_removed, null))
		}
	}

	fun snapshot(ids: LongSet): Collection<DownloadItemModel> {
		return works.value?.filterTo(ArrayList(ids.size)) { x -> x.id.mostSignificantBits in ids }.orEmpty()
	}

	fun allIds(): Set<Long> = works.value?.mapToSet {
		it.id.mostSignificantBits
	} ?: emptySet()

	fun expandCollapse(item: DownloadItemModel) {
		expanded.update {
			if (item.id in it) {
				it - item.id
			} else {
				it + item.id
			}
		}
	}

	private suspend fun List<WorkInfo>.toDownloadsList(exp: Set<UUID>): List<DownloadItemModel> {
		if (isEmpty()) {
			return emptyList()
		}
		val list = mapNotNullTo(ArrayList(size)) { it.toUiModel(it.id in exp) }
		list.sortByDescending { it.timestamp }
		return list
	}

	private fun List<DownloadItemModel>.toUiList(): List<ListModel> {
		if (isEmpty()) {
			return emptyStateList()
		}
		val queued = LinkedList<ListModel>()
		val running = LinkedList<ListModel>()
		val destination = ArrayDeque<ListModel>((size * 1.4).toInt())
		var prevDate: DateTimeAgo? = null
		for (item in this) {
			when (item.workState) {
				WorkInfo.State.RUNNING -> running += item
				WorkInfo.State.BLOCKED,
				WorkInfo.State.ENQUEUED -> queued += item

				else -> {
					val date = calculateTimeAgo(item.timestamp)
					if (prevDate != date) {
						destination += if (date != null) {
							ListHeader(date)
						} else {
							ListHeader(R.string.unknown)
						}
					}
					prevDate = date
					destination += item
				}
			}
		}
		if (running.isNotEmpty()) {
			running.addFirst(ListHeader(R.string.in_progress))
		}
		destination.addAll(0, running)
		if (queued.isNotEmpty()) {
			queued.sortByDescending { (it as DownloadItemModel).priority }
			queued.addFirst(ListHeader(R.string.queued))
		}
		destination.addAll(0, queued)
		return destination
	}

	private suspend fun WorkInfo.toUiModel(isExpanded: Boolean): DownloadItemModel? {
		val workData = outputData.takeUnless { it.isEmpty }
			?: progress.takeUnless { it.isEmpty }
			?: workScheduler.getInputData(id)
			?: return null
		val mangaId = DownloadState.getMangaId(workData)
		if (mangaId == 0L) return null
		val manga = getManga(mangaId) ?: return null
		val chapters = synchronized(chaptersCache) {
			chaptersCache.getOrPut(id) {
				observeChapters(manga, id)
			}
		}
		return DownloadItemModel(
			id = id,
			workState = state,
			manga = manga,
			error = DownloadState.getError(workData),
			isIndeterminate = DownloadState.isIndeterminate(workData),
			isPaused = DownloadState.isPaused(workData),
			max = DownloadState.getMax(workData),
			progress = DownloadState.getProgress(workData),
			eta = DownloadState.getEta(workData),
			isStuck = DownloadState.isStuck(workData),
			timestamp = DownloadState.getTimestamp(workData),
			chaptersDownloaded = DownloadState.getDownloadedChapters(workData),
			isExpanded = isExpanded,
			chapters = chapters,
			priority = DownloadState.getPriority(workData),
		)
	}

	private fun emptyStateList() = listOf(
		EmptyState(
			icon = R.drawable.ic_empty_common,
			textPrimary = R.string.text_downloads_list_holder,
			textSecondary = 0,
			actionStringRes = 0,
		),
	)

	private suspend fun getManga(mangaId: Long): Manga? {
		mangaCache[mangaId]?.let {
			return it
		}
		return cacheMutex.withLock {
			mangaCache.getOrElse(mangaId) {
				mangaDataRepository.findMangaById(mangaId, withChapters = true)?.also {
					mangaCache[mangaId] = it
				} ?: return null
			}
		}
	}

	private fun observeChapters(manga: Manga, workId: UUID): StateFlow<List<DownloadChapter>?> = flow {
		val chapterIds = workScheduler.getTask(workId)?.chaptersIds
		val chapters = (tryLoad(manga) ?: manga).chapters ?: return@flow

		suspend fun mapChapters(): List<DownloadChapter> {
			val size = chapterIds?.size ?: chapters.size
			val localChapters =
				localMangaRepository.findSavedManga(manga)?.manga?.chapters?.mapToSet { it.id }.orEmpty()
			return chapters.mapNotNullTo(ArrayList(size)) {
				if (chapterIds == null || it.id in chapterIds) {
					DownloadChapter(
						number = it.numberString(),
						name = it.title.orEmpty(),
						isDownloaded = it.id in localChapters,
					)
				} else {
					null
				}
			}
		}
		emit(mapChapters())
		localStorageChanges.collect {
			if (it?.manga?.id == manga.id) {
				emit(mapChapters())
			}
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	private suspend fun tryLoad(manga: Manga) = runCatchingCancellable {
		mangaRepositoryFactory.create(manga.source).getDetails(manga)
	}.getOrNull()
}
