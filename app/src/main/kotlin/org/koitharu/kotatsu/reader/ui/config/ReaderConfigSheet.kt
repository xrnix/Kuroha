package org.koitharu.kotatsu.reader.ui.config

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.ui.sheet.AdaptiveSheetBehavior
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.ui.sheet.SheetAction
import org.koitharu.kotatsu.core.ui.sheet.SheetActionGrid
import org.koitharu.kotatsu.core.ui.sheet.SheetContentPadding
import org.koitharu.kotatsu.core.ui.sheet.SheetRows
import org.koitharu.kotatsu.core.util.ext.findParentCallback
import org.koitharu.kotatsu.databinding.SheetReaderConfigBinding
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.ui.ReaderViewModel
import org.koitharu.kotatsu.reader.ui.ScreenOrientationHelper
import org.koitharu.kotatsu.settings.compose.InlineSliderSettingsRow
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.NavigationSettingsItem
import org.koitharu.kotatsu.settings.compose.PreviewChoiceRow
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.readerModePreviewOptions
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ReaderConfigSheet : BaseAdaptiveSheet<SheetReaderConfigBinding>() {

	private val viewModel by activityViewModels<ReaderViewModel>()

	@Inject
	lateinit var orientationHelper: ScreenOrientationHelper

	@Inject
	lateinit var mangaRepositoryFactory: MangaRepository.Factory

	@Inject
	lateinit var pageLoader: PageLoader

	@Inject
	lateinit var settings: AppSettings

	private lateinit var mode: ReaderMode
	private lateinit var imageServerDelegate: ImageServerDelegate

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		mode = arguments?.getInt(AppRouter.KEY_READER_MODE)
			?.let { ReaderMode.valueOf(it) }
			?: ReaderMode.STANDARD
		imageServerDelegate = ImageServerDelegate(
			mangaRepositoryFactory = mangaRepositoryFactory,
			mangaSource = viewModel.getMangaOrNull()?.source,
		)
	}

	override fun onStart() {
		// When the reader is immersive, keep system bars hidden while the sheet opens
		val window = dialog?.window
		val activityInsets = activity?.window?.decorView?.let(ViewCompat::getRootWindowInsets)
		val barsHidden = activityInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) == false
		if (window != null && barsHidden) {
			window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
			WindowInsetsControllerCompat(window, window.decorView).apply {
				systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
				hide(WindowInsetsCompat.Type.systemBars())
			}
			super.onStart()
			window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
		} else {
			super.onStart()
		}
	}

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): SheetReaderConfigBinding {
		return SheetReaderConfigBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(
		binding: SheetReaderConfigBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.composeView.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		binding.composeView.setContent {
			KurohaTheme {
				ReaderConfigContent()
			}
		}
		expandToContent()
	}

	private fun expandToContent() {
		val b = behavior ?: return
		if (b is AdaptiveSheetBehavior.Bottom) {
			if (isLandscape()) {
				b.isFitToContents = false
				val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
				if (sheet != null) {
					sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
				}
			} else {
				b.isFitToContents = true
			}
		} else if (b is AdaptiveSheetBehavior.Side) {
			val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
				?: dialog?.findViewById<View>(com.google.android.material.R.id.m3_side_sheet)
			if (sheet != null) {
				val displayWidth = resources.displayMetrics.widthPixels
				sheet.layoutParams.width = (displayWidth * 0.5).toInt()
				sheet.requestLayout()
			}
		}
		b.state = AdaptiveSheetBehavior.STATE_EXPANDED
	}

	private fun isLandscape(): Boolean {
		return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		return insets
	}

	@Composable
	private fun ReaderConfigContent() {
		val navBarPadding = remember {
			dialog?.window?.decorView?.let { decor ->
				ViewCompat.getRootWindowInsets(decor)
					?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
			} ?: 0
		}
		val bottomPadding = 16.dp + with(LocalDensity.current) { navBarPadding.toDp() }
		SheetRows {
			MangaConfigContent(bottomPadding)
		}
	}

	@Composable
	private fun MangaConfigContent(bottomPadding: Dp) {
		val context = LocalContext.current
		val scope = rememberCoroutineScope()
		var currentMode by remember { mutableStateOf(mode) }
		val isBookmarkAdded by viewModel.isBookmarkAdded.collectAsState()
		val isAutoRotationEnabled by orientationHelper.observeAutoOrientation().collectAsState(initial = false)
		val callback = remember { findParentCallback(Callback::class.java) }

		var doubleOnLandscape by remember { mutableStateOf(settings.isReaderDoubleOnLandscape) }
		var doubleLandscapeOnly by remember { mutableStateOf(settings.isReaderDoubleLandscapeOnly) }
		var doubleOnFoldable by remember { mutableStateOf(settings.isReaderDoubleOnFoldable) }
		var doubleCoverPage by remember { mutableStateOf(settings.isReaderDoubleCoverPage) }
		var doubleSensitivity by remember { mutableFloatStateOf(settings.readerDoublePagesSensitivity * 100f) }

		var isImageServerAvailable by remember { mutableStateOf(false) }
		var imageServerName by remember { mutableStateOf<String?>(null) }

		LaunchedEffect(Unit) {
			val available = imageServerDelegate.isAvailable()
			isImageServerAvailable = available
			if (available) {
				imageServerName = imageServerDelegate.getValue()
			}
		}

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = SheetContentPadding)
				.padding(top = 4.dp, bottom = bottomPadding),
		) {
			SettingsGroup(title = stringResource(R.string.read_mode)) {
				item {
					PreviewChoiceRow(
						options = readerModePreviewOptions(READER_MODES.map { stringResource(it.second) }),
						selectedIndex = READER_MODES.indexOfFirst { it.first == currentMode }.coerceAtLeast(0),
						onSelected = { index ->
							val newMode = READER_MODES[index].first
							if (newMode != currentMode) {
								callback?.onReaderModeChanged(newMode)
								currentMode = newMode
								mode = newMode
							}
						},
					)
				}
			}
			Text(
				text = stringResource(R.string.reader_mode_hint),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
			)

			val actions = buildList {
				add(
					SheetAction(
						icon = if (isBookmarkAdded) R.drawable.ic_bookmark_checked else R.drawable.ic_bookmark,
						label = stringResource(if (isBookmarkAdded) R.string.bookmark_remove else R.string.bookmark_add),
						checked = isBookmarkAdded,
					) { viewModel.toggleBookmark() },
				)
				add(
					SheetAction(R.drawable.ic_timer, stringResource(R.string.automatic_scroll)) {
						callback?.onScrollTimerClick(isLongClick = false)
						dismissAllowingStateLoss()
					},
				)
				add(
					SheetAction(R.drawable.ic_save, stringResource(R.string.save_page)) {
						callback?.onSavePageClick()
						dismissAllowingStateLoss()
					},
				)
				add(
					SheetAction(
						icon = if (isAutoRotationEnabled) R.drawable.ic_screen_rotation_lock else R.drawable.ic_screen_rotation,
						label = stringResource(if (isAutoRotationEnabled) R.string.lock_screen_rotation else R.string.rotate_screen),
						checked = isAutoRotationEnabled && orientationHelper.isLocked,
					) {
						if (isAutoRotationEnabled) {
							orientationHelper.isLocked = !orientationHelper.isLocked
						} else {
							orientationHelper.isLandscape = !orientationHelper.isLandscape
						}
					},
				)
				add(
					SheetAction(R.drawable.ic_appearance, stringResource(R.string.color_correction)) {
						val page = viewModel.getCurrentPage()
						val manga = viewModel.getMangaOrNull()
						if (page != null && manga != null) {
							router.openColorFilterConfig(manga, page)
						}
					},
				)
				if (isImageServerAvailable) {
					add(
						SheetAction(
							icon = R.drawable.ic_images,
							label = imageServerName ?: stringResource(R.string.image_server),
						) {
							scope.launch {
								if (imageServerDelegate.showDialog(context)) {
									imageServerName = imageServerDelegate.getValue()
									pageLoader.invalidate(clearCache = true)
									viewModel.switchChapterBy(0)
								}
							}
						},
					)
				}
			}

			SheetActionGrid(
				actions = actions,
				attachedBelow = true,
			)
			Spacer(Modifier.height(2.dp))

			val supportsDouble = currentMode == ReaderMode.STANDARD || currentMode == ReaderMode.REVERSED
			if (supportsDouble) {
				Spacer(Modifier.height(12.dp))
				SettingsGroup {
					item { pos ->
						SwitchSettingsItem(
							title = stringResource(R.string.two_page_layout),
							checked = doubleOnLandscape,
							onCheckedChange = { checked ->
								doubleOnLandscape = checked
								settings.isReaderDoubleOnLandscape = checked
								callback?.onDoubleModeChanged(checked)
							},
							shape = pos.shape,
						)
					}
					if (doubleOnLandscape) {
						item { pos ->
							SwitchSettingsItem(
								title = stringResource(R.string.landscape_only),
								checked = doubleLandscapeOnly,
								onCheckedChange = { checked ->
									doubleLandscapeOnly = checked
									settings.isReaderDoubleLandscapeOnly = checked
									callback?.onDoubleModeChanged(doubleOnLandscape)
								},
								shape = pos.shape,
							)
						}
						item { pos ->
							SwitchSettingsItem(
								title = stringResource(R.string.auto_double_foldable),
								checked = doubleOnFoldable,
								onCheckedChange = { checked ->
									doubleOnFoldable = checked
									settings.isReaderDoubleOnFoldable = checked
									callback?.onDoubleModeChanged(doubleOnLandscape)
								},
								shape = pos.shape,
							)
						}
						item { pos ->
							SwitchSettingsItem(
								title = stringResource(R.string.double_page_cover_page),
								checked = doubleCoverPage,
								onCheckedChange = { checked ->
									doubleCoverPage = checked
									settings.isReaderDoubleCoverPage = checked
									callback?.onDoubleModeChanged(doubleOnLandscape)
								},
								shape = pos.shape,
							)
						}
						item { pos ->
							InlineSliderSettingsRow(
								title = stringResource(R.string.two_page_scroll_sensitivity),
								value = doubleSensitivity.roundToInt(),
								valueFrom = 0,
								valueTo = 100,
								stepSize = 1,
								unitSuffix = "%",
								shape = pos.shape,
								onValueChange = { v ->
									doubleSensitivity = v.toFloat()
									settings.readerDoublePagesSensitivity = v / 100f
								},
							)
						}
					}
				}
				Spacer(Modifier.height(12.dp))
			}

			NavigationSettingsItem(
				title = stringResource(R.string.settings),
				subtitle = stringResource(R.string.reader_settings_summary),
				onClick = {
					router.openReaderSettings()
					dismissAllowingStateLoss()
				},
				icon = R.drawable.ic_settings,
				shape = if (supportsDouble) {
					RoundedCornerShape(24.dp)
				} else {
					RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
				},
			)
		}
	}

	private companion object {
		val READER_MODES = listOf(
			ReaderMode.STANDARD to R.string.standard,
			ReaderMode.REVERSED to R.string.right_to_left,
			ReaderMode.VERTICAL to R.string.vertical,
			ReaderMode.WEBTOON to R.string.webtoon,
		)
	}

	interface Callback {

		fun onReaderModeChanged(mode: ReaderMode)

		fun onDoubleModeChanged(isEnabled: Boolean)

		fun onSavePageClick()

		fun onScrollTimerClick(isLongClick: Boolean)

		fun onBookmarkClick()
	}
}
