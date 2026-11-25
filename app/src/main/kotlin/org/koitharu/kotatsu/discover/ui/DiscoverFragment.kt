package org.koitharu.kotatsu.discover.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.slider.TickVisibilityMode
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.ui.BaseFragment
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.dialog.DialogCornerAction
import org.koitharu.kotatsu.core.ui.dialog.ExpressiveDialogCard
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.ui.util.RecyclerViewOwner
import org.koitharu.kotatsu.core.ui.util.SpanSizeResolver
import org.koitharu.kotatsu.core.ui.widgets.TipView
import org.koitharu.kotatsu.core.util.ext.HapticEffect
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.findActivity
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.rememberHapticEffect
import org.koitharu.kotatsu.core.util.ext.setValueRounded
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.core.util.progress.IntPercentLabelFormatter
import org.koitharu.kotatsu.databinding.FragmentDiscoverBinding
import org.koitharu.kotatsu.discover.domain.DiscoverWindow
import org.koitharu.kotatsu.explore.ui.SourcesActivity
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.list.ui.adapter.ListStateHolderListener
import org.koitharu.kotatsu.list.ui.adapter.TypedListSpacingDecoration
import org.koitharu.kotatsu.list.ui.adapter.emptyHintAD
import org.koitharu.kotatsu.list.ui.adapter.listHeaderAD
import org.koitharu.kotatsu.list.ui.adapter.loadingStateAD
import org.koitharu.kotatsu.list.ui.adapter.mangaGridItemAD
import org.koitharu.kotatsu.list.ui.adapter.tipAD
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.list.ui.size.DynamicItemSizeResolver
import org.koitharu.kotatsu.list.ui.size.StaticItemSizeResolver
import org.koitharu.kotatsu.main.ui.owners.BottomNavOwner
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import javax.inject.Inject
import kotlin.math.roundToInt

private const val MAX_POOLED_COVERS = 24

