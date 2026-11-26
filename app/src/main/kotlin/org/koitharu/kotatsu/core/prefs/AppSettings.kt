package org.koitharu.kotatsu.core.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.ArraySet
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.ZoomMode
import org.koitharu.kotatsu.core.network.DoHProvider
import org.koitharu.kotatsu.core.os.AppIcon
import org.koitharu.kotatsu.core.prefs.AppProtectionTimeout
import org.koitharu.kotatsu.core.util.ext.connectivityManager
import org.koitharu.kotatsu.core.util.ext.getEnumValue
import org.koitharu.kotatsu.core.util.ext.observeChanges
import org.koitharu.kotatsu.core.util.ext.putAll
import org.koitharu.kotatsu.core.util.ext.putEnumValue
import org.koitharu.kotatsu.core.util.ext.takeIfReadable
import org.koitharu.kotatsu.core.util.ext.toUriOrNull
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.find
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.koitharu.kotatsu.reader.domain.ReaderColorFilter
import java.io.File
import java.net.Proxy
import java.util.EnumSet
import java.util.HashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koitharu.kotatsu.core.model.ContentWarning
import org.koitharu.kotatsu.mihon.model.ExternalRepoInfo
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionInstallMode
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreRegistryState

@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {

	private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
	private val json = Json { ignoreUnknownKeys = true }
	private val connectivityManager = context.connectivityManager
	private val mangaListBadgesDefault = ArraySet(context.resources.getStringArray(R.array.values_list_badges))

	var listMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE, ListMode.GRID)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE, value) }

	val theme: Int
		get() = prefs.getString(KEY_THEME, null)?.toIntOrNull()
			?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

	val colorScheme: ColorScheme
		get() = prefs.getEnumValue(KEY_COLOR_THEME, ColorScheme.default)

	val isAmoledTheme: Boolean
		get() = prefs.getBoolean(KEY_THEME_AMOLED, false)

	var mainNavItems: List<NavItem>
		get() {
			val raw = prefs.getString(KEY_NAV_MAIN, null)?.split(',')
			return if (raw.isNullOrEmpty()) {
				listOf(NavItem.HISTORY, NavItem.FAVORITES, NavItem.FEED, NavItem.EXPLORE)
			} else {
				raw.mapNotNull { x -> NavItem.entries.find(x) }.ifEmpty { listOf(NavItem.EXPLORE) }
			}
		}
		set(value) {
			prefs.edit {
				putString(KEY_NAV_MAIN, value.joinToString(",") { it.name })
			}
		}

	val isNavLabelsVisible: Boolean
		get() = prefs.getBoolean(KEY_NAV_LABELS, true)

	val isNavBarPinned: Boolean
		get() = prefs.getBoolean(KEY_NAV_PINNED, true)

	val isBarBlurEnabled: Boolean
		get() = prefs.getBoolean(KEY_BAR_BLUR, true)

	val isLegacyNavigationBar: Boolean
		get() = prefs.getBoolean(KEY_NAV_LEGACY, false)

	val isFloatingNavBar: Boolean
		get() = if (prefs.contains(KEY_FLOATING_NAV)) {
			prefs.getBoolean(KEY_FLOATING_NAV, false)
		} else {
			true
		}

	val isMainFabEnabled: Boolean
		get() = prefs.getBoolean(KEY_MAIN_FAB, true)

	var dismissedUpdateVersion: String?
		get() = prefs.getString(KEY_UPDATE_PROMPT_DISMISSED, null)
		set(value) = prefs.edit { putString(KEY_UPDATE_PROMPT_DISMISSED, value) }

	var uiScalePercent: Int
		get() = prefs.getInt(KEY_UI_SCALE, 100)
		set(value) = prefs.edit { putInt(KEY_UI_SCALE, value) }

	var isTitleOverCover: Boolean
		get() = prefs.getBoolean(KEY_TITLE_OVER_COVER, true)
		set(value) = prefs.edit { putBoolean(KEY_TITLE_OVER_COVER, value) }

	var isGridSpacingIncreased: Boolean
		get() = prefs.getBoolean(KEY_GRID_SPACING_INCREASED, false)
		set(value) = prefs.edit { putBoolean(KEY_GRID_SPACING_INCREASED, value) }

	var isTitleTapToReadEnabled: Boolean
		get() = prefs.getBoolean(KEY_TITLE_TAP_TO_READ, false)
		set(value) = prefs.edit { putBoolean(KEY_TITLE_TAP_TO_READ, value) }

	var appIcon: AppIcon
		get() = prefs.getEnumValue(KEY_APP_ICON, AppIcon.DEFAULT)
		set(value) = prefs.edit { putEnumValue(KEY_APP_ICON, value) }

	var isAppProtectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_PROTECT_APP, false) || !appPassword.isNullOrEmpty()
		set(value) = prefs.edit { putBoolean(KEY_PROTECT_APP, value) }

	var appProtectionTimeout: AppProtectionTimeout
		get() = prefs.getEnumValue(KEY_PROTECT_APP_TIMEOUT, AppProtectionTimeout.INSTANT)
		set(value) = prefs.edit { putEnumValue(KEY_PROTECT_APP_TIMEOUT, value) }

	fun clearAppPassword() {
		appPassword = null
		isAppProtectionEnabled = false
	}

	fun updateAppPassword(pin: String) {
		appPassword = pin
		isAppProtectionEnabled = true
	}

	val isStatusBarHidden: Boolean
		get() = prefs.getBoolean("hide_status_bar", false)

	var isFeedCounterAsDot: Boolean
		get() = prefs.getBoolean(KEY_FEED_COUNTER_DOT, false)
		set(value) = prefs.edit { putBoolean(KEY_FEED_COUNTER_DOT, value) }

	var gridSize: Int
		get() = prefs.getInt(KEY_GRID_SIZE, 100)
		set(value) = prefs.edit { putInt(KEY_GRID_SIZE, value) }

	var gridSizePages: Int
		get() = prefs.getInt(KEY_GRID_SIZE_PAGES, 100)
		set(value) = prefs.edit { putInt(KEY_GRID_SIZE_PAGES, value) }

	/** The covers of the Explore tab, set apart from the library's: its charts are laid out closer together. */
	var gridSizeDiscover: Int
		get() = prefs.getInt(KEY_GRID_SIZE_DISCOVER, 100)
		set(value) = prefs.edit { putInt(KEY_GRID_SIZE_DISCOVER, value) }

	val isQuickFilterEnabled: Boolean
		get() = prefs.getBoolean(KEY_QUICK_FILTER, true)

	val isDescriptionExpanded: Boolean
		get() = !prefs.getBoolean(KEY_COLLAPSE_DESCRIPTION, true)

	var historyListMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE_HISTORY, listMode)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE_HISTORY, value) }

	var suggestionsListMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE_SUGGESTIONS, listMode)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE_SUGGESTIONS, value) }

	var favoritesListMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE_FAVORITES, listMode)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE_FAVORITES, value) }


	var isNsfwContentDisabled: Boolean
		get() = contentFilter < ContentWarning.NSFW
		set(value) {
			contentFilter = if (value) ContentWarning.SAFE else ContentWarning.NSFW
		}

	var contentFilter: ContentWarning
		get() = prefs.getString(KEY_CONTENT_FILTER, null)?.let { x -> ContentWarning.entries.find { it.name == x } }
			?: if (prefs.getBoolean(KEY_DISABLE_NSFW, false)) ContentWarning.SAFE else ContentWarning.NSFW
		set(value) = prefs.edit {
			putString(KEY_CONTENT_FILTER, value.name)
			putBoolean(KEY_DISABLE_NSFW, value < ContentWarning.NSFW)
		}

	var appLocales: LocaleListCompat
		get() {
			val raw = prefs.getString(KEY_APP_LOCALE, null)
			return LocaleListCompat.forLanguageTags(raw)
		}
		set(value) {
			prefs.edit {
				putString(KEY_APP_LOCALE, value.toLanguageTags())
			}
		}

	var isReaderDoubleOnLandscape: Boolean
		get() = prefs.getBoolean(KEY_READER_DOUBLE_PAGES, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_DOUBLE_PAGES, value) }

	var isReaderDoubleOnFoldable: Boolean
		get() = prefs.getBoolean(KEY_READER_DOUBLE_FOLDABLE, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_DOUBLE_FOLDABLE, value) }

	var isReaderDoubleCoverPage: Boolean
		get() = prefs.getBoolean(KEY_READER_DOUBLE_COVER_PAGE, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_DOUBLE_COVER_PAGE, value) }

	@get:FloatRange(0.0, 1.0)
	var readerDoublePagesSensitivity: Float
		get() = getFloatCompat(KEY_READER_DOUBLE_PAGES_SENSITIVITY, 0.5f).coerceIn(0f, 1f)
		set(@FloatRange(0.0, 1.0) value) = prefs.edit { putFloat(KEY_READER_DOUBLE_PAGES_SENSITIVITY, value) }

	val readerScreenOrientation: Int
		get() = prefs.getString(KEY_READER_ORIENTATION, null)?.toIntOrNull()
			?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

	val isReaderVolumeButtonsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_VOLUME_BUTTONS, false)

	val isReaderZoomButtonsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_ZOOM_BUTTONS, false)

	val isReaderControlAlwaysLTR: Boolean
		get() = prefs.getBoolean(KEY_READER_CONTROL_LTR, false)

	val isReaderNavigationInverted: Boolean
		get() = prefs.getBoolean(KEY_READER_NAVIGATION_INVERTED, false)

	val isReaderFullscreenEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_FULLSCREEN, true)

	val isReaderOptimizationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_OPTIMIZE, false)

	val isEInkFlashEnabled: Boolean
		get() = prefs.getBoolean(KEY_EINK_FLASH, false)

	val eInkFlashDuration: Int
		get() = prefs.getInt(KEY_EINK_FLASH_DURATION, 300).coerceIn(100, 1000)

	val eInkFlashEvery: Int
		get() = prefs.getInt(KEY_EINK_FLASH_EVERY, 1).coerceAtLeast(1)

	val eInkFlashColor: EInkFlashColor
		get() = prefs.getEnumValue(KEY_EINK_FLASH_COLOR, EInkFlashColor.WHITE)

	val readerControls: Set<ReaderControl>
		get() = prefs.getStringSet(KEY_READER_CONTROLS, null)?.mapNotNullTo(EnumSet.noneOf(ReaderControl::class.java)) {
			ReaderControl.entries.find(it)
		} ?: ReaderControl.DEFAULT.toSet()

	var readerControlsLayout: List<Pair<ReaderControl, Boolean>>
		get() {
			val stored = prefs.getString(KEY_READER_CONTROLS_ORDERED, null)
				?.split(',')
				?.mapNotNull { token ->
					val name = token.trim()
					val control = ReaderControl.entries.find(name.removePrefix("-")) ?: return@mapNotNull null
					control to !name.startsWith('-')
				}
				?.distinctBy { it.first }
				.orEmpty()
			val base = stored.ifEmpty {
				val legacy = readerControls
				ReaderControl.entries.map { it to (it in legacy) }
			}
			val known = base.mapToSet { it.first }
			return base + ReaderControl.entries.filterNot { it in known }.map { it to false }
		}
		set(value) = prefs.edit {
			putString(KEY_READER_CONTROLS_ORDERED, value.joinToString(",") { (c, isShown) -> if (isShown) c.name else "-${c.name}" })
		}

	val isOfflineCheckDisabled: Boolean
		get() = prefs.getBoolean(KEY_OFFLINE_DISABLED, false)

	var isAllFavouritesVisible: Boolean
		get() = prefs.getBoolean(KEY_ALL_FAVOURITES_VISIBLE, true)
		set(value) = prefs.edit { putBoolean(KEY_ALL_FAVOURITES_VISIBLE, value) }

	val isTrackerEnabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_ENABLED, true)

	val isTrackerWifiOnly: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_WIFI_ONLY, false)

	val trackerFrequencyFactor: Float
		get() = prefs.getString(KEY_TRACKER_FREQUENCY, null)?.toFloatOrNull() ?: 1f

	/**
	 * How often to run a background sync, in hours, or `0` to only sync when local data changes.
	 * Applied by SyncController.updateSyncSchedule.
	 */
	val syncPeriodHours: Int
		get() = prefs.getString(KEY_SYNC_PERIOD, null)?.toIntOrNull() ?: SYNC_PERIOD_DEFAULT

	val isTrackerNotificationsEnabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_NOTIFICATIONS, true)

	val isTrackerNsfwDisabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_NO_NSFW, false)

	val trackerDownloadStrategy: TrackerDownloadStrategy
		get() = prefs.getEnumValue(KEY_TRACKER_DOWNLOAD, TrackerDownloadStrategy.DISABLED)

	var isTrackerUnstuckMigrationDone: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_UNSTUCK_MIGRATION_V4, false)
		set(value) = prefs.edit { putBoolean(KEY_TRACKER_UNSTUCK_MIGRATION_V4, value) }

	var isTrackerProgressRefreshDone: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_PROGRESS_REFRESH_V1, false)
		set(value) = prefs.edit { putBoolean(KEY_TRACKER_PROGRESS_REFRESH_V1, value) }

	var notificationSound: Uri
		get() = prefs.getString(KEY_NOTIFICATIONS_SOUND, null)?.toUriOrNull()
			?: Settings.System.DEFAULT_NOTIFICATION_URI
		set(value) = prefs.edit { putString(KEY_NOTIFICATIONS_SOUND, value.toString()) }

	val notificationVibrate: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATIONS_VIBRATE, false)

	val notificationLight: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATIONS_LIGHT, true)

	val readerAnimation: ReaderAnimation
		get() = prefs.getEnumValue(KEY_READER_ANIMATION, ReaderAnimation.DEFAULT)

	val readerBackground: ReaderBackground
		get() = prefs.getEnumValue(KEY_READER_BACKGROUND, ReaderBackground.DEFAULT)

	val defaultReaderMode: ReaderMode
		get() = prefs.getEnumValue(KEY_READER_MODE, ReaderMode.STANDARD)

	val isReaderModeDetectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_MODE_DETECT, true)

	var isHistoryGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_HISTORY_GROUPING, true)
		set(value) = prefs.edit { putBoolean(KEY_HISTORY_GROUPING, value) }

	var isUpdatedGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_UPDATED_GROUPING, true)
		set(value) = prefs.edit { putBoolean(KEY_UPDATED_GROUPING, value) }

	var isFeedHeaderVisible: Boolean
		get() = prefs.getBoolean(KEY_FEED_HEADER, true)
		set(value) = prefs.edit { putBoolean(KEY_FEED_HEADER, value) }

	val progressIndicatorMode: ProgressIndicatorMode
		get() = prefs.getEnumValue(KEY_PROGRESS_INDICATORS, ProgressIndicatorMode.PERCENT_READ)

	var incognitoModeForNsfw: TriStateOption
		get() = prefs.getEnumValue(KEY_INCOGNITO_NSFW, TriStateOption.ASK)
		set(value) = prefs.edit { putEnumValue(KEY_INCOGNITO_NSFW, value) }

	var isIncognitoModeEnabled: Boolean
		get() = prefs.getBoolean(KEY_INCOGNITO_MODE, false)
		set(value) = prefs.edit { putBoolean(KEY_INCOGNITO_MODE, value) }

	var isVerboseLoggingEnabled: Boolean
		get() = prefs.getBoolean(KEY_VERBOSE_LOGGING, false)
		set(value) = prefs.edit { putBoolean(KEY_VERBOSE_LOGGING, value) }

	val isReaderMultiTaskEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_MULTITASK, false)

	var isChaptersReverse: Boolean
		get() = prefs.getBoolean(KEY_REVERSE_CHAPTERS, false)
		set(value) = prefs.edit { putBoolean(KEY_REVERSE_CHAPTERS, value) }

	var isChaptersGridView: Boolean
		get() = prefs.getBoolean(KEY_GRID_VIEW_CHAPTERS, false)
		set(value) = prefs.edit { putBoolean(KEY_GRID_VIEW_CHAPTERS, value) }

	val zoomMode: ZoomMode
		get() = prefs.getEnumValue(KEY_ZOOM_MODE, ZoomMode.FIT_CENTER)

	val trackSources: Set<String>
		get() = prefs.getStringSet(KEY_TRACK_SOURCES, null) ?: setOf(TRACK_FAVOURITES)

	var appPassword: String?
		get() = prefs.getString(KEY_APP_PASSWORD, null)
		set(value) = prefs.edit {
			if (value != null) putString(KEY_APP_PASSWORD, value) else remove(KEY_APP_PASSWORD)
		}

	var isAppPasswordNumeric: Boolean
		get() = prefs.getBoolean(KEY_APP_PASSWORD_NUMERIC, false)
		set(value) = prefs.edit { putBoolean(KEY_APP_PASSWORD_NUMERIC, value) }

	val searchSuggestionTypes: Set<SearchSuggestionType>
		get() = prefs.getStringSet(KEY_SEARCH_SUGGESTION_TYPES, null)?.let { stringSet ->
			stringSet.mapNotNullTo(EnumSet.noneOf(SearchSuggestionType::class.java)) { x ->
				enumValueOf<SearchSuggestionType>(x)
			}
		} ?: EnumSet.allOf(SearchSuggestionType::class.java)

	var isBiometricProtectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_PROTECT_APP_BIOMETRIC, true)
		set(value) = prefs.edit { putBoolean(KEY_PROTECT_APP_BIOMETRIC, value) }

	val isMirrorSwitchingEnabled: Boolean
		get() = prefs.getBoolean(KEY_MIRROR_SWITCHING, false)

	val isExitConfirmationEnabled: Boolean
		get() = prefs.getBoolean(KEY_EXIT_CONFIRM, false)

	val isDynamicShortcutsEnabled: Boolean
		get() = prefs.getBoolean(KEY_SHORTCUTS, true)

	/**
	 * Keep adult manga out of the launcher's long-press shortcuts.
	 *
	 * Defaults to `true`, unlike the other per-surface NSFW switches: the home screen is visible to
	 * anyone holding the phone, not just to whoever opened the app.
	 */
	val isShortcutsNsfwDisabled: Boolean
		get() = prefs.getBoolean(KEY_SHORTCUTS_NO_NSFW, true)

	val isUnstableUpdatesAllowed: Boolean
		get() = prefs.getBoolean(KEY_UPDATES_UNSTABLE, false)

	val isPagesTabEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_TAB, true)

	val defaultDetailsTab: Int
		get() = if (isPagesTabEnabled) {
			val raw = prefs.getString(KEY_DETAILS_TAB, null)?.toIntOrNull() ?: -1
			if (raw == -1) {
				lastDetailsTab
			} else {
				raw
			}.coerceIn(0, 2)
		} else {
			0
		}

	var lastDetailsTab: Int
		get() = prefs.getInt(KEY_DETAILS_LAST_TAB, 0)
		set(value) = prefs.edit { putInt(KEY_DETAILS_LAST_TAB, value) }

	val isBackdropEnabled: Boolean
		get() = prefs.getBoolean(KEY_DETAILS_BACKDROP, true)

	var backdropBlurAmount: Int
		get() {
			val raw = prefs.getInt(KEY_DETAILS_BACKDROP_BLUR_AMOUNT, 2)
			return when {
				raw <= 0 -> 0
				raw == 1 -> 1
				else -> 2
			}
		}
		set(value) = prefs.edit { putInt(KEY_DETAILS_BACKDROP_BLUR_AMOUNT, value) }

	var detailsUiMode: DetailsUiMode
		get() = prefs.getEnumValue(KEY_DETAILS_UI, DetailsUiMode.EXPRESSIVE)
		set(value) = prefs.edit { putEnumValue(KEY_DETAILS_UI, value) }

	val isContentPrefetchEnabled: Boolean
		get() {
			if (isBackgroundNetworkRestricted()) {
				return false
			}
			val policy =
				NetworkPolicy.from(prefs.getString(KEY_PREFETCH_CONTENT, null), NetworkPolicy.NEVER)
			return policy.isNetworkAllowed(connectivityManager)
		}

	var sourcesSortOrder: SourcesSortOrder
		get() = prefs.getEnumValue(KEY_SOURCES_ORDER, SourcesSortOrder.SCORE)
		set(value) = prefs.edit { putEnumValue(KEY_SOURCES_ORDER, value) }

	var isSourcesGridMode: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_GRID, true)
		set(value) = prefs.edit { putBoolean(KEY_SOURCES_GRID, value) }

	var sourcesVersion: Int
		get() = prefs.getInt(KEY_SOURCES_VERSION, 0)
		set(value) = prefs.edit { putInt(KEY_SOURCES_VERSION, value) }

	/** Sources were added by parsers downloaded since the list of new ones was last looked at. */
	var hasNewDownloadedSources: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_NEW_DOWNLOADED, false)
		set(value) = prefs.edit { putBoolean(KEY_SOURCES_NEW_DOWNLOADED, value) }

	/** Whether newer parsers than the app's own are looked for and taken in, see ParsersUpdater. */
	val isParsersAutoUpdateEnabled: Boolean
		get() = prefs.getBoolean(KEY_PARSERS_AUTO_UPDATE, true)

	var isAllSourcesEnabled: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_ENABLED_ALL, false)
		set(value) = prefs.edit { putBoolean(KEY_SOURCES_ENABLED_ALL, value) }

	var activeSourcePresetId: Long
		get() = getLongCompat(KEY_ACTIVE_SOURCE_PRESET, 0L)
		set(value) = prefs.edit { putLong(KEY_ACTIVE_SOURCE_PRESET, value) }

	val isPagesNumbersEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_NUMBERS, false)

	val screenshotsPolicy: ScreenshotsPolicy
		get() = prefs.getEnumValue(KEY_SCREENSHOTS_POLICY, ScreenshotsPolicy.ALLOW)

	var userSpecifiedMangaDirectories: Set<File>
		get() {
			val set = prefs.getStringSet(KEY_LOCAL_MANGA_DIRS, emptySet()).orEmpty()
			return set.mapNotNullToSet { File(it).takeIfReadable() }
		}
		set(value) {
			val set = value.mapToSet { it.absolutePath }
			prefs.edit { putStringSet(KEY_LOCAL_MANGA_DIRS, set) }
		}

	var mangaStorageDir: File?
		get() = prefs.getString(KEY_LOCAL_STORAGE, null)?.let {
			File(it)
		}?.takeIf { it.exists() && it in userSpecifiedMangaDirectories }
		set(value) = prefs.edit {
			if (value == null) {
				remove(KEY_LOCAL_STORAGE)
			} else {
				val userDirs = userSpecifiedMangaDirectories
				if (value !in userDirs) {
					userSpecifiedMangaDirectories = userDirs + value
				}
				putString(KEY_LOCAL_STORAGE, value.path)
			}
		}

	var allowDownloadOnMeteredNetwork: TriStateOption
		get() = prefs.getEnumValue(KEY_DOWNLOADS_METERED_NETWORK, TriStateOption.ASK)
		set(value) = prefs.edit { putEnumValue(KEY_DOWNLOADS_METERED_NETWORK, value) }

	val preferredDownloadFormat: DownloadFormat
		get() = prefs.getEnumValue(KEY_DOWNLOADS_FORMAT, DownloadFormat.AUTOMATIC)

	var isSuggestionsEnabled: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
		set(value) = prefs.edit { putBoolean(KEY_SUGGESTIONS, value) }

	val isSuggestionsWiFiOnly: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_WIFI_ONLY, false)

	val isSuggestionsExcludeNsfw: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_EXCLUDE_NSFW, false)

	val isSuggestionsIncludeDisabledSources: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_DISABLED_SOURCES, false)

	val isSuggestionsNotificationAvailable: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_NOTIFICATIONS, false)

	val suggestionsTagsBlacklist: Set<String>
		get() {
			val string = prefs.getString(KEY_SUGGESTIONS_EXCLUDE_TAGS, null)?.trimEnd(' ', ',')
			if (string.isNullOrEmpty()) {
				return emptySet()
			}
			return string.split(',').mapToSet { it.trim() }
		}

	val isReaderBarEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_BAR, true)

	val isReaderBarTransparent: Boolean
		get() = prefs.getBoolean(KEY_READER_BAR_TRANSPARENT, true)

	val isReaderChapterToastEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_CHAPTER_TOAST, true)

	val isReaderKeepScreenOn: Boolean
		get() = prefs.getBoolean(KEY_READER_SCREEN_ON, true)

	var readerColorFilter: ReaderColorFilter?
		get() = runCatching {
			ReaderColorFilter(
				brightness = getFloatCompat(KEY_CF_BRIGHTNESS, ReaderColorFilter.EMPTY.brightness),
				contrast = getFloatCompat(KEY_CF_CONTRAST, ReaderColorFilter.EMPTY.contrast),
				isInverted = prefs.getBoolean(KEY_CF_INVERTED, ReaderColorFilter.EMPTY.isInverted),
				isGrayscale = prefs.getBoolean(KEY_CF_GRAYSCALE, ReaderColorFilter.EMPTY.isGrayscale),
				isBookBackground = prefs.getBoolean(KEY_CF_BOOK, ReaderColorFilter.EMPTY.isBookBackground),
			).takeUnless { it.isEmpty }
		}.getOrNull()
		set(value) {
			prefs.edit {
				if (value != null) {
					putFloat(KEY_CF_BRIGHTNESS, value.brightness)
					putFloat(KEY_CF_CONTRAST, value.contrast)
					putBoolean(KEY_CF_INVERTED, value.isInverted)
					putBoolean(KEY_CF_GRAYSCALE, value.isGrayscale)
					putBoolean(KEY_CF_BOOK, value.isBookBackground)
				} else {
					remove(KEY_CF_BRIGHTNESS)
					remove(KEY_CF_CONTRAST)
					remove(KEY_CF_INVERTED)
					remove(KEY_CF_GRAYSCALE)
					remove(KEY_CF_BOOK)
				}
			}
		}

	val imagesProxy: Int
		get() {
			val raw = prefs.getString(KEY_IMAGES_PROXY, null)?.toIntOrNull()
			return raw ?: if (prefs.getBoolean(KEY_IMAGES_PROXY_OLD, false)) 0 else -1
		}

	val dnsOverHttps: DoHProvider
		get() = prefs.getEnumValue(KEY_DOH, DoHProvider.NONE)

	var isSSLBypassEnabled: Boolean
		get() = prefs.getBoolean(KEY_SSL_BYPASS, false)
		set(value) = prefs.edit { putBoolean(KEY_SSL_BYPASS, value) }

	val proxyType: Proxy.Type
		get() {
			val raw = prefs.getString(KEY_PROXY_TYPE, null) ?: return Proxy.Type.DIRECT
			return enumValues<Proxy.Type>().find { it.name == raw } ?: Proxy.Type.DIRECT
		}

	val proxyAddress: String?
		get() = prefs.getString(KEY_PROXY_ADDRESS, null)

	val proxyPort: Int
		get() = prefs.getString(KEY_PROXY_PORT, null)?.toIntOrNull() ?: 0

	val proxyLogin: String?
		get() = prefs.getString(KEY_PROXY_LOGIN, null)?.nullIfEmpty()

	val proxyPassword: String?
		get() = prefs.getString(KEY_PROXY_PASSWORD, null)?.nullIfEmpty()

	var localListOrder: SortOrder
		get() = prefs.getEnumValue(KEY_LOCAL_LIST_ORDER, SortOrder.NEWEST)
		set(value) = prefs.edit { putEnumValue(KEY_LOCAL_LIST_ORDER, value) }

	var historySortOrder: ListSortOrder
		get() = prefs.getEnumValue(KEY_HISTORY_ORDER, ListSortOrder.LAST_READ)
		set(value) = prefs.edit { putEnumValue(KEY_HISTORY_ORDER, value) }

	var allFavoritesSortOrder: ListSortOrder
		get() = prefs.getEnumValue(KEY_FAVORITES_ORDER, ListSortOrder.NEWEST)
		set(value) = prefs.edit { putEnumValue(KEY_FAVORITES_ORDER, value) }

	val isRelatedMangaEnabled: Boolean
		get() = prefs.getBoolean(KEY_RELATED_MANGA, true)

	val isWebtoonZoomEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_ZOOM, true)

	var isWebtoonGapsEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_GAPS, false)
		set(value) = prefs.edit { putBoolean(KEY_WEBTOON_GAPS, value) }

	var isWebtoonPullGestureEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_PULL_GESTURE, false)
		set(value) = prefs.edit { putBoolean(KEY_WEBTOON_PULL_GESTURE, value) }

	val isReaderEraseGestureEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_ERASE_GESTURE, true)

	/**
	 * A saved layout's EXPLORE used to be the list of sources, which is now the SOURCES tab, so it gets
	 * that tab beside the new screen, once. Removing it afterwards has to stick, hence the flag. A bar
	 * already at the limit is left alone: adding one would push the reader's own last tab off the end, and
	 * the Explore screen links to the same place.
	 */
	private fun withSourcesTab(items: List<NavItem>): List<NavItem> {
		if (prefs.getBoolean(KEY_NAV_SOURCES_OFFERED, false)) {
			return items
		}
		prefs.edit { putBoolean(KEY_NAV_SOURCES_OFFERED, true) }
		val index = items.indexOf(NavItem.EXPLORE)
		if (index < 0 || NavItem.SOURCES in items || items.size >= NAV_ITEMS_ALWAYS_FIT) {
			return items
		}
		val updated = items.toMutableList().apply { add(index + 1, NavItem.SOURCES) }
		mainNavItems = updated
		return updated
	}

	@get:FloatRange(from = 0.0, to = 0.5)
	val defaultWebtoonZoomOut: Float
		get() = prefs.getInt(KEY_WEBTOON_ZOOM_OUT, 0).coerceIn(0, 50) / 100f

	@get:FloatRange(from = 0.0, to = 1.0)
	var readerAutoscrollSpeed: Float
		get() = getFloatCompat(KEY_READER_AUTOSCROLL_SPEED, 0f)
		set(@FloatRange(from = 0.0, to = 1.0) value) = prefs.edit {
			putFloat(
				KEY_READER_AUTOSCROLL_SPEED,
				value,
			)
		}

	var isReaderAutoscrollFabVisible: Boolean
		get() = prefs.getBoolean(KEY_READER_AUTOSCROLL_FAB, true)
		set(value) = prefs.edit { putBoolean(KEY_READER_AUTOSCROLL_FAB, value) }

	val isPagesPreloadEnabled: Boolean
		get() {
			if (isBackgroundNetworkRestricted()) {
				return false
			}
			val policy = NetworkPolicy.from(
				prefs.getString(KEY_PAGES_PRELOAD, null),
				NetworkPolicy.NON_METERED,
			)
			return policy.isNetworkAllowed(connectivityManager)
		}

	val is32BitColorsEnabled: Boolean
		get() = prefs.getBoolean(KEY_32BIT_COLOR, false)

	val isDiscordRpcEnabled: Boolean
		get() = prefs.getBoolean(KEY_DISCORD_RPC, false)

	val isDiscordRpcSkipNsfw: Boolean
		get() = prefs.getBoolean(KEY_DISCORD_RPC_SKIP_NSFW, false)

	var discordToken: String?
		get() = prefs.getString(KEY_DISCORD_TOKEN, null)?.trim()?.nullIfEmpty()
		set(value) = prefs.edit { putString(KEY_DISCORD_TOKEN, value?.nullIfEmpty()) }

	val isPeriodicalBackupEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_PERIODICAL_ENABLED, false)

	val periodicalBackupFrequency: Float
		get() = prefs.getString(KEY_BACKUP_PERIODICAL_FREQUENCY, null)?.toFloatOrNull() ?: 7f

	val periodicalBackupFrequencyMillis: Long
		get() = (TimeUnit.DAYS.toMillis(1) * periodicalBackupFrequency).toLong()

	val periodicalBackupMaxCount: Int
		get() = if (prefs.getBoolean(KEY_BACKUP_PERIODICAL_TRIM, true)) {
			prefs.getInt(KEY_BACKUP_PERIODICAL_COUNT, 10)
		} else {
			Int.MAX_VALUE
		}

	var periodicalBackupDirectory: Uri?
		get() = prefs.getString(KEY_BACKUP_PERIODICAL_OUTPUT, null)?.toUriOrNull()
		set(value) = prefs.edit { putString(KEY_BACKUP_PERIODICAL_OUTPUT, value?.toString()) }

	val isBackupTelegramUploadEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_TG_ENABLED, false)

	val backupTelegramChatId: String?
		get() = prefs.getString(KEY_BACKUP_TG_CHAT, null)?.nullIfEmpty()

	val isReadingTimeEstimationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READING_TIME, true)

	val isPagesSavingAskEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_SAVE_ASK, true)

	val isStatsEnabled: Boolean
		get() = prefs.getBoolean(KEY_STATS_ENABLED, false)

	val isAutoLocalChaptersCleanupEnabled: Boolean
		get() = prefs.getBoolean(KEY_CHAPTERS_CLEAR_AUTO, false)

	fun isPagesCropEnabled(mode: ReaderMode): Boolean {
		val rawValue = prefs.getStringSet(KEY_READER_CROP, emptySet())
		if (rawValue.isNullOrEmpty()) {
			return false
		}
		val needle = if (mode == ReaderMode.WEBTOON) READER_CROP_WEBTOON else READER_CROP_PAGED
		return needle.toString() in rawValue
	}

	fun isTipEnabled(tip: String): Boolean {
		return prefs.getStringSet(KEY_TIPS_CLOSED, emptySet())?.contains(tip) != true
	}

	fun reopenTip(tip: String) {
		val closedTips = prefs.getStringSet(KEY_TIPS_CLOSED, emptySet()).orEmpty()
		prefs.edit { putStringSet(KEY_TIPS_CLOSED, closedTips - tip) }
	}

	fun closeTip(tip: String) {
		val closedTips = prefs.getStringSet(KEY_TIPS_CLOSED, emptySet()).orEmpty()
		if (tip in closedTips) {
			return
		}
		prefs.edit { putStringSet(KEY_TIPS_CLOSED, closedTips + tip) }
	}

	fun isIncognitoModeEnabled(isNsfw: Boolean): Boolean {
		return isIncognitoModeEnabled || (isNsfw && incognitoModeForNsfw == TriStateOption.ENABLED)
	}

	fun getPagesSaveDir(context: Context): DocumentFile? =
		prefs.getString(KEY_PAGES_SAVE_DIR, null)?.toUriOrNull()?.let {
			DocumentFile.fromTreeUri(context, it)?.takeIf { it.canWrite() }
		}

	fun setPagesSaveDir(uri: Uri?) {
		prefs.edit { putString(KEY_PAGES_SAVE_DIR, uri?.toString()) }
	}

	fun getMangaListBadges(): Int {
		val raw = prefs.getStringSet(KEY_MANGA_LIST_BADGES, mangaListBadgesDefault).orEmpty()
		var result = 0
		for (item in raw) {
			result = result or item.toInt()
		}
		return result
	}

	fun subscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		prefs.registerOnSharedPreferenceChangeListener(listener)
	}

	fun unsubscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		prefs.unregisterOnSharedPreferenceChangeListener(listener)
	}

	fun observeChanges() = prefs.observeChanges()

	fun observe(vararg keys: String): Flow<String?> = prefs.observeChanges()
		.filter { key -> key == null || key in keys }
		.onStart { emit(null) }
		.flowOn(Dispatchers.IO)

	fun getAllValues(): Map<String, *> = prefs.all

	fun upsertAll(m: Map<String, *>) = prefs.edit {
		clear()
		putAll(normalizeImportedPreferences(m))
	}

	private fun getLongCompat(key: String, defaultValue: Long): Long = try {
		prefs.getLong(key, defaultValue)
	} catch (_: ClassCastException) {
		val fixedValue = when (val rawValue = prefs.all[key]) {
			is Number -> rawValue.toLong()
			is String -> rawValue.toLongOrNull()
			else -> null
		}
		if (fixedValue == null) {
			prefs.edit { remove(key) }
			defaultValue
		} else {
			prefs.edit { putLong(key, fixedValue) }
			fixedValue
		}
	}

	private fun getFloatCompat(key: String, defaultValue: Float): Float = try {
		prefs.getFloat(key, defaultValue)
	} catch (_: ClassCastException) {
		val fixedValue = when (val rawValue = prefs.all[key]) {
			is Number -> rawValue.toFloat()
			is String -> rawValue.toFloatOrNull()
			else -> null
		}
		if (fixedValue == null || !fixedValue.isFinite()) {
			prefs.edit { remove(key) }
			defaultValue
		} else {
			prefs.edit { putFloat(key, fixedValue) }
			fixedValue
		}
	}

	private fun normalizeImportedPreferences(values: Map<String, *>): Map<String, *> {
		val result = HashMap(values)
		result[KEY_ACTIVE_SOURCE_PRESET]?.let { rawValue ->
			val fixedValue = when (rawValue) {
				is Long -> rawValue
				is Number -> rawValue.toLong()
				is String -> rawValue.toLongOrNull()
				else -> null
			}
			if (fixedValue != null) {
				result[KEY_ACTIVE_SOURCE_PRESET] = fixedValue
			}
		}
		for (key in FLOAT_PREFERENCE_KEYS) {
			result[key]?.let { rawValue ->
				val fixedValue = when (rawValue) {
					is Number -> rawValue.toFloat()
					is String -> rawValue.toFloatOrNull()
					else -> null
				}
				if (fixedValue != null && fixedValue.isFinite()) {
					result[key] = fixedValue
				}
			}
		}
		return result
	}

	var isListCheckpointEnabled: Boolean
		get() = prefs.getBoolean(KEY_LIST_CHECKPOINT, true)
		set(value) = prefs.edit { putBoolean(KEY_LIST_CHECKPOINT, value) }

	fun getListCheckpoint(scope: String): String? {
		val key = KEY_LIST_CHECKPOINT + '_' + scope
		return try {
			prefs.getString(key, null)
		} catch (e: ClassCastException) {
			prefs.edit { remove(key) }
			null
		}
	}

	fun setListCheckpoint(scope: String, value: String?) {
		val key = KEY_LIST_CHECKPOINT + '_' + scope
		prefs.edit {
			if (value == null) remove(key) else putString(key, value)
		}
	}

	var isFavouritesQuickAddEnabled: Boolean
		get() = prefs.getBoolean(KEY_FAVOURITES_QUICK_ADD, false)
		set(value) = prefs.edit { putBoolean(KEY_FAVOURITES_QUICK_ADD, value) }

	var defaultFavouriteCategoryId: Long
		get() = prefs.getLong(KEY_DEFAULT_FAVOURITE_CATEGORY, 0L)
		set(value) = prefs.edit { putLong(KEY_DEFAULT_FAVOURITE_CATEGORY, value) }

	var isHapticFeedbackEnabled: Boolean
		get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
		set(value) = prefs.edit { putBoolean(KEY_HAPTIC_FEEDBACK, value) }

	private fun isBackgroundNetworkRestricted(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
		} else {
			false
		}
	}

	var defaultBrowseSortOrder: SortOrder
		get() = prefs.getEnumValue(KEY_DEFAULT_BROWSE_SORT, SortOrder.POPULARITY)
			.takeIf { it == SortOrder.POPULARITY || it == SortOrder.UPDATED } ?: SortOrder.POPULARITY
		set(value) = prefs.edit { putEnumValue(KEY_DEFAULT_BROWSE_SORT, value) }

	var isShizukuInstallerEnabled: Boolean
		get() = prefs.getBoolean(KEY_SHIZUKU_INSTALLER, false)
		set(value) = prefs.edit {
			putBoolean(KEY_SHIZUKU_INSTALLER, value)
			if (!value) putBoolean(KEY_AUTO_UPDATE_EXTENSIONS, false)
			if (value) putBoolean(KEY_PRIVATE_INSTALLER, false)
		}

	var isPrivateInstallEnabled: Boolean
		get() = prefs.getBoolean(KEY_PRIVATE_INSTALLER, false)
		set(value) = prefs.edit {
			putBoolean(KEY_PRIVATE_INSTALLER, value)
			if (value) putBoolean(KEY_SHIZUKU_INSTALLER, false)
		}

	var isAutoUpdateExtensionsEnabled: Boolean
		get() = prefs.getBoolean(KEY_AUTO_UPDATE_EXTENSIONS, false)
		set(value) = prefs.edit { putBoolean(KEY_AUTO_UPDATE_EXTENSIONS, value) }

	var hasExtensionUpdates: Boolean
		get() = prefs.getBoolean(KEY_EXTENSION_UPDATES_AVAILABLE, false)
		set(value) = prefs.edit { putBoolean(KEY_EXTENSION_UPDATES_AVAILABLE, value) }

	var isExtensionUpdateNotificationsEnabled: Boolean
		get() = prefs.getBoolean(KEY_EXTENSION_UPDATE_NOTIFICATIONS, true)
		set(value) = prefs.edit { putBoolean(KEY_EXTENSION_UPDATE_NOTIFICATIONS, value) }

	var lastExtensionUpdateNotificationTime: Long
		get() = prefs.getLong(KEY_LAST_EXTENSION_UPDATE_NOTIFICATION_TIME, 0L)
		set(value) = prefs.edit { putLong(KEY_LAST_EXTENSION_UPDATE_NOTIFICATION_TIME, value) }

	var mihonPerExtActiveLangs: Set<String>
		get() = prefs.getStringSet(KEY_MIHON_PER_EXT_ACTIVE_LANG, emptySet()).orEmpty()
		set(value) = prefs.edit { putStringSet(KEY_MIHON_PER_EXT_ACTIVE_LANG, value) }

	fun getMihonActiveLang(pkgName: String, sourceName: String): String? {
		val suffix = "\n" + mihonSourceKey(pkgName, sourceName)
		return mihonPerExtActiveLangs.firstOrNull { it.endsWith(suffix) }
			?.substringBefore("\n")
	}

	fun setMihonActiveLang(pkgName: String, sourceName: String, lang: String) {
		val suffix = "\n" + mihonSourceKey(pkgName, sourceName)
		mihonPerExtActiveLangs = mihonPerExtActiveLangs.filterNot { it.endsWith(suffix) }.toSet() + (lang + suffix)
	}

	private fun mihonSourceKey(pkgName: String, sourceName: String): String = "$pkgName\n$sourceName"

	var mihonHiddenPackages: Set<String>
		get() = prefs.getStringSet(KEY_MIHON_HIDDEN_PACKAGES, emptySet()).orEmpty()
		set(value) = prefs.edit { putStringSet(KEY_MIHON_HIDDEN_PACKAGES, value) }

	fun isMihonPackageHidden(pkgName: String): Boolean = pkgName in mihonHiddenPackages

	fun setMihonPackageHidden(pkgName: String, hidden: Boolean) {
		val updated = mihonHiddenPackages.toMutableSet()
		if (hidden) updated.add(pkgName) else updated.remove(pkgName)
		mihonHiddenPackages = updated
	}

	var externalExtensionsRepoUrl: String?
		get() = prefs.getString(KEY_EXTERNAL_EXTENSIONS_REPO_URL, null)?.takeIf { it.isNotBlank() }
		set(value) = prefs.edit {
			if (value.isNullOrBlank()) {
				remove(KEY_EXTERNAL_EXTENSIONS_REPO_URL)
			} else {
				putString(KEY_EXTERNAL_EXTENSIONS_REPO_URL, value.trim())
			}
		}

	private val extensionRepoMap: Map<String, String>
		get() = prefs.getStringSet(KEY_MIHON_EXTENSION_REPOS, emptySet()).orEmpty()
			.mapNotNull { entry ->
				val sep = entry.indexOf('\t')
				if (sep <= 0) null else entry.substring(0, sep) to entry.substring(sep + 1)
			}.toMap()

	fun getExtensionRepoUrls(): Map<String, String> = extensionRepoMap

	val externalRepoInfos: List<ExternalRepoInfo>
		get() = prefs.getString(KEY_MIHON_REPO_INFOS, null)
			?.let { runCatching { json.decodeFromString<List<ExternalRepoInfo>>(it) }.getOrNull() }
			?: emptyList()

	var extensionStoreRegistryState: ExtensionStoreRegistryState
		get() = prefs.getString(KEY_EXTENSION_STORE_REGISTRY, null)
			?.let { runCatching { json.decodeFromString<ExtensionStoreRegistryState>(it) }.getOrNull() }
			?: ExtensionStoreRegistryState()
		set(value) = prefs.edit {
			putString(KEY_EXTENSION_STORE_REGISTRY, json.encodeToString(value))
		}

	var isExtensionStoreMigrationComplete: Boolean
		get() = prefs.getBoolean(KEY_EXTENSION_STORE_MIGRATED, false)
		set(value) = prefs.edit { putBoolean(KEY_EXTENSION_STORE_MIGRATED, value) }

	fun getExtensionStoreLabel(packageName: String, signatures: Collection<String>): String? {
		val state = extensionStoreRegistryState
		val mode = if (isPrivateInstallEnabled) ExtensionInstallMode.SANDBOX else ExtensionInstallMode.SYSTEM
		state.ownerships.firstOrNull { it.mode == mode && it.packageName == packageName }
			?.storeId
			?.let { id -> state.stores.firstOrNull { it.id == id } }
			?.let { return it.displayName }
		return state.stores.filter { store ->
			store.fingerprint?.let { fingerprint ->
				signatures.any { it.equals(fingerprint, ignoreCase = true) }
			} == true
		}.singleOrNull()?.displayName
	}

	var hiddenSourceLanguages: Set<String>
		get() = prefs.getStringSet(KEY_HIDDEN_SOURCE_LANGUAGES, emptySet()).orEmpty()
		set(value) = prefs.edit { putStringSet(KEY_HIDDEN_SOURCE_LANGUAGES, value) }

	val mihonUserAgentOverride: String?
		get() = prefs.getString(KEY_MIHON_USER_AGENT, null)

	companion object {

		const val DEFAULT_MIHON_USER_AGENT =
			"Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

		const val KEY_CONTENT_FILTER = "content_filter"
		const val KEY_DEFAULT_BROWSE_SORT = "default_browse_sort"
		const val KEY_SHIZUKU_INSTALLER = "shizuku_installer"
		const val KEY_PRIVATE_INSTALLER = "private_installer"
		const val KEY_AUTO_UPDATE_EXTENSIONS = "auto_update_extensions"
		const val KEY_EXTENSION_UPDATE_NOTIFICATIONS = "extension_update_notifications"
		const val KEY_EXTENSION_UPDATES_AVAILABLE = "extension_updates_available"
		const val KEY_LAST_EXTENSION_UPDATE_NOTIFICATION_TIME = "last_extension_update_notification_time"
		const val KEY_MIHON_USER_AGENT = "mihon_user_agent"
		const val KEY_MIHON_PER_EXT_ACTIVE_LANG = "mihon_per_ext_active_lang"
		const val KEY_MIHON_HIDDEN_PACKAGES = "mihon_hidden_packages"
		const val KEY_EXTERNAL_EXTENSIONS_REPO_URL = "external_extensions_repo_url"
		const val KEY_MIHON_EXTENSION_REPOS = "mihon_extension_repos"
		const val KEY_MIHON_REPO_INFOS = "mihon_repo_infos"
		const val KEY_HIDDEN_SOURCE_LANGUAGES = "hidden_source_languages"
		const val KEY_EXTENSION_STORE_REGISTRY = "extension_store_registry"
		const val KEY_EXTENSION_STORE_MIGRATED = "extension_store_migrated"

		private val FLOAT_PREFERENCE_KEYS = setOf(
			KEY_READER_DOUBLE_PAGES_SENSITIVITY,
			KEY_CF_BRIGHTNESS,
			KEY_CF_CONTRAST,
			KEY_READER_AUTOSCROLL_SPEED,
		)

		const val TRACK_HISTORY = "history"
		const val TRACK_FAVOURITES = "favourites"

		const val KEY_LIST_MODE = "list_mode_2"
		const val KEY_LIST_CHECKPOINT = "list_checkpoint"
		const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
		const val KEY_FAVOURITES_QUICK_ADD = "fav_quick_add"
		const val KEY_DEFAULT_FAVOURITE_CATEGORY = "default_favourite_category"
		const val TIP_QUICK_ADD = "quick_add"
		const val KEY_LIST_MODE_HISTORY = "list_mode_history"
		const val KEY_LIST_MODE_FAVORITES = "list_mode_favorites"
		const val KEY_LIST_MODE_SUGGESTIONS = "list_mode_suggestions"
		const val KEY_THEME = "theme"
		const val KEY_COLOR_THEME = "color_theme"
		const val KEY_THEME_AMOLED = "amoled_theme"
		const val KEY_APP_ICON = "app_icon"
		const val KEY_PROTECT_APP_TIMEOUT = "protect_app_timeout"
		const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
		const val KEY_SOURCES_GRID_COLUMNS = "sources_grid_columns"
		const val KEY_FEED_SWIPE_GESTURES = "feed_swipe_gestures"
		const val KEY_TRACKER_FAILURE_NOTIFICATION = "tracker_notify_fail"
		const val KEY_TRACKER_SMART_UPDATE = "tracker_smart_update"
		const val KEY_ADBLOCK = "adblock"
		const val KEY_READER_DOUBLE_TAP_FILL = "reader_double_tap_fill"
		const val KEY_READER_UPSCALE = "reader_upscale"
		const val KEY_READER_ROTATE_WIDE = "reader_rotate_wide"
		const val KEY_READER_ROTATE_WIDE_INVERT = "reader_rotate_wide_invert"
		const val KEY_READER_WIDE_SPLIT = "reader_wide_split"
		const val KEY_READER_WIDE_SPLIT_MANUAL = "reader_wide_split_manual"
		const val KEY_CHAPTER_JUMP_DIALOG = "chapter_jump_dialog"
		const val KEY_CHECK_DUPLICATES = "check_duplicates"
		const val KEY_SCROBBLING_PROGRESS_SYNC = "scrobbler_progress_sync"
		const val KEY_EYE_REMINDER = "eye_reminder"
		const val KEY_EYE_REMINDER_INTERVAL = "eye_reminder_interval"
		const val EYE_REMINDER_DEFAULT_INTERVAL = 1200
		const val KEY_OFFLINE_DISABLED = "no_offline"
		const val KEY_PAGES_CACHE_CLEAR = "pages_cache_clear"
		const val KEY_HTTP_CACHE_CLEAR = "http_cache_clear"
		const val KEY_COOKIES_CLEAR = "cookies_clear"
		const val KEY_CHAPTERS_CLEAR = "chapters_clear"
		const val KEY_CHAPTERS_CLEAR_AUTO = "chapters_clear_auto"
		const val KEY_THUMBS_CACHE_CLEAR = "thumbs_cache_clear"
		const val KEY_SEARCH_HISTORY_CLEAR = "search_history_clear"
		const val KEY_UPDATES_FEED_CLEAR = "updates_feed_clear"
		const val KEY_GRID_SIZE = "grid_size"
		const val KEY_GRID_SIZE_PAGES = "grid_size_pages"
		const val KEY_GRID_SIZE_DISCOVER = "grid_size_discover"
		const val KEY_REMOTE_SOURCES = "remote_sources"
		const val KEY_LOCAL_STORAGE = "local_storage"
		const val KEY_READER_DOUBLE_PAGES = "reader_double_pages"
		const val KEY_READER_DOUBLE_PAGES_SENSITIVITY = "reader_double_pages_sensitivity_2"
		const val KEY_READER_DOUBLE_FOLDABLE = "reader_double_foldable"
		const val KEY_READER_DOUBLE_COVER_PAGE = "reader_double_cover_page"
		const val KEY_READER_ZOOM_BUTTONS = "reader_zoom_buttons"
		const val KEY_READER_CONTROL_LTR = "reader_taps_ltr"
		const val KEY_READER_NAVIGATION_INVERTED = "reader_navigation_inverted"
		const val KEY_READER_FULLSCREEN = "reader_fullscreen"
		const val KEY_READER_VOLUME_BUTTONS = "reader_volume_buttons"
		const val KEY_READER_ORIENTATION = "reader_orientation"
		const val KEY_TRACKER_ENABLED = "tracker_enabled"
		const val KEY_TRACKER_WIFI_ONLY = "tracker_wifi"
		const val KEY_TRACKER_FREQUENCY = "tracker_freq"
		const val KEY_SYNC_PERIOD = "sync_period"
		const val KEY_TRACK_SOURCES = "track_sources"
		const val KEY_TRACK_CATEGORIES = "track_categories"
		const val KEY_TRACK_WARNING = "track_warning"
		const val KEY_TRACKER_NOTIFICATIONS = "tracker_notifications"
		const val KEY_TRACKER_NO_NSFW = "tracker_no_nsfw"
		const val KEY_TRACKER_DOWNLOAD = "tracker_download"
		const val KEY_TRACKER_UNSTUCK_MIGRATION_V4 = "tracker_unstuck_migration_v4"
		const val KEY_TRACKER_PROGRESS_REFRESH_V1 = "tracker_progress_refresh_v1"
		const val KEY_NOTIFICATIONS_SETTINGS = "notifications_settings"
		const val KEY_NOTIFICATIONS_SOUND = "notifications_sound"
		const val KEY_NOTIFICATIONS_VIBRATE = "notifications_vibrate"
		const val KEY_NOTIFICATIONS_LIGHT = "notifications_light"
		const val KEY_NOTIFICATIONS_INFO = "tracker_notifications_info"
		const val KEY_READER_ANIMATION = "reader_animation2"
		const val KEY_READER_CONTROLS = "reader_controls"
		const val KEY_READER_CONTROLS_ORDERED = "reader_controls_ordered"
		const val KEY_READER_MODE = "reader_mode"
		const val KEY_READER_MODE_DETECT = "reader_mode_detect"
		const val KEY_READER_CROP = "reader_crop"
		const val KEY_APP_PASSWORD = "app_password"
		const val KEY_APP_PASSWORD_NUMERIC = "app_password_num"
		const val KEY_PROTECT_APP = "protect_app"
		const val KEY_PROTECT_APP_BIOMETRIC = "protect_app_bio"
		const val KEY_ZOOM_MODE = "zoom_mode"
		const val KEY_BACKUP = "backup"
		const val KEY_RESTORE = "restore"
		const val KEY_BACKUP_PERIODICAL_ENABLED = "backup_periodic"
		const val KEY_BACKUP_PERIODICAL_FREQUENCY = "backup_periodic_freq"
		const val KEY_BACKUP_PERIODICAL_TRIM = "backup_periodic_trim"
		const val KEY_BACKUP_PERIODICAL_COUNT = "backup_periodic_count"
		const val KEY_BACKUP_PERIODICAL_OUTPUT = "backup_periodic_output"
		const val KEY_BACKUP_PERIODICAL_LAST = "backup_periodic_last"
		const val KEY_HISTORY_GROUPING = "history_grouping"
		const val KEY_UPDATED_GROUPING = "updated_grouping"
		const val KEY_PROGRESS_INDICATORS = "progress_indicators"
		const val KEY_REVERSE_CHAPTERS = "reverse_chapters"
		const val KEY_GRID_VIEW_CHAPTERS = "grid_view_chapters"
		const val KEY_INCOGNITO_NSFW = "incognito_nsfw"
		const val KEY_PAGES_NUMBERS = "pages_numbers"
		const val KEY_SCREENSHOTS_POLICY = "screenshots_policy"
		const val KEY_PAGES_PRELOAD = "pages_preload"
		const val KEY_SUGGESTIONS = "suggestions"
		const val KEY_SUGGESTIONS_WIFI_ONLY = "suggestions_wifi"
		const val KEY_SUGGESTIONS_EXCLUDE_NSFW = "suggestions_exclude_nsfw"
		const val KEY_SUGGESTIONS_EXCLUDE_TAGS = "suggestions_exclude_tags"
		const val KEY_SUGGESTIONS_DISABLED_SOURCES = "suggestions_disabled_sources"
		const val KEY_SUGGESTIONS_NOTIFICATIONS = "suggestions_notifications"
		const val KEY_SHIKIMORI = "shikimori"
		const val KEY_ANILIST = "anilist"
		const val KEY_MAL = "mal"
		const val KEY_KITSU = "kitsu"
		const val KEY_DOWNLOADS_METERED_NETWORK = "downloads_metered_network"
		const val KEY_DOWNLOADS_FORMAT = "downloads_format"
		const val KEY_ALL_FAVOURITES_VISIBLE = "all_favourites_visible"
		const val KEY_DOH = "doh"
		const val KEY_EXIT_CONFIRM = "exit_confirm"
		const val KEY_INCOGNITO_MODE = "incognito"
		const val KEY_VERBOSE_LOGGING = "verbose_logging"
		const val KEY_READER_MULTITASK = "reader_multitask"
		const val KEY_READER_BAR = "reader_bar"
		const val KEY_READER_BAR_TRANSPARENT = "reader_bar_transparent"
		const val KEY_READER_CHAPTER_TOAST = "reader_chapter_toast"
		const val KEY_READER_BACKGROUND = "reader_background"
		const val KEY_READER_SCREEN_ON = "reader_screen_on"
		const val KEY_SHORTCUTS = "dynamic_shortcuts"
		const val KEY_SHORTCUTS_NO_NSFW = "shortcuts_no_nsfw"
		const val KEY_READER_TAP_ACTIONS = "reader_tap_actions"
		const val KEY_READER_OPTIMIZE = "reader_optimize"
		const val KEY_EINK_FLASH = "eink_flash"
		const val KEY_EINK_FLASH_DURATION = "eink_flash_duration"
		const val KEY_EINK_FLASH_EVERY = "eink_flash_every"
		const val KEY_EINK_FLASH_COLOR = "eink_flash_color"
		const val KEY_LOCAL_LIST_ORDER = "local_order"
		const val KEY_HISTORY_ORDER = "history_order"
		const val KEY_FAVORITES_ORDER = "fav_order"
		const val KEY_WEBTOON_GAPS = "webtoon_gaps"
		const val KEY_WEBTOON_ZOOM = "webtoon_zoom"
		const val KEY_WEBTOON_ZOOM_OUT = "webtoon_zoom_out"
		const val KEY_WEBTOON_PULL_GESTURE = "webtoon_pull_gesture"
		const val KEY_READER_ERASE_GESTURE = "reader_erase_gesture"
		const val KEY_ERASE_TUTORIAL = "erase_tutorial"
		const val TIP_ERASE_TUTORIAL = "erase_tutorial"
		const val TIP_DISCOVER_DISMISS = "discover_dismiss"
		private const val KEY_NAV_SOURCES_OFFERED = "nav_sources_offered"
		private const val NAV_ITEMS_ALWAYS_FIT = 5
		const val KEY_PREFETCH_CONTENT = "prefetch_content"
		const val KEY_APP_LOCALE = "app_locale"
		const val KEY_SOURCES_GRID = "sources_grid"
		const val KEY_UPDATES_UNSTABLE = "updates_unstable"
		const val KEY_TIPS_CLOSED = "tips_closed"
		const val KEY_SSL_BYPASS = "ssl_bypass"
		const val KEY_READER_AUTOSCROLL_SPEED = "as_speed"
		const val KEY_READER_AUTOSCROLL_FAB = "as_fab"
		const val KEY_MIRROR_SWITCHING = "mirror_switching"
		const val KEY_PROXY = "proxy"
		const val KEY_PROXY_TYPE = "proxy_type_2"
		const val KEY_PROXY_ADDRESS = "proxy_address"
		const val KEY_PROXY_PORT = "proxy_port"
		const val KEY_PROXY_AUTH = "proxy_auth"
		const val KEY_PROXY_LOGIN = "proxy_login"
		const val KEY_PROXY_PASSWORD = "proxy_password"
		const val KEY_IMAGES_PROXY = "images_proxy_2"
		const val KEY_LOCAL_MANGA_DIRS = "local_manga_dirs"
		const val KEY_DISABLE_NSFW = "no_nsfw"
		const val KEY_RELATED_MANGA = "related_manga"
		const val KEY_NAV_MAIN = "nav_main"
		const val KEY_NAV_LABELS = "nav_labels"
		const val KEY_NAV_PINNED = "nav_pinned"
		const val KEY_NAV_LEGACY = "nav_legacy"
		const val KEY_FLOATING_NAV = "floating_nav"
		const val KEY_BAR_BLUR = "bar_blur"
		const val KEY_MAIN_FAB = "main_fab"
		const val KEY_UPDATE_PROMPT_DISMISSED = "update_prompt_dismissed"
		const val KEY_UI_SCALE = "ui_scale"
		const val KEY_TITLE_OVER_COVER = "title_over_cover"
		const val KEY_GRID_SPACING_INCREASED = "grid_spacing_increased"
		const val KEY_TITLE_TAP_TO_READ = "title_tap_to_read"
		const val KEY_FEED_COUNTER_DOT = "feed_counter_dot"
		const val KEY_32BIT_COLOR = "enhanced_colors"
		const val KEY_SOURCES_ORDER = "sources_sort_order"
		const val KEY_SOURCES_CATALOG = "sources_catalog"
		const val KEY_CF_BRIGHTNESS = "cf_brightness"
		const val KEY_CF_CONTRAST = "cf_contrast"
		const val KEY_CF_INVERTED = "cf_inverted"
		const val KEY_CF_GRAYSCALE = "cf_grayscale"
		const val KEY_CF_BOOK = "cf_book"
		const val KEY_PAGES_TAB = "pages_tab"
		const val KEY_DETAILS_TAB = "details_tab"
		const val KEY_DETAILS_LAST_TAB = "details_last_tab"
		const val KEY_DETAILS_UI = "details_ui"
		const val KEY_DETAILS_BACKDROP = "details_backdrop"
		const val KEY_DETAILS_BACKDROP_BLUR_AMOUNT = "details_backdrop_blur_amount"
		const val KEY_READING_TIME = "reading_time"
		const val KEY_PAGES_SAVE_DIR = "pages_dir"
		const val KEY_PAGES_SAVE_ASK = "pages_dir_ask"
		const val KEY_STATS_ENABLED = "stats_on"
		const val KEY_FEED_HEADER = "feed_header"
		const val KEY_SEARCH_SUGGESTION_TYPES = "search_suggest_types"
		const val KEY_SOURCES_VERSION = "sources_version"
		const val KEY_SOURCES_ENABLED_ALL = "sources_enabled_all"
		const val KEY_SOURCES_NEW_DOWNLOADED = "sources_new_downloaded"
		const val KEY_PARSERS_AUTO_UPDATE = "parsers_auto_update"
		const val KEY_QUICK_FILTER = "quick_filter"
		const val KEY_COLLAPSE_DESCRIPTION = "description_collapse"
		const val KEY_BACKUP_TG_ENABLED = "backup_periodic_tg_enabled"
		const val KEY_BACKUP_TG_CHAT = "backup_periodic_tg_chat_id"
		const val KEY_MANGA_LIST_BADGES = "manga_list_badges"
		const val KEY_DISCORD_RPC = "discord_rpc"
		const val KEY_DISCORD_RPC_SKIP_NSFW = "discord_rpc_skip_nsfw"
		const val KEY_DISCORD_TOKEN = "discord_token"
		const val KEY_ACTIVE_SOURCE_PRESET = "active_source_preset"

		// keys for non-persistent preferences
		const val KEY_APP_VERSION = "app_version"
		const val KEY_IGNORE_DOZE = "ignore_dose"
		const val KEY_TRACKER_DEBUG = "tracker_debug"
		const val KEY_LINK_WEBLATE = "about_app_translation"
		const val KEY_LINK_DISCORD = "about_discord"
		const val KEY_LINK_GITHUB = "about_github"
		const val KEY_LINK_MANUAL = "about_help"
		const val KEY_PROXY_TEST = "proxy_test"
		const val KEY_OPEN_BROWSER = "open_browser"
		const val KEY_HANDLE_LINKS = "handle_links"
		const val KEY_BACKUP_TG = "backup_periodic_tg"
		const val KEY_BACKUP_TG_OPEN = "backup_periodic_tg_open"
		const val KEY_BACKUP_TG_TEST = "backup_periodic_tg_test"
		const val KEY_CLEAR_MANGA_DATA = "manga_data_clear"
		const val KEY_STORAGE_USAGE = "storage_usage"
		const val KEY_WEBVIEW_CLEAR = "webview_clear"
		const val KEY_FAVORITES_PINNED = "fav_pinned_order_"

		val SENSITIVE_BACKUP_KEYS = setOf(
			KEY_APP_PASSWORD,
			KEY_APP_PASSWORD_NUMERIC,
			KEY_PROTECT_APP,
			KEY_PROTECT_APP_BIOMETRIC,
			KEY_PROXY_PASSWORD,
			KEY_PROXY_LOGIN,
			KEY_INCOGNITO_MODE,
		)

		private val SYNC_LOCAL_KEYS = setOf(
			KEY_LOCAL_STORAGE,
			KEY_BACKUP_PERIODICAL_ENABLED,
			KEY_BACKUP_PERIODICAL_FREQUENCY,
			KEY_BACKUP_PERIODICAL_TRIM,
			KEY_BACKUP_PERIODICAL_COUNT,
			KEY_BACKUP_TG,
			KEY_DEFAULT_FAVOURITE_CATEGORY,
			KEY_FAVOURITES_QUICK_ADD,
		)

		fun isSyncableKey(key: String): Boolean = key !in SENSITIVE_BACKUP_KEYS &&
			key !in SYNC_LOCAL_KEYS

		/** Sync period options offered in settings, in hours. `0` means "only when local data changes". */
		@JvmField
		val SYNC_PERIODS = intArrayOf(0, 1, 6, 12, 24)
		const val SYNC_PERIOD_DEFAULT = 6

		// old keys are for migration only
		private const val KEY_IMAGES_PROXY_OLD = "images_proxy"

		// values
		private const val READER_CROP_PAGED = 1
		private const val READER_CROP_WEBTOON = 2
	}
}
