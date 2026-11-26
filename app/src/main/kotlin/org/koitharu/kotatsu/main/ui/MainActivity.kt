package org.koitharu.kotatsu.main.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.search.SearchView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.ui.periodical.PeriodicalBackupService
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.os.VoiceInputContract
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.NavItem
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.menu.MenuPopup
import org.koitharu.kotatsu.core.ui.util.FadingAppbarMediator
import org.koitharu.kotatsu.core.ui.util.StatusBarScrim
import org.koitharu.kotatsu.core.ui.widgets.SlidingBottomNavigationView
import org.koitharu.kotatsu.core.util.ext.HapticEffect
import org.koitharu.kotatsu.core.util.ext.applySystemAnimatorScale
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.core.util.ext.end
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.hapticFeedback
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.setOptionalIconsVisibleCompat
import org.koitharu.kotatsu.explore.ui.ExploreContainerFragment
import org.koitharu.kotatsu.explore.ui.SourcesActivity
import org.koitharu.kotatsu.core.util.ext.start
import org.koitharu.kotatsu.databinding.ActivityMainBinding
import org.koitharu.kotatsu.details.service.MangaPrefetchService
import org.koitharu.kotatsu.favourites.ui.container.FavouritesContainerFragment
import org.koitharu.kotatsu.history.ui.HistoryListFragment
import org.koitharu.kotatsu.local.ui.LocalIndexUpdateService
import org.koitharu.kotatsu.local.ui.LocalStorageCleanupWorker
import org.koitharu.kotatsu.main.ui.owners.AppBarOwner
import org.koitharu.kotatsu.main.ui.owners.BottomNavOwner
import org.koitharu.kotatsu.main.ui.owners.ListCheckpointOwner
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.remotelist.ui.MangaSearchMenuProvider
import org.koitharu.kotatsu.search.domain.ScreenSearchQuery
import org.koitharu.kotatsu.search.ui.suggestion.SearchSuggestionItemCallback
import org.koitharu.kotatsu.search.ui.suggestion.SearchSuggestionListenerImpl
import org.koitharu.kotatsu.search.ui.suggestion.SearchSuggestionMenuProvider
import org.koitharu.kotatsu.search.ui.suggestion.SearchSuggestionViewModel
import org.koitharu.kotatsu.search.ui.suggestion.adapter.SearchSuggestionAdapter
import javax.inject.Inject
import kotlin.math.roundToInt

