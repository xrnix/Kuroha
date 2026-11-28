package org.koitharu.kotatsu.core.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IdRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.material.navigationrail.NavigationRailView
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.core.prefs.NavItem
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.main.ui.nav.FloatingNavBarColors
import org.koitharu.kotatsu.main.ui.nav.FloatingNavBarItem
import org.koitharu.kotatsu.main.ui.nav.FloatingNavRail
import com.google.android.material.R as materialR

/**
 * A NavigationRailView whose visible face is rendered with Jetpack Compose as a
 * floating, pill-shaped vertical toolbar — the side-mounted twin of [FloatingBottomNavigationView].
 *
 * It still owns the underlying NavigationRailView menu so MainNavigationDelegate keeps
 * driving it through the standard selectedItemId / listener APIs. The inherited rail
 * children are hidden, and a ComposeView sibling renders the floating vertical bar.
 */
class FloatingNavRailView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : NavigationRailView(context, attrs) {

	private val composeItemsState = MutableStateFlow<List<FloatingNavBarItem>>(emptyList())
	private val selectedIdState = MutableStateFlow(0)
	private val navColorsState = MutableStateFlow(readNavColors())
	private val continueVisibleState = MutableStateFlow(false)
	private var continueClickListener: (() -> Unit)? = null
	private var continueLongClickListener: (() -> Unit)? = null
	private val sourceItems = mutableListOf<NavItem>()
	private val hiddenIds = mutableSetOf<Int>()
	private val badgeCounts = mutableMapOf<Int, Int>()

	val maxRenderedItems: Int = MAX_RENDERED_ITEMS

	private val composeView: ComposeView = ComposeView(context).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			org.koitharu.kotatsu.settings.compose.KurohaTheme {
				val items by composeItemsState.collectAsState()
				val selectedId by selectedIdState.collectAsState()
				val navColors by navColorsState.collectAsState()
				val showContinue by continueVisibleState.collectAsState()
				Box(
					modifier = Modifier
						.fillMaxHeight()
						.padding(start = 14.dp, end = 6.dp),
					contentAlignment = Alignment.CenterStart,
				) {
					FloatingNavRail(
						items = items,
						selectedId = selectedId,
						colors = navColors,
						onItemSelected = { id -> this@FloatingNavRailView.selectedItemId = id },
						onItemReselected = { id ->
							val menuItem = menu.findItem(id) ?: return@FloatingNavRail
							reselectedListener?.invoke(menuItem)
						},
						modifier = Modifier.wrapContentHeight(),
						showContinue = showContinue,
						onContinueClick = { continueClickListener?.invoke() },
						onContinueLongClick = { continueLongClickListener?.invoke() },
					)
				}
			}
		}
	}

	private var reselectedListener: ((android.view.MenuItem) -> Unit)? = null

	init {
		background = null
		elevation = 0f
		clipChildren = false
		clipToPadding = false
		composeView.clipChildren = false
		composeView.clipToPadding = false
		hideNativeChildren()
		addView(
			composeView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
		)
	}

	override fun setSelectedItemId(@IdRes itemId: Int) {
		super.setSelectedItemId(itemId)
		selectedIdState.value = selectedItemId
	}

	override fun setOnItemReselectedListener(listener: OnItemReselectedListener?) {
		super.setOnItemReselectedListener(listener)
		reselectedListener = listener?.let { l -> { item -> l.onNavigationItemReselected(item) } }
	}

	fun setComposeItems(items: List<NavItem>) {
		sourceItems.clear()
		sourceItems.addAll(items)
		rebuildComposeItems()
	}

	fun setComposeBadge(@IdRes itemId: Int, count: Int) {
		if (count == 0) badgeCounts.remove(itemId) else badgeCounts[itemId] = count
		rebuildComposeItems()
	}

	fun setComposeItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		if (isVisible) hiddenIds.remove(itemId) else hiddenIds.add(itemId)
		rebuildComposeItems()
	}

	fun setContinueVisible(value: Boolean) {
		continueVisibleState.value = value
	}

	fun setOnContinueClickListener(listener: (() -> Unit)?) {
		continueClickListener = listener
	}

	fun setOnContinueLongClickListener(listener: (() -> Unit)?) {
		continueLongClickListener = listener
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		navColorsState.value = readNavColors()
	}

	private fun rebuildComposeItems() {
		val out = ArrayList<FloatingNavBarItem>(sourceItems.size.coerceAtMost(maxRenderedItems))
		for (item in sourceItems) {
			if (item.id in hiddenIds) continue
			out += FloatingNavBarItem(
				id = item.id,
				titleRes = item.title,
				icon = item.icon,
				badgeCount = badgeCounts[item.id] ?: 0,
			)
			if (out.size >= maxRenderedItems) break
		}
		composeItemsState.value = out
		selectedIdState.value = selectedItemId
	}

	private fun hideNativeChildren() {
		for (i in 0 until childCount) {
			val child = getChildAt(i)
			if (child !== composeView) {
				child.visibility = View.GONE
			}
		}
	}

	private fun readNavColors(): FloatingNavBarColors {
		return FloatingNavBarColors(
			container = context.getThemeColor(materialR.attr.colorSurfaceContainer),
			selectedContainer = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary),
			selectedContent = context.getThemeColor(materialR.attr.colorOnPrimary),
			unselectedContent = context.getThemeColor(materialR.attr.colorOnSurfaceVariant),
		)
	}

	companion object {
		const val MAX_RENDERED_ITEMS = 6
	}
}