@AndroidEntryPoint
class DiscoverFragment :
	BaseFragment<FragmentDiscoverBinding>(),
	RecyclerViewOwner,
	ListStateHolderListener,
	OnListItemClickListener<MangaListModel>,
	TipView.OnButtonClickListener {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<DiscoverViewModel>()
	private var pages: List<RecyclerView> = emptyList()
	private val columnResolvers = ArrayList<SpanSizeResolver>(DiscoverTab.entries.size)

	override val recyclerView: RecyclerView?
		get() = viewBinding?.pager?.currentItem?.let(pages::getOrNull)

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
		FragmentDiscoverBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentDiscoverBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		pages = DiscoverTab.entries.map { createPage(it) }
		with(binding.pager) {
			offscreenPageLimit = 1
			adapter = PagesAdapter(pages)
			setCurrentItem(viewModel.tab.value.ordinal, false)
			registerOnPageChangeCallback(
				object : ViewPager2.OnPageChangeCallback() {
					override fun onPageSelected(position: Int) {
						viewModel.setTab(DiscoverTab.entries[position])
					}
				},
			)
		}
		binding.filterBar.apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				KurohaTheme {
					val state by viewModel.chips.collectAsState(
						initial = DiscoverChips(
							tab = viewModel.tab.value,
							window = viewModel.window.value,
							filter = org.koitharu.kotatsu.discover.domain.DiscoverFilter(),
							types = emptyList(),
							genres = emptyList(),
						),
					)
					DiscoverFilterBar(
						state = state,
						onSelectTab = { tab ->
							viewBinding?.pager?.setCurrentItem(tab.ordinal, true)
						},
						onSelectWindow = viewModel::setWindow,
						onToggleType = viewModel::toggleType,
						onToggleGenre = viewModel::toggleGenre,
						onClearFilters = viewModel::clearFilters,
					)
				}
			}
		}
		settings.observeAsFlow(AppSettings.KEY_GRID_SIZE_DISCOVER) { gridSizeDiscover }.observe(viewLifecycleOwner) {
			val width = getColumnWidth()
			columnResolvers.forEach { it.minItemWidth = width }
		}
		addMenuProvider(DiscoverMenuProvider())
	}

	override fun onDestroyView() {
		requireViewBinding().pager.adapter = null
		pages = emptyList()
		columnResolvers.clear()
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.systemBarsInsets
		val padding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		pages.forEach { it.setPadding(bars.left + padding, padding, bars.right + padding, bars.bottom + padding) }
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onResume() {
		super.onResume()
		viewModel.onScreenResumed()
	}

	override fun onItemClick(item: MangaListModel, view: View) = router.openDetails(item.manga)

	override fun onItemLongClick(item: MangaListModel, view: View): Boolean {
		offerDismiss(item.manga, view)
		return true
	}

	private fun offerDismiss(manga: Manga, anchor: View) {
		val popup = PopupMenu(anchor.context, anchor)
		popup.menu.add(R.string.not_interested).setOnMenuItemClickListener {
			dismissWithUndo(manga)
			true
		}
		popup.show()
	}

	private fun dismissWithUndo(manga: Manga) {
		val key = viewModel.dismiss(manga)
		val host = viewBinding?.root ?: return
		val snackbar = Snackbar.make(host, R.string.removed_from_suggestions, Snackbar.LENGTH_LONG)
		(host.context.findActivity() as? BottomNavOwner)?.let { snackbar.anchorView = it.bottomNav }
		snackbar.setAction(R.string.undo) { viewModel.undoDismiss(key) }
		snackbar.show()
	}

	override fun onPrimaryButtonClick(tipView: TipView) = viewModel.closeTip()

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onRetryClick(error: Throwable) = viewModel.refresh()

	override fun onEmptyActionClick() = router.openManageSources()

	private fun createPage(tab: DiscoverTab): RecyclerView {
		val rowSize = StaticItemSizeResolver(resources.getDimensionPixelSize(R.dimen.smaller_grid_width))
		val coverPool = RecyclerView.RecycledViewPool().apply {
			setMaxRecycledViews(ListItemType.MANGA_GRID.ordinal, MAX_POOLED_COVERS)
		}
		val gridSize = DynamicItemSizeResolver(
			resources = resources,
			lifecycleOwner = viewLifecycleOwner,
			settings = settings,
			adjustWidth = false,
			sizeKey = AppSettings.KEY_GRID_SIZE_DISCOVER,
			sizeOf = { it.gridSizeDiscover },
		)
		val listAdapter = BaseListAdapter<ListModel>()
			.addDelegate(ListItemType.DISCOVER_RANKED, discoverRankedAD({ router.openDetails(it) }, ::offerDismiss, ::dismissWithUndo))
			.addDelegate(ListItemType.DISCOVER_ROW, discoverRowAD(rowSize, coverPool, { router.openSuggestions() }, this) { item, _ -> dismissWithUndo(item.manga) })
			.addDelegate(ListItemType.FOOTER_LOADING, discoverPlaceholderAD())
			.addDelegate(ListItemType.MANGA_GRID, mangaGridItemAD(gridSize, this) { item, _ -> dismissWithUndo(item.manga) })
			.addDelegate(ListItemType.HEADER, listHeaderAD(null))
			.addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(this))
			.addDelegate(ListItemType.TIP, tipAD(this))
			.addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		val page = RecyclerView(requireContext())
		page.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
		page.clipToPadding = false
		page.isVerticalScrollBarEnabled = true
		page.adapter = listAdapter
		val grid = GridLayoutManager(page.context, 3)
		grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int = when (listAdapter.getItemViewType(position)) {
				ListItemType.MANGA_GRID.ordinal,
				ListItemType.DISCOVER_RANKED.ordinal,
				ListItemType.FOOTER_LOADING.ordinal,
					-> 1

				else -> grid.spanCount
			}
		}
		page.layoutManager = grid
		columnResolvers += SpanSizeResolver(page, getColumnWidth()).also { it.attach() }
		page.addItemDecoration(TypedListSpacingDecoration(page.context, false))
		val content = if (tab == DiscoverTab.FOR_YOU) viewModel.forYouContent else viewModel.topContent
		content.observe(viewLifecycleOwner, listAdapter)
		return page
	}

	private fun getColumnWidth(): Int {
		return (resources.getDimension(R.dimen.preferred_grid_width) * settings.gridSizeDiscover / 100f).roundToInt()
	}

	private fun showGridSizeDialog() {
		val context = requireContext()
		val slider = Slider(context).apply {
			valueFrom = 50f
			valueTo = 150f
			stepSize = 5f
			tickVisibilityMode = TickVisibilityMode.TICK_VISIBILITY_HIDDEN
			labelBehavior = LabelFormatter.LABEL_FLOATING
			setLabelFormatter(IntPercentLabelFormatter(context))
			setValueRounded(settings.gridSizeDiscover.toFloat())
			addOnChangeListener { _, value, fromUser ->
				if (fromUser) {
					settings.gridSizeDiscover = value.toInt()
				}
			}
		}
		val padding = resources.getDimensionPixelOffset(R.dimen.margin_normal)
		val container = FrameLayout(context).apply {
			setPadding(padding, padding, padding, 0)
			addView(slider)
		}
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.grid_size)
			.setView(container)
			.setPositiveButton(R.string.done, null)
			.show()
	}

	private class PagesAdapter(private val pages: List<View>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

		override fun getItemCount() = pages.size

		override fun getItemViewType(position: Int) = position

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			object : RecyclerView.ViewHolder(pages[viewType]) {}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
	}

	private inner class DiscoverMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_discover, menu)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_by_source -> {
				startActivity(Intent(requireContext(), SourcesActivity::class.java))
				true
			}

			R.id.action_grid_size -> {
				showGridSizeDialog()
				true
			}

			else -> false
		}
	}
}

