package org.koitharu.kotatsu.download.ui.list

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.dialog.showConfirmDialog
import org.koitharu.kotatsu.core.ui.util.MenuInvalidator
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityDownloadsBinding
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import javax.inject.Inject

@AndroidEntryPoint
class DownloadsActivity : BaseActivity<ActivityDownloadsBinding>() {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var scheduler: DownloadWorker.Scheduler

	private val viewModel by viewModels<DownloadsViewModel>()
	private val bottomInset = mutableIntStateOf(0)
	private var selectedIds by mutableStateOf(emptySet<Long>())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDownloadsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		addMenuProvider(DownloadsMenuProvider(this, viewModel))

		val menuInvalidator = MenuInvalidator(this)
		viewModel.hasActiveWorks.observe(this, menuInvalidator)
		viewModel.hasPausedWorks.observe(this, menuInvalidator)
		viewModel.hasFailedWorks.observe(this, menuInvalidator)
		viewModel.hasCancellableWorks.observe(this, menuInvalidator)
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.composeView))

		viewBinding.composeView.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		viewBinding.composeView.setContent {
			KurohaTheme {
				val density = LocalDensity.current
				val items by viewModel.filteredUiItems.collectAsState()
				val summary by viewModel.summary.collectAsState()
				val selectedFilter by viewModel.selectedFilter.collectAsState()
				val filterCounts by viewModel.filterCounts.collectAsState()
				val allDownloads by viewModel.allDownloads.collectAsState()

				DownloadsScreen(
					items = items,
					summary = summary,
					selectedFilter = selectedFilter,
					filterCounts = filterCounts,
					selectedIds = selectedIds,
					coil = coil,
					bottomInset = with(density) { bottomInset.intValue.toDp() },
					onSelectFilter = viewModel::setFilter,
					onItemClick = { item ->
						val id = item.id.mostSignificantBits
						if (selectedIds.isNotEmpty()) {
							selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
						} else {
							router.openDetails(item.manga ?: return@DownloadsScreen)
						}
					},
					onItemLongClick = { item ->
						val id = item.id.mostSignificantBits
						selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
					},
					onExpandClick = viewModel::expandCollapse,
					onPauseClick = { scheduler.pause(it.id) },
					onResumeClick = { scheduler.resume(it.id) },
					onRetryClick = { viewModel.retry(it.id) },
					onCancelClick = { viewModel.cancel(it.id) },
					onTogglePriority = viewModel::togglePriority,
					onPauseAll = viewModel::pauseAll,
					onResumeAll = viewModel::resumeAll,
					onRetryAllFailed = viewModel::retryAllFailed,
					onCancelAll = {
						showConfirmDialog(
							context = this@DownloadsActivity,
							icon = R.drawable.ic_cancel_multiple,
							title = getString(R.string.cancel_all),
							message = getString(R.string.cancel_all_downloads_confirm),
							confirmLabel = getString(R.string.confirm),
							destructive = true,
						) { viewModel.cancelAll() }
					},
					onBatchPause = {
						viewModel.pause(selectedIds)
						selectedIds = emptySet()
					},
					onBatchResume = {
						viewModel.resume(selectedIds)
						selectedIds = emptySet()
					},
					onBatchPrioritize = {
						viewModel.setPriority(selectedIds, isHigh = true)
						selectedIds = emptySet()
					},
					onBatchCancel = {
						viewModel.cancel(selectedIds)
						selectedIds = emptySet()
					},
					onBatchRemove = {
						viewModel.remove(selectedIds)
						selectedIds = emptySet()
					},
					onSelectAll = {
						selectedIds = viewModel.allIds()
					},
					onClearSelection = {
						selectedIds = emptySet()
					},
					onOpenDownloadSettings = {
						router.openDownloadsSetting()
					},
					allDownloads = allDownloads.orEmpty(),
				)
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		bottomInset.intValue = bars.bottom
		with(viewBinding.composeView) {
			if (paddingLeft != bars.left || paddingRight != bars.right) {
				updatePadding(
					left = bars.left,
					right = bars.right,
				)
			}
		}
		with(viewBinding.appbar) {
			if (paddingLeft != bars.left || paddingRight != bars.right || paddingTop != bars.top) {
				updatePadding(
					left = bars.left,
					right = bars.right,
					top = bars.top,
				)
			}
		}
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}
}
