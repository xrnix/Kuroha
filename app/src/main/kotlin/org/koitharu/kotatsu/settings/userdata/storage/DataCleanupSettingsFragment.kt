package org.koitharu.kotatsu.settings.userdata.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.FileSize
import org.koitharu.kotatsu.core.util.ext.getQuantityStringSafe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.local.data.CacheDir
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.ConfirmDialog
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold

@AndroidEntryPoint
class DataCleanupSettingsFragment : BaseComposeSettingsFragment(R.string.data_removal) {

	private val viewModel by viewModels<DataCleanupSettingsViewModel>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				val thumbsSize by checkNotNull(viewModel.cacheSizes[CacheDir.THUMBS]).collectAsState()
				val pagesSize by checkNotNull(viewModel.cacheSizes[CacheDir.PAGES]).collectAsState()
				val httpSize by viewModel.httpCacheSize.collectAsState()
				val searchCount by viewModel.searchHistoryCount.collectAsState()
				val feedCount by viewModel.feedItemsCount.collectAsState()
				val loadingKeys by viewModel.loadingKeys.collectAsState()

				DataCleanupScreen(
					thumbsSize = thumbsSize,
					pagesSize = pagesSize,
					httpSize = httpSize,
					searchCount = searchCount,
					feedCount = feedCount,
					loadingKeys = loadingKeys,
					isBrowserDataCleanupEnabled = viewModel.isBrowserDataCleanupEnabled,
					onClearSearchHistory = viewModel::clearSearchHistory,
					onClearUpdatesFeed = viewModel::clearUpdatesFeed,
					onClearThumbs = { viewModel.clearCache(AppSettings.KEY_THUMBS_CACHE_CLEAR, CacheDir.THUMBS, CacheDir.FAVICONS) },
					onClearPages = { viewModel.clearCache(AppSettings.KEY_PAGES_CACHE_CLEAR, CacheDir.PAGES) },
					onClearHttp = viewModel::clearHttpCache,
					onClearMangaData = viewModel::clearMangaData,
					onClearCookies = viewModel::clearCookies,
					onClearBrowserData = viewModel::clearBrowserData,
					onCleanupChapters = viewModel::cleanupChapters,
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(view, this))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(view))
		viewModel.onChaptersCleanedUp.observeEvent(viewLifecycleOwner, ::onChaptersCleanedUp)
	}

	private fun onChaptersCleanedUp(result: Pair<Int, Long>) {
		val c = context ?: return
		val v = view ?: return
		val text = if (result.first == 0 && result.second == 0L) {
			c.getString(R.string.no_chapters_deleted)
		} else {
			c.getString(
				R.string.chapters_deleted_pattern,
				c.resources.getQuantityStringSafe(R.plurals.chapters, result.first, result.first),
				FileSize.BYTES.format(c, result.second),
			)
		}
		Snackbar.make(v, text, Snackbar.LENGTH_SHORT).show()
	}
}

private enum class CleanupConfirmAction {
	SEARCH_HISTORY,
	COOKIES,
	CHAPTERS,
}

