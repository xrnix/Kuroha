package org.koitharu.kotatsu.favourites.ui.container

import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.util.PopupMenuMediator

class FavouritesTabConfigurationStrategy(
	private val adapter: FavouritesContainerAdapter,
	private val viewModel: FavouritesContainerViewModel,
	private val router: AppRouter,
) : TabConfigurationStrategy {

	override fun onConfigureTab(tab: TabLayout.Tab, position: Int) {
		val item = adapter.getItem(position)
		val ctx = tab.view.context
		tab.text = when (item.id) {
			FavouritesContainerViewModel.ID_LOCAL -> ctx.getString(R.string.on_device)
			FavouritesContainerViewModel.ID_BOOKMARKS -> ctx.getString(R.string.bookmarks)
			else -> item.title ?: ctx.getString(R.string.all_favourites)
		}
		tab.tag = item
		if (item.id != FavouritesContainerViewModel.ID_LOCAL && item.id != FavouritesContainerViewModel.ID_BOOKMARKS) {
			PopupMenuMediator(
				FavouriteTabPopupMenuProvider(ctx, router, viewModel, item.id),
			).attach(tab.view)
		}
	}
}