@Composable
private fun DiscoverFilterBar(
	state: DiscoverChips,
	onSelectTab: (DiscoverTab) -> Unit,
	onSelectWindow: (DiscoverWindow) -> Unit,
	onToggleType: (ContentType) -> Unit,
	onToggleGenre: (String) -> Unit,
	onClearFilters: () -> Unit,
) {
	val cs = MaterialTheme.colorScheme
	val haptic = rememberHapticEffect()
	var showFilterDialog by remember { mutableStateOf(false) }

	val activeParts = buildList {
		state.filter.type?.let { add(stringResource(it.titleResId)) }
		state.filter.genre?.let { add(it) }
		if (state.tab == DiscoverTab.TOP && state.window != DiscoverWindow.WEEK) {
			add(stringResource(state.window.titleRes))
		}
	}
	val hasActiveFilters = state.filter.type != null || state.filter.genre != null
	val filterLabel = if (activeParts.isEmpty()) {
		stringResource(R.string.filter)
	} else {
		activeParts.joinToString(" · ")
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		// Left: sleek segmented pill switcher (For you | Top)
		Surface(
			shape = CircleShape,
			color = cs.surfaceContainerHigh,
			border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
		) {
			Row(
				modifier = Modifier.padding(3.dp),
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				DiscoverTab.entries.forEach { tab ->
					val selected = tab == state.tab
					val bg by animateColorAsState(
						targetValue = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
						label = "tabSegBg",
					)
					val fg by animateColorAsState(
						targetValue = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
						label = "tabSegFg",
					)
					Box(
						modifier = Modifier
							.clip(CircleShape)
							.background(bg)
							.clickable {
								haptic(HapticEffect.CONFIRM)
								onSelectTab(tab)
							}
							.padding(horizontal = 14.dp, vertical = 7.dp),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = stringResource(tab.titleRes),
							style = MaterialTheme.typography.labelLarge,
							fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
							color = fg,
							maxLines = 1,
						)
					}
				}
			}
		}

		Spacer(Modifier.width(8.dp))

		// Right: compact M3 Filter pill button (+ quick clear when active)
		Row(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				shape = CircleShape,
				color = if (hasActiveFilters) cs.primaryContainer else cs.surfaceContainerHigh,
				contentColor = if (hasActiveFilters) cs.onPrimaryContainer else cs.onSurface,
				border = BorderStroke(
					1.dp,
					if (hasActiveFilters) cs.primary.copy(alpha = 0.4f) else cs.outlineVariant.copy(alpha = 0.5f),
				),
				modifier = Modifier
					.clip(CircleShape)
					.clickable {
						haptic(HapticEffect.CONFIRM)
						showFilterDialog = true
					},
			) {
				Row(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Icon(
						painter = painterResource(R.drawable.ic_filter_menu),
						contentDescription = null,
						modifier = Modifier.size(18.dp),
					)
					Text(
						text = filterLabel,
						style = MaterialTheme.typography.labelLarge,
						fontWeight = if (hasActiveFilters) FontWeight.Bold else FontWeight.Medium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			if (hasActiveFilters) {
				Surface(
					shape = CircleShape,
					color = cs.surfaceContainerHighest,
					contentColor = cs.onSurfaceVariant,
					modifier = Modifier
						.size(34.dp)
						.clip(CircleShape)
						.clickable {
							haptic(HapticEffect.CONFIRM)
							onClearFilters()
						},
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							painter = painterResource(R.drawable.ic_close),
							contentDescription = stringResource(R.string.reset),
							modifier = Modifier.size(16.dp),
						)
					}
				}
			}
		}
	}

	if (showFilterDialog) {
		DiscoverFilterDialog(
			state = state,
			onSelectWindow = onSelectWindow,
			onToggleType = onToggleType,
			onToggleGenre = onToggleGenre,
			onReset = onClearFilters,
			onDismiss = { showFilterDialog = false },
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverFilterDialog(
	state: DiscoverChips,
	onSelectWindow: (DiscoverWindow) -> Unit,
	onToggleType: (ContentType) -> Unit,
	onToggleGenre: (String) -> Unit,
	onReset: () -> Unit,
	onDismiss: () -> Unit,
) {
	val cs = MaterialTheme.colorScheme
	val haptic = rememberHapticEffect()

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		ExpressiveDialogCard(
			icon = painterResource(R.drawable.ic_filter_menu),
			title = stringResource(R.string.filter),
			cornerAction = DialogCornerAction(
				icon = painterResource(R.drawable.ic_check),
				label = stringResource(R.string.done),
				onClick = onDismiss,
			),
			headerTrailing = if (state.filter.type != null || state.filter.genre != null) {
				{
					Surface(
						shape = CircleShape,
						color = cs.surfaceContainerHighest,
						modifier = Modifier
							.clip(CircleShape)
							.clickable {
								haptic(HapticEffect.CONFIRM)
								onReset()
							},
					) {
						Text(
							text = stringResource(R.string.reset),
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Medium,
							color = cs.primary,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						)
					}
				}
			} else {
				null
			},
		) {
			if (state.tab == DiscoverTab.TOP) {
				Text(
					text = stringResource(R.string.top_charts),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold,
					color = cs.primary,
				)
				Spacer(Modifier.height(8.dp))
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					DiscoverWindow.entries.forEach { w ->
						FilterPill(
							label = stringResource(w.titleRes),
							selected = w == state.window,
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onSelectWindow(w)
							},
						)
					}
				}
				Spacer(Modifier.height(16.dp))
			}

			if (state.types.isNotEmpty()) {
				Text(
					text = stringResource(R.string.categories),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold,
					color = cs.primary,
				)
				Spacer(Modifier.height(8.dp))
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					state.types.forEach { type ->
						FilterPill(
							label = stringResource(type.titleResId),
							selected = type == state.filter.type,
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onToggleType(type)
							},
						)
					}
				}
			}

			if (state.genres.isNotEmpty()) {
				Spacer(Modifier.height(16.dp))
				Text(
					text = stringResource(R.string.genres),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold,
					color = cs.primary,
				)
				Spacer(Modifier.height(8.dp))
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					state.genres.forEach { genre ->
						FilterPill(
							label = genre,
							selected = genre == state.filter.genre,
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onToggleGenre(genre)
							},
						)
					}
				}
			}
		}
	}
}

@Composable
private fun FilterPill(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val cs = MaterialTheme.colorScheme
	Surface(
		shape = RoundedCornerShape(14.dp),
		color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
		contentColor = if (selected) cs.onPrimaryContainer else cs.onSurface,
		border = BorderStroke(
			1.dp,
			if (selected) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.45f),
		),
		modifier = Modifier
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
		)
	}
}
