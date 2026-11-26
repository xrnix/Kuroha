package org.koitharu.kotatsu.settings.sources.manage

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.LocalizedAppContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.removeObserverAsync
import org.koitharu.kotatsu.core.model.getLocale
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.ui.util.ReversibleAction
import org.koitharu.kotatsu.core.util.LocaleComparator
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.core.util.ext.mapSortedByCount
import org.koitharu.kotatsu.core.util.ext.sortedWithSafe
import org.koitharu.kotatsu.core.util.ext.toLocale
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.move
import org.koitharu.kotatsu.settings.sources.model.SourceConfigItem
import java.util.Locale
import javax.inject.Inject

data class BuiltInSourceEntry(
	val source: ParserMangaSource,
	val title: String,
	val localeLabel: String,
	val contentTypeLabel: String,
	val isEnabled: Boolean,
)

@HiltViewModel
class SourcesManageViewModel @Inject constructor(
	@LocalizedAppContext private val context: android.content.Context,
	private val database: MangaDatabase,
	private val settings: AppSettings,
	private val repository: MangaSourcesRepository,
	private val listProducer: SourcesListProducer,
) : BaseViewModel() {

	val content = listProducer.list
	val onActionDone = MutableEventFlow<ReversibleAction>()
	private var commitJob: Job? = null

	val availableContentTypes: List<ContentType> by lazy {
		repository.allMangaSources.mapSortedByCount { it.contentType }
	}

	val availableLocales: List<Locale> by lazy {
		repository.allMangaSources.groupBy { it.locale.toLocale() }.keys.sortedWithSafe(LocaleComparator())
	}

	val selectedContentTypes: StateFlow<Set<ContentType>> = repository.observeEnabledSources()
		.map { list ->
			list.mapNotNullTo(HashSet()) { (it.mangaSource as? ParserMangaSource)?.contentType }
		}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptySet())

	val selectedLocales: StateFlow<Set<Locale>> = repository.observeEnabledSources()
		.map { list ->
			list.mapNotNullTo(HashSet()) { (it.mangaSource as? ParserMangaSource)?.locale?.toLocale() }
		}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptySet())

	fun applySourceFilters(types: Set<ContentType>, locales: Set<Locale>) {
		viewModelScope.launch(Dispatchers.Default) {
			if (settings.isAllSourcesEnabled) {
				settings.isAllSourcesEnabled = false
			}
			val languages = locales.mapToSet { it.language }
			val enabledSources = repository.allMangaSources.filterTo(HashSet<ParserMangaSource>()) { x ->
				x.contentType in types && x.locale in languages
			}
			repository.setSourcesEnabledExclusive(enabledSources)
		}
	}

	val allBuiltInSources: StateFlow<List<BuiltInSourceEntry>> = combine(
		listProducer.list,
		repository.observeEnabledSources(),
	) { _, _ ->
		val enabledNames = repository.getEnabledSources().mapTo(HashSet()) { it.name }
		repository.allMangaSources
			.map { source ->
				BuiltInSourceEntry(
					source = source,
					title = source.title,
					localeLabel = source.getLocale()?.getDisplayName(context) ?: context.getString(R.string.various_languages),
					contentTypeLabel = context.getString(source.contentType.titleResId),
					isEnabled = source.name in enabledNames,
				)
			}
			.sortedBy { it.title.lowercase() }
	}
		.flowOn(Dispatchers.Default)
		.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

	init {
		launchJob(Dispatchers.Default) {
			database.invalidationTracker.addObserver(listProducer)
		}
	}

	override fun onCleared() {
		super.onCleared()
		database.invalidationTracker.removeObserverAsync(listProducer)
	}

	fun saveSourcesOrder(snapshot: List<SourceConfigItem>) {
		val prevJob = commitJob
		commitJob = launchJob(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			val newSourcesList = snapshot.mapNotNull { x ->
				if (x is SourceConfigItem.SourceItem && x.isDraggable) {
					x.source
				} else {
					null
				}
			}
			repository.setPositions(newSourcesList)
		}
	}

	fun canReorder(oldPos: Int, newPos: Int): Boolean {
		val snapshot = content.value
		val oldPosItem = snapshot.getOrNull(oldPos) as? SourceConfigItem.SourceItem ?: return false
		val newPosItem = snapshot.getOrNull(newPos) as? SourceConfigItem.SourceItem ?: return false
		return oldPosItem.isEnabled && newPosItem.isEnabled && oldPosItem.isPinned == newPosItem.isPinned
	}

	fun setEnabled(source: MangaSource, isEnabled: Boolean) {
		launchJob(Dispatchers.Default) {
			val rollback = repository.setSourcesEnabled(setOf(source), isEnabled)
			if (!isEnabled) {
				onActionDone.call(ReversibleAction(R.string.source_disabled, rollback))
			}
		}
	}

	fun enableBatch(sources: List<ParserMangaSource>) {
		if (sources.isEmpty()) return
		launchJob(Dispatchers.Default) {
			repository.setSourcesEnabled(sources.toSet(), true)
		}
	}

	fun setPinned(source: MangaSource, isPinned: Boolean) {
		launchJob(Dispatchers.Default) {
			val rollback = repository.setIsPinned(setOf(source), isPinned)
			val message = if (isPinned) R.string.source_pinned else R.string.source_unpinned
			onActionDone.call(ReversibleAction(message, rollback))
		}
	}

	fun bringToTop(source: MangaSource) {
		val snapshot = content.value
		launchJob(Dispatchers.Default) {
			var oldPos = -1
			var newPos = -1
			for ((i, x) in snapshot.withIndex()) {
				if (x !is SourceConfigItem.SourceItem) {
					continue
				}
				if (newPos == -1) {
					newPos = i
				}
				if (x.source == source) {
					oldPos = i
					break
				}
			}
			@Suppress("KotlinConstantConditions")
			if (oldPos != -1 && newPos != -1) {
				reorderSources(oldPos, newPos)
				val revert = ReversibleAction(R.string.moved_to_top) {
					reorderSources(newPos, oldPos)
				}
				commitJob?.join()
				onActionDone.call(revert)
			}
		}
	}

	fun disableAll() {
		launchJob(Dispatchers.Default) {
			repository.disableAllSources()
		}
	}

	fun performSearch(query: String?) {
		listProducer.setQuery(query?.trim().orEmpty())
	}

	fun onTipClosed(item: SourceConfigItem.Tip) {
		launchJob(Dispatchers.Default) {
			settings.closeTip(item.key)
		}
	}

	private fun reorderSources(oldPos: Int, newPos: Int) {
		val snapshot = content.value.toMutableList()
		if ((snapshot[oldPos] as? SourceConfigItem.SourceItem)?.isDraggable != true) {
			return
		}
		if ((snapshot[newPos] as? SourceConfigItem.SourceItem)?.isDraggable != true) {
			return
		}
		snapshot.move(oldPos, newPos)
		saveSourcesOrder(snapshot)
	}
}