@Composable
private fun DataCleanupScreen(
	thumbsSize: Long,
	pagesSize: Long,
	httpSize: Long,
	searchCount: Int,
	feedCount: Int,
	loadingKeys: Set<String>,
	isBrowserDataCleanupEnabled: Boolean,
	onClearSearchHistory: () -> Unit,
	onClearUpdatesFeed: () -> Unit,
	onClearThumbs: () -> Unit,
	onClearPages: () -> Unit,
	onClearHttp: () -> Unit,
	onClearMangaData: () -> Unit,
	onClearCookies: () -> Unit,
	onClearBrowserData: () -> Unit,
	onCleanupChapters: () -> Unit,
) {
	val ctx = LocalContext.current
	var pendingConfirm by remember { mutableStateOf<CleanupConfirmAction?>(null) }

	val computingLabel = stringResource(R.string.computing_)
	val loadingLabel = stringResource(R.string.loading_)

	fun formatSize(bytes: Long, fallback: String): String =
		if (bytes < 0) fallback else FileSize.BYTES.format(ctx, bytes)

	fun formatCount(count: Int): String =
		if (count < 0) loadingLabel else ctx.resources.getQuantityStringSafe(R.plurals.items, count, count)

	when (pendingConfirm) {
		CleanupConfirmAction.SEARCH_HISTORY -> ConfirmDialog(
			title = stringResource(R.string.clear_search_history),
			message = stringResource(R.string.text_clear_search_history_prompt),
			confirmLabel = stringResource(R.string.clear),
			destructive = true,
			icon = R.drawable.ic_history,
			onConfirm = onClearSearchHistory,
			onDismiss = { pendingConfirm = null },
		)

		CleanupConfirmAction.COOKIES -> ConfirmDialog(
			title = stringResource(R.string.clear_cookies),
			message = stringResource(R.string.text_clear_cookies_prompt),
			confirmLabel = stringResource(R.string.clear),
			destructive = true,
			icon = R.drawable.ic_data_privacy,
			onConfirm = onClearCookies,
			onDismiss = { pendingConfirm = null },
		)

		CleanupConfirmAction.CHAPTERS -> ConfirmDialog(
			title = stringResource(R.string.delete_read_chapters),
			message = stringResource(R.string.delete_read_chapters_prompt),
			confirmLabel = stringResource(R.string.delete),
			destructive = true,
			icon = R.drawable.ic_delete,
			onConfirm = onCleanupChapters,
			onDismiss = { pendingConfirm = null },
		)

		null -> Unit
	}

	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.history)) {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_search_history),
						subtitle = formatCount(searchCount),
						icon = R.drawable.ic_history,
						enabled = AppSettings.KEY_SEARCH_HISTORY_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = { pendingConfirm = CleanupConfirmAction.SEARCH_HISTORY },
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_updates_feed),
						subtitle = formatCount(feedCount),
						icon = R.drawable.ic_feed,
						enabled = AppSettings.KEY_UPDATES_FEED_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = onClearUpdatesFeed,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = "Cache") {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_thumbs_cache),
						subtitle = formatSize(thumbsSize, computingLabel),
						icon = R.drawable.ic_images,
						enabled = AppSettings.KEY_THUMBS_CACHE_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = onClearThumbs,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_pages_cache),
						subtitle = formatSize(pagesSize, computingLabel),
						icon = R.drawable.ic_book_page,
						enabled = AppSettings.KEY_PAGES_CACHE_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = onClearPages,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_network_cache),
						subtitle = formatSize(httpSize, loadingLabel),
						icon = R.drawable.ic_web,
						enabled = AppSettings.KEY_HTTP_CACHE_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = onClearHttp,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.storage_and_network)) {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_database),
						subtitle = stringResource(R.string.clear_database_summary),
						icon = R.drawable.ic_storage,
						enabled = AppSettings.KEY_CLEAR_MANGA_DATA !in loadingKeys,
						shape = pos.shape,
						onClick = onClearMangaData,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.clear_cookies),
						subtitle = stringResource(R.string.clear_cookies_summary),
						icon = R.drawable.ic_data_privacy,
						enabled = AppSettings.KEY_COOKIES_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = { pendingConfirm = CleanupConfirmAction.COOKIES },
					)
				}
				if (isBrowserDataCleanupEnabled) {
					item { pos ->
						ActionSettingsItem(
							title = stringResource(R.string.clear_browser_data),
							subtitle = stringResource(R.string.clear_browser_data_summary),
							icon = R.drawable.ic_web,
							enabled = AppSettings.KEY_WEBVIEW_CLEAR !in loadingKeys,
							shape = pos.shape,
							onClick = onClearBrowserData,
						)
					}
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.delete_read_chapters),
						subtitle = stringResource(R.string.delete_read_chapters_summary),
						icon = R.drawable.ic_delete,
						enabled = AppSettings.KEY_CHAPTERS_CLEAR !in loadingKeys,
						shape = pos.shape,
						onClick = { pendingConfirm = CleanupConfirmAction.CHAPTERS },
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
