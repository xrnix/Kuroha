package org.koitharu.kotatsu.explore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseFragment
import org.koitharu.kotatsu.core.ui.util.RecyclerViewOwner
import org.koitharu.kotatsu.core.util.ext.findCurrentPagerFragment
import org.koitharu.kotatsu.core.util.ext.recyclerView
import org.koitharu.kotatsu.databinding.FragmentFavouritesContainerBinding
import org.koitharu.kotatsu.discover.ui.DiscoverFragment
import org.koitharu.kotatsu.main.ui.MainNavigationDelegate.TabTransition
import org.koitharu.kotatsu.main.ui.owners.AppBarOwner

@AndroidEntryPoint
class ExploreContainerFragment : BaseFragment<FragmentFavouritesContainerBinding>(), RecyclerViewOwner {

	var isTabsAttachDeferred = false
	private var pendingTabsEnter: Runnable? = null

	override val recyclerView: RecyclerView?
		get() = (findCurrentFragment() as? RecyclerViewOwner)?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentFavouritesContainerBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentFavouritesContainerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val adapter = ExplorePagerAdapter(this)
		binding.pager.adapter = adapter
		binding.pager.offscreenPageLimit = 1
		binding.pager.recyclerView?.isNestedScrollingEnabled = false
		TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
			tab.text = getString(
				when (position) {
					0 -> R.string.discover
					else -> R.string.by_source
				},
			)
		}.attach()
		if (!isHidden && !isTabsAttachDeferred) {
			attachTabsToAppBar()
		}
	}

	override fun onDestroyView() {
		cancelPendingTabsEnter()
		detachTabsFromAppBar()
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onHiddenChanged(hidden: Boolean) {
		super.onHiddenChanged(hidden)
		if (!hidden) {
			for (page in childFragmentManager.fragments) {
				val recyclerView = (page as? RecyclerViewOwner)?.recyclerView ?: continue
				when (val lm = recyclerView.layoutManager) {
					is LinearLayoutManager -> lm.scrollToPositionWithOffset(0, 0)
					else -> recyclerView.scrollToPosition(0)
				}
			}
		}
	}

	private fun findCurrentFragment(): Fragment? {
		return childFragmentManager.findCurrentPagerFragment(
			viewBinding?.pager ?: return null,
		)
	}

	fun showTabsInAppBar(transition: TabTransition) {
		val tabs = viewBinding?.tabs ?: return
		val appBar = (activity as? AppBarOwner)?.appBar ?: return
		cancelPendingTabsEnter()
		when {
			transition == TabTransition.NONE -> {
				attachTabsToAppBar()
				tabs.stopTabsAnimation()
			}

			tabs.parent === appBar -> tabs.slideTabsIn()
			else -> {
				val enter = Runnable {
					pendingTabsEnter = null
					attachTabsToAppBar()
					val distance = resources.getDimension(R.dimen.nav_tab_slide_distance)
					tabs.alpha = 0f
					tabs.translationX = -transition.slideX * distance
					tabs.translationY = -transition.slideY * distance
					tabs.slideTabsIn()
				}
				pendingTabsEnter = enter
				requireView().postDelayed(enter, resources.getInteger(R.integer.nav_tab_exit_duration).toLong())
			}
		}
	}

	fun hideTabsFromAppBar(transition: TabTransition) {
		val tabs = viewBinding?.tabs ?: return
		cancelPendingTabsEnter()
		if (transition == TabTransition.NONE || tabs.parent !== (activity as? AppBarOwner)?.appBar) {
			detachTabsFromAppBar()
			return
		}
		val distance = resources.getDimension(R.dimen.nav_tab_exit_distance)
		tabs.animate()
			.alpha(0f)
			.translationX(transition.slideX * distance)
			.translationY(transition.slideY * distance)
			.setDuration(resources.getInteger(R.integer.nav_tab_exit_duration).toLong())
			.setInterpolator(AnimationUtils.loadInterpolator(tabs.context, R.interpolator.m3_easing_emphasized_accelerate))
			.withLayer()
			.withEndAction(::detachTabsFromAppBar)
	}

	private fun View.slideTabsIn() {
		animate()
			.alpha(1f)
			.translationX(0f)
			.translationY(0f)
			.setDuration(
				(resources.getInteger(R.integer.nav_tab_enter_duration) -
					resources.getInteger(R.integer.nav_tab_exit_duration)).toLong(),
			)
			.setInterpolator(AnimationUtils.loadInterpolator(context, R.interpolator.m3_easing_emphasized_decelerate))
			.withLayer()
	}

	private fun View.stopTabsAnimation() {
		animate().cancel()
		alpha = 1f
		translationX = 0f
		translationY = 0f
	}

	private fun cancelPendingTabsEnter() {
		isTabsAttachDeferred = false
		val enter = pendingTabsEnter ?: return
		pendingTabsEnter = null
		view?.removeCallbacks(enter)
	}

	private fun attachTabsToAppBar() {
		val tabs = viewBinding?.tabs ?: return
		val appBar = (activity as? AppBarOwner)?.appBar ?: return
		if (tabs.parent === appBar) return
		(tabs.parent as? ViewGroup)?.removeView(tabs)
		appBar.addView(
			tabs,
			AppBarLayout.LayoutParams(
				AppBarLayout.LayoutParams.MATCH_PARENT,
				AppBarLayout.LayoutParams.WRAP_CONTENT,
			).apply {
				scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
					AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
					AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
			},
		)
	}

	private fun detachTabsFromAppBar() {
		val tabs = viewBinding?.tabs ?: return
		val appBar = (activity as? AppBarOwner)?.appBar ?: return
		if (tabs.parent === appBar) {
			appBar.removeView(tabs)
		}
	}

	private class ExplorePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

		override fun getItemCount(): Int = 2

		override fun createFragment(position: Int): Fragment = when (position) {
			0 -> DiscoverFragment()
			else -> ExploreFragment()
		}
	}
}