private const val COLD_START_MAX_MS = 3000L

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), AppBarOwner, BottomNavOwner, ListCheckpointOwner,
	SearchView.TransitionListener, MainNavigationDelegate.OnFragmentChangedListener, View.OnClickListener,
	View.OnLayoutChangeListener, SearchSuggestionItemCallback.SuggestionItemListener {

	private val viewModel: MainViewModel by viewModels()
	private val searchSuggestionViewModel: SearchSuggestionViewModel by viewModels()

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var screenSearchQuery: ScreenSearchQuery

	private lateinit var navigationDelegate: MainNavigationDelegate
	private lateinit var fadingAppbarMediator: FadingAppbarMediator
	private val overflowMenuProviders = mutableListOf<OverflowMenuProviderEntry>()
	private var isSearchFullyShown = false
	private var mainFabModeKey: String? = null
	private var lastBottomInset = 0
	private val shrinkFabRunnable = Runnable { viewBinding.fab?.shrink() }
	private var navSystemBarBottom: Int = 0
	private var barBlur: BarBlur? = null
	private var activeScreenFilter: String = ""

	private val voiceInputLauncher = registerForActivityResult(VoiceInputContract()) { text ->
		if (!text.isNullOrBlank()) {
			viewBinding.searchView.setText(text)
		}
	}

	override val appBar: AppBarLayout
		get() = viewBinding.appbar

	override val bottomNav: SlidingBottomNavigationView?
		get() = viewBinding.bottomNav

	override val listCheckpointButton: View
		get() = viewBinding.buttonCheckpoint.root

	override fun onRemoveQuery(query: String) {
		searchSuggestionViewModel.deleteQuery(query)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		val splash = installSplashScreen()
		super.onCreate(savedInstanceState)

		setContentView(ActivityMainBinding.inflate(layoutInflater))

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && savedInstanceState == null && isTaskRoot) {
			val now = SystemClock.uptimeMillis()
			val introStart = Process.getStartUptimeMillis().takeIf { now - it < COLD_START_MAX_MS } ?: now
			val introEnd = introStart + resources.getInteger(R.integer.splash_screen_duration)
			splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() < introEnd }
			splash.setOnExitAnimationListener { splashScreenViewProvider ->
				val fadeOut = ObjectAnimator.ofFloat(
					splashScreenViewProvider.view,
					View.ALPHA,
					1f,
					0f
				).apply {
					interpolator = FastOutSlowInInterpolator()
					duration = 300L
					doOnEnd { splashScreenViewProvider.remove() }
				}
				fadeOut.start()
			}
		}

		setSupportActionBar(viewBinding.searchBar)
		viewBinding.searchBar.textCentered = true
		viewBinding.searchBar.textView.apply {
			gravity = android.view.Gravity.CENTER
			includeFontPadding = false
			setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
			val topBarIconTint = ColorStateList.valueOf(context.getThemeColor(R.attr.colorTopBarIcon))
			TextViewCompat.setCompoundDrawableTintList(this, topBarIconTint)
			setHintTextColor(topBarIconTint)
			compoundDrawablePadding = resources.getDimensionPixelOffset(R.dimen.margin_small)
		}
		var searchBarTextContainer: View? = null
		viewBinding.searchBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			val container = searchBarTextContainer ?: viewBinding.searchBar.findViewById<View>(
				com.google.android.material.R.id.open_search_bar_text_view_container
			).also { searchBarTextContainer = it }
			val barHeight = viewBinding.searchBar.height
			val textView = viewBinding.searchBar.textView
			if (container != null && barHeight > 0 && textView.height > 0) {
				val targetY = (barHeight - textView.height) / 2f - container.top
				if (kotlin.math.abs(textView.translationY - targetY) > 0.5f) {
					textView.translationY = targetY
				}
			}
		}

		viewBinding.statusBarScrim.background = StatusBarScrim.drawable(this)

		viewBinding.fab?.setOnClickListener(this)
		viewBinding.navRail?.headerView?.findViewById<View>(R.id.railFab)?.setOnClickListener(this)
		viewBinding.bottomNav?.setOnContinueClickListener { viewModel.openLastReader() }
		viewBinding.bottomNav?.setOnContinueLongClickListener { viewModel.openLastDetails() }
		viewBinding.buttonOverflow.setOnClickListener(this::showMainOverflowMenu)
		viewBinding.buttonSources.setOnClickListener {
			startActivity(android.content.Intent(this, SourcesActivity::class.java))
		}
		viewBinding.buttonUpdate.setOnClickListener { router.openAppUpdate() }
		viewBinding.buttonUpdateDismiss.setOnClickListener {
			viewModel.appUpdate.value?.let { settings.dismissedUpdateVersion = it.name }
			viewBinding.layoutUpdatePrompt.isVisible = false
		}
		viewBinding.buttonIncognitoDisable.setOnClickListener { viewModel.setIncognitoMode(false) }

		fadingAppbarMediator = FadingAppbarMediator(viewBinding.appbar, viewBinding.layoutSearch)

		navigationDelegate = MainNavigationDelegate(
			navBar = checkNotNull(bottomNav ?: viewBinding.navRail),
			fragmentManager = supportFragmentManager,
			settings = settings,
		)
		navigationDelegate.addOnFragmentChangedListener(this)
		navigationDelegate.onExploreReselected = {
			if (isSearchFullyShown) {
				viewBinding.searchView.hide()
			} else if (!viewBinding.searchView.isShowing) {
				viewBinding.searchView.show()
			}
		}
		navigationDelegate.onCreate(this, savedInstanceState)
		viewBinding.textViewTitle?.let { tv ->
			navigationDelegate.observeTitle().observe(this) { tv.text = it }
		}

		addMainOverflowMenuProvider(MainMenuProvider(router, viewModel))

		val exitCallback = ExitCallback(this, viewBinding.container)
		onBackPressedDispatcher.addCallback(exitCallback)
		onBackPressedDispatcher.addCallback(navigationDelegate)

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !resources.getBoolean(R.bool.is_predictive_back_enabled)) {
			val legacySearchCallback = SearchViewLegacyBackCallback(viewBinding.searchView)
			viewBinding.searchView.addTransitionListener(legacySearchCallback)
			onBackPressedDispatcher.addCallback(legacySearchCallback)
		}

		if (savedInstanceState == null) {
			onFirstStart()
		}

		viewModel.onOpenReader.observeEvent(this, this::onOpenReader)
		viewModel.onOpenLastDetails.observeEvent(this) { router.openDetails(it) }
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.container, null))
		viewModel.isLoading.observe(this, this::onLoadingStateChanged)
		viewModel.isResumeEnabled.observe(this, this::onResumeEnabledChanged)
		settings.observe(AppSettings.KEY_NAV_LEGACY).observe(this) { adjustFabVisibility() }
		viewModel.feedCounter.observe(this, ::onFeedCounterChanged)
		viewModel.appUpdate.observe(this) { update ->
			viewBinding.layoutUpdatePrompt.isVisible =
				update != null && update.name != settings.dismissedUpdateVersion
		}
		viewModel.onFirstStart.observeEvent(this) { router.openOnboarding() }
		viewModel.isBottomNavPinned.observe(this, ::setNavbarPinned)
		searchSuggestionViewModel.isIncognitoModeEnabled.observe(this, this::onIncognitoModeChanged)
		viewBinding.bottomNav?.addOnLayoutChangeListener(this)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			barBlur = BarBlur(viewBinding.container, viewBinding.bottomNav)
		}
		settings.observe(AppSettings.KEY_BAR_BLUR).observe(this) { onBarBlurChanged() }

		setupContainerInsetsListener()
		viewBinding.searchView.addTransitionListener(this)
		viewBinding.searchView.addTransitionListener(exitCallback)
		observeFoldHinge()
		initSearch()
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		isSearchFullyShown = viewBinding.searchView.isShowing
		adjustSearchUI(viewBinding.searchView.isShowing)
		navigationDelegate.syncSelectedItem()
	}

	override fun onFragmentChanged(
		fragment: Fragment,
		fromUser: Boolean,
		transition: MainNavigationDelegate.TabTransition,
	) {
		adjustFabVisibility(topFragment = fragment)
		adjustAppbar(topFragment = fragment, transition = transition)
		if (fromUser) {
			actionModeDelegate.finishActionMode()
			viewBinding.appbar.setExpanded(true)
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		if (!viewBinding.searchView.isShowing) {
			viewBinding.searchView.show()
		}
		return true
	}

	override fun addMenuProvider(provider: MenuProvider, owner: LifecycleOwner, state: Lifecycle.State) {
		if (provider !is MangaSearchMenuProvider) {
			addMainOverflowMenuProvider(provider, owner, state)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.fab, R.id.railFab -> {
				v.hapticFeedback(HapticEffect.CONFIRM)
				viewModel.openLastReader()
			}
		}
	}

	private fun addMainOverflowMenuProvider(
		provider: MenuProvider,
		owner: LifecycleOwner? = null,
		state: Lifecycle.State = Lifecycle.State.CREATED,
	) {
		val entry = OverflowMenuProviderEntry(provider, owner, state)
		overflowMenuProviders += entry
		owner?.lifecycle?.addObserver(
			LifecycleEventObserver { _, event ->
				if (event == Lifecycle.Event.ON_DESTROY) {
					overflowMenuProviders -= entry
				}
			},
		)
	}

	private fun showMainOverflowMenu(anchor: View) {
		val providers = overflowMenuProviders
			.filter { it.isActive }
			.map { it.provider }
		val popup = MenuPopup(anchor)
		val menu = popup.menu
		providers.forEach { it.onCreateMenu(menu, popup.menuInflater) }
		providers.forEach { it.onPrepareMenu(menu) }
		if (!menu.hasVisibleItems()) {
			return
		}
		menu.setOptionalIconsVisibleCompat(true)
		popup.setForceShowIcon(true)
		popup.setOnMenuItemClickListener { item ->
			providers.any { it.onMenuItemSelected(item) }
		}
		popup.setOnDismissListener {
			providers.forEach { it.onMenuClosed(menu) }
		}
		popup.show()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		navSystemBarBottom = barsInsets.bottom
		val searchBarDefaultMargin = resources.getDimensionPixelOffset(R.dimen.search_bar_margin_horizontal)
		viewBinding.layoutSearch.updateLayoutParams<MarginLayoutParams> {
			marginEnd = searchBarDefaultMargin + barsInsets.end(v)
			marginStart = if (viewBinding.navRail != null) {
				searchBarDefaultMargin
			} else {
				searchBarDefaultMargin + barsInsets.start(v)
			}
		}
		viewBinding.bottomNav?.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.navRail?.updateLayoutParams<MarginLayoutParams> {
			marginStart = barsInsets.start(v)
			topMargin = barsInsets.top
			bottomMargin = barsInsets.bottom
		}
		updateCheckpointMargin(barsInsets.bottom)

		val statusBarZone = if (settings.isStatusBarHidden) {
			0
		} else {
			(barsInsets.top * StatusBarScrim.HEIGHT_FACTOR).roundToInt()
		}
		viewBinding.statusBarScrim.updateLayoutParams { height = statusBarZone }
		barBlur?.statusBarZone = statusBarZone.toFloat()
		updateContainerBottomMargin()

		val hasRail = viewBinding.navRail != null
		return insets.consume(v, typeMask, start = hasRail)
			.consume(v, WindowInsetsCompat.Type.displayCutout(), start = hasRail)
			.also { handleSearchSuggestionsInsets(it) }
	}

	override fun onLayoutChange(
		v: View?,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		oldLeft: Int,
		oldTop: Int,
		oldRight: Int,
		oldBottom: Int,
	) {
		if (top != oldTop || bottom != oldBottom) {
			updateContainerBottomMargin()
			updateCheckpointMargin()
			if (settings.isNavBarPinned && !settings.isLegacyNavigationBar) {
				ViewCompat.requestApplyInsets(viewBinding.container)
			}
		}
	}

	override fun onStateChanged(
		searchView: SearchView,
		previousState: SearchView.TransitionState,
		newState: SearchView.TransitionState,
	) {
		val wasOpened = previousState >= SearchView.TransitionState.SHOWING
		val isOpened = newState >= SearchView.TransitionState.SHOWING
		if (isOpened != wasOpened) {
			adjustSearchUI(isOpened)
		}
		isSearchFullyShown = (newState == SearchView.TransitionState.SHOWN)
	}

	override fun onSupportActionModeStarted(mode: ActionMode) {
		super.onSupportActionModeStarted(mode)
		adjustFabVisibility()
		bottomNav?.hide()
		viewBinding.layoutSearch.fadeOutForActionMode()
		updateContainerBottomMargin()
	}

	override fun onSupportActionModeFinished(mode: ActionMode) {
		super.onSupportActionModeFinished(mode)
		adjustFabVisibility()
		bottomNav?.show()
		viewBinding.layoutSearch.fadeInAfterActionMode()
		updateContainerBottomMargin()
	}

	private fun onOpenReader(manga: Manga) {
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView
		router.openReader(manga, fab)
	}

	private fun onFeedCounterChanged(counter: Int) {
		navigationDelegate.setCounter(NavItem.FEED, counter)
	}

	private fun onIncognitoModeChanged(isIncognito: Boolean) {
		var options = viewBinding.searchView.getEditText().imeOptions
		options = if (isIncognito) {
			options or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
		} else {
			options and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
		}
		viewBinding.searchView.getEditText().imeOptions = options
		viewBinding.layoutIncognito.isVisible = isIncognito
		invalidateOptionsMenu()
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		adjustFabVisibility()
	}

	private fun onResumeEnabledChanged(isEnabled: Boolean) {
		adjustFabVisibility(isResumeEnabled = isEnabled)
	}

	private fun onFirstStart() {
		lifecycleScope.launch(Dispatchers.Main) {
			withContext(Dispatchers.Default) {
				LocalStorageCleanupWorker.enqueue(applicationContext)
			}
			withResumed {
				try {
					MangaPrefetchService.prefetchLast(this@MainActivity)
					startService(Intent(this@MainActivity, LocalIndexUpdateService::class.java))
					if (settings.isPeriodicalBackupEnabled) {
						startService(Intent(this@MainActivity, PeriodicalBackupService::class.java))
					}
					requestNotificationsPermission()
				} catch (e: IllegalStateException) {
					e.printStackTraceDebug()
				}
			}
		}
	}

	private fun requestNotificationsPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.POST_NOTIFICATIONS,
			) != PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.POST_NOTIFICATIONS),
				1,
			)
		}
	}

	private fun adjustAppbar(topFragment: Fragment, transition: MainNavigationDelegate.TabTransition) {
		val favourites = supportFragmentManager
			.findFragmentByTag(FavouritesContainerFragment::class.java.name) as? FavouritesContainerFragment
		val explore = supportFragmentManager
			.findFragmentByTag(ExploreContainerFragment::class.java.name) as? ExploreContainerFragment
		when (topFragment) {
			is FavouritesContainerFragment -> {
				fadingAppbarMediator.bind()
				explore?.hideTabsFromAppBar(transition)
				topFragment.showTabsInAppBar(transition)
			}

			is ExploreContainerFragment -> {
				fadingAppbarMediator.bind()
				favourites?.hideTabsFromAppBar(transition)
				topFragment.showTabsInAppBar(transition)
			}

			else -> {
				fadingAppbarMediator.unbind()
				favourites?.hideTabsFromAppBar(transition)
				explore?.hideTabsFromAppBar(transition)
			}
		}
	}

	private fun adjustFabVisibility(
		isResumeEnabled: Boolean = viewModel.isResumeEnabled.value,
		topFragment: Fragment? = navigationDelegate.primaryFragment,
		isSearchOpened: Boolean = viewBinding.searchView.isShowing,
	) {
		val fragment = if (topFragment?.isAdded == true) {
			topFragment
		} else {
			navigationDelegate.primaryFragment
		}

		val useFloatingContinue = viewBinding.bottomNav != null && !settings.isLegacyNavigationBar
		if (!useFloatingContinue && isResumeEnabled && !actionModeDelegate.isActionModeStarted &&
			!isSearchOpened && (viewBinding.navRail != null || fragment is HistoryListFragment)
		) {
			showMainFab("continue", ::configureContinueFab)
		} else {
			hideMainFab()
		}
		viewBinding.bottomNav?.setContinueVisible(useFloatingContinue && isResumeEnabled)
	}

	private fun showMainFab(
		modeKey: String,
		configure: (ExtendedFloatingActionButton) -> Unit,
	) {
		navigationDelegate.navRailHeader?.railFab?.let { railFab ->
			configure(railFab)
			railFab.isVisible = true
		}
		val fab = viewBinding.fab ?: run {
			mainFabModeKey = modeKey
			return
		}
		configure(fab)
		val isModeChanged = mainFabModeKey != modeKey
		mainFabModeKey = modeKey
		if (!fab.isVisible || isModeChanged) {
			fab.show()
			if (viewBinding.buttonCheckpoint.root.isVisible) {
				fab.shrink()
			} else {
				fab.extend()
				fab.removeCallbacks(shrinkFabRunnable)
				fab.postDelayed(shrinkFabRunnable, FAB_SHRINK_DELAY_MS)
			}
		}
	}

	private fun hideMainFab() {
		navigationDelegate.navRailHeader?.railFab?.isVisible = false
		mainFabModeKey = null
		val fab = viewBinding.fab ?: return
		if (fab.isVisible) {
			fab.removeCallbacks(shrinkFabRunnable)
			fab.hide()
		}
	}

	private fun configureContinueFab(fab: ExtendedFloatingActionButton) {
		fab.setText(R.string._continue)
		fab.setIconResource(R.drawable.ic_read)
		fab.isEnabled = !viewModel.isLoading.value
	}

	private fun adjustSearchUI(isOpened: Boolean) {
		val appBarScrollFlags = if (isOpened) {
			SCROLL_FLAG_NO_SCROLL
		} else if (settings.isNavBarPinned) {
			SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
		} else {
			SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
		}
		viewBinding.insetsHolder.updateLayoutParams<AppBarLayout.LayoutParams> {
			scrollFlags = appBarScrollFlags
		}
		adjustFabVisibility(isSearchOpened = isOpened)
		bottomNav?.showOrHide(!isOpened)
		updateContainerBottomMargin()
	}

	private fun handleSearchSuggestionsInsets(insets: WindowInsetsCompat) {
		val typeMask = WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val topGap = resources.getDimensionPixelOffset(R.dimen.list_spacing_large)
		viewBinding.recyclerViewSearch.setPadding(barsInsets.left, topGap, barsInsets.right, barsInsets.bottom)
	}

	private fun initSearch() {
		val listener = SearchSuggestionListenerImpl(router, viewBinding.searchView, searchSuggestionViewModel)
		addMenuProvider(
			ScreenFilterMenuProvider(
				isFilterActive = { activeScreenFilter.isNotEmpty() },
				onClear = {
					viewBinding.searchView.setText("")
					searchSuggestionViewModel.setScoped(false)
				},
			),
		)
		screenSearchQuery.activeQuery.observe(this) { query ->
			activeScreenFilter = query
			viewBinding.searchBar.setText(query)
			invalidateOptionsMenu()
		}

		val adapter = SearchSuggestionAdapter(listener)
		viewBinding.searchView.toolbar.addMenuProvider(
			SearchSuggestionMenuProvider(this, voiceInputLauncher, searchSuggestionViewModel),
		)
		viewBinding.searchView.editText.apply {
			gravity = android.view.Gravity.CENTER_VERTICAL
			includeFontPadding = false
		}
		viewBinding.searchView.editText.addTextChangedListener(listener)
		viewBinding.recyclerViewSearch.adapter = adapter
		viewBinding.searchView.editText.setOnEditorActionListener(listener)

		viewBinding.searchView.observeState()
			.map { if (it >= SearchView.TransitionState.SHOWING) SearchView.TransitionState.SHOWING else it }
			.distinctUntilChanged()
			.flatMapLatest { state ->
				when {
					state >= SearchView.TransitionState.SHOWING -> searchSuggestionViewModel.suggestion
					state == SearchView.TransitionState.HIDDEN -> flowOf(emptyList())
					else -> emptyFlow()
				}
			}.observe(this, adapter)

		searchSuggestionViewModel.onError.observeEvent(
			this,
			SnackbarErrorObserver(viewBinding.recyclerViewSearch, null),
		)
		ItemTouchHelper(SearchSuggestionItemCallback(this))
			.attachToRecyclerView(viewBinding.recyclerViewSearch)
	}

	private fun setNavbarPinned(isPinned: Boolean) {
		val bottomNavBar = viewBinding.bottomNav
		bottomNavBar?.isPinned = isPinned
		for (view in viewBinding.appbar.children) {
			val lp = view.layoutParams as? AppBarLayout.LayoutParams ?: continue
			val scrollFlags = if (isPinned) {
				lp.scrollFlags and SCROLL_FLAG_SCROLL.inv()
			} else {
				lp.scrollFlags or SCROLL_FLAG_SCROLL
			}
			if (scrollFlags != lp.scrollFlags) {
				lp.scrollFlags = scrollFlags
				view.layoutParams = lp
			}
		}
		updateContainerBottomMargin()
		ViewCompat.requestApplyInsets(viewBinding.container)
	}

	private fun setupContainerInsetsListener() {
		ViewCompat.setOnApplyWindowInsetsListener(viewBinding.container) { _, windowInsets ->
			val extraBottom = if (settings.isNavBarPinned && !settings.isLegacyNavigationBar) {
				((viewBinding.bottomNav?.height ?: 0) - navSystemBarBottom).coerceAtLeast(0)
			} else {
				0
			}
			if (extraBottom == 0) {
				windowInsets
			} else {
				val type = WindowInsetsCompat.Type.systemBars()
				val current = windowInsets.getInsets(type)
				WindowInsetsCompat.Builder(windowInsets)
					.setInsets(
						type,
						Insets.of(current.left, current.top, current.right, current.bottom + extraBottom),
					)
					.build()
			}
		}
	}

	private fun updateCheckpointMargin(bottomInset: Int = lastBottomInset) {
		lastBottomInset = bottomInset
		val gap = resources.getDimensionPixelOffset(R.dimen.margin_small)
		val navHeight = viewBinding.bottomNav?.let { it.height - it.paddingBottom } ?: 0
		viewBinding.buttonCheckpoint.root.updateLayoutParams<MarginLayoutParams> {
			bottomMargin = bottomInset + navHeight + gap
		}
	}

	private fun onBarBlurChanged() {
		val blur = barBlur
		blur?.isEnabled = settings.isBarBlurEnabled
		viewBinding.statusBarScrim.isVisible = blur?.isEnabled != true
	}

	private fun updateContainerBottomMargin() {
		with(viewBinding.container) {
			val params = layoutParams as MarginLayoutParams
			if (params.bottomMargin != 0) {
				params.bottomMargin = 0
				layoutParams = params
			}
		}
	}

	private fun View.fadeOutForActionMode() {
		animate().cancel()
		isInvisible = false
		animate()
			.alpha(0f)
			.setDuration(ACTION_MODE_TOP_BAR_FADE_DURATION_MS)
			.applySystemAnimatorScale(context)
			.withEndAction {
				isInvisible = true
				alpha = 0f
			}
			.start()
	}

	private fun View.fadeInAfterActionMode() {
		animate().cancel()
		val targetAlpha = 1f
		alpha = 0f
		isInvisible = false
		animate()
			.alpha(targetAlpha)
			.setDuration(ACTION_MODE_TOP_BAR_FADE_DURATION_MS)
			.applySystemAnimatorScale(context)
			.withEndAction {
				alpha = targetAlpha
			}
			.start()
	}

	private fun observeFoldHinge() {
		val spacer = viewBinding.foldHingeSpacer ?: return
		lifecycleScope.launch {
			WindowInfoTracker.getOrCreate(this@MainActivity)
				.windowLayoutInfo(this@MainActivity)
				.collect { layoutInfo ->
					val hingeWidth = layoutInfo.displayFeatures
						.filterIsInstance<FoldingFeature>()
						.firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
						?.bounds
						?.width()
						?: 0
					spacer.isVisible = hingeWidth > 0
					spacer.updateLayoutParams<LinearLayout.LayoutParams> {
						width = hingeWidth
					}
				}
		}
	}

	private fun SearchView.observeState() = callbackFlow {
		val listener = SearchView.TransitionListener { _, _, state ->
			trySendBlocking(state)
		}
		addTransitionListener(listener)
		awaitClose { removeTransitionListener(listener) }
	}

	private companion object {

		private const val FAB_SHRINK_DELAY_MS = 1500L
		private const val ACTION_MODE_TOP_BAR_FADE_DURATION_MS = 150L
	}
}

private data class OverflowMenuProviderEntry(
	val provider: MenuProvider,
	val owner: LifecycleOwner?,
	val minState: Lifecycle.State,
) {

	val isActive: Boolean
		get() = owner?.lifecycle?.currentState?.isAtLeast(minState) ?: true
}
