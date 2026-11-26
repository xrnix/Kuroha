package org.koitharu.kotatsu.settings.sources

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.core.util.ext.getQuantityStringSafe
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.extensions.install.ShizukuExtensionInstaller
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.util.names
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.ListSettingsItem
import org.koitharu.kotatsu.settings.compose.MultiChoiceDialog
import org.koitharu.kotatsu.settings.compose.NavigationSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import org.koitharu.kotatsu.settings.sources.manage.SourcesManageFragment
import rikka.shizuku.Shizuku
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SourcesSettingsFragment : BaseComposeSettingsFragment(R.string.remote_sources) {

	private val viewModel by viewModels<SourcesSettingsViewModel>()

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var shizukuInstaller: ShizukuExtensionInstaller

	private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
		override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
			if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) return
			runCatching { Shizuku.removeRequestPermissionResultListener(this) }
			if (grantResult == PackageManager.PERMISSION_GRANTED) {
				settings.isShizukuInstallerEnabled = true
			} else {
				settings.isShizukuInstallerEnabled = false
				Toast.makeText(requireContext(), R.string.shizuku_permission_denied, Toast.LENGTH_LONG).show()
			}
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				val enabledCount by viewModel.enabledSourcesCount.collectAsState()
				val availableCount by viewModel.availableSourcesCount.collectAsState()
				val isLinksEnabled by viewModel.isLinksEnabled.collectAsState()
				val selectedTypes by viewModel.selectedContentTypes.collectAsState()
				val selectedLocales by viewModel.selectedLocales.collectAsState()

				SourcesScreen(
					onOpenManage = {
						(activity as? SettingsActivity)?.openFragment(
							SourcesManageFragment::class.java,
							null,
							isFromRoot = false,
						)
					},
					onOpenCatalog = { router.openSourcesCatalog() },
					onOpenStores = { router.openExtensionStores() },
					onShizukuChanged = ::setShizukuEnabled,
					enabledCount = enabledCount,
					availableCount = availableCount,
					isLinksEnabled = isLinksEnabled,
					onSetLinksEnabled = viewModel::setLinksEnabled,
					isParsersUpdateSupported = viewModel.isParsersUpdateSupported,
					availableTypes = viewModel.availableContentTypes,
					selectedTypes = selectedTypes,
					availableLocales = viewModel.availableLocales,
					selectedLocales = selectedLocales,
					onApplyFilters = viewModel::applySourceFilters,
				)
			}
		}
	}

	override fun onDestroy() {
		runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
		super.onDestroy()
	}

	override fun onResume() {
		super.onResume()
		if (!settings.isShizukuInstallerEnabled) return
		if (!shizukuInstaller.isInstalled) {
			settings.isShizukuInstallerEnabled = false
			return
		}
		val binderReady = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
		if (
			binderReady &&
			runCatching {
				Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
			}.getOrDefault(true)
		) {
			settings.isShizukuInstallerEnabled = false
		}
	}

	private fun setShizukuEnabled(enabled: Boolean) {
		if (!enabled) {
			settings.isShizukuInstallerEnabled = false
			return
		}
		when {
			!shizukuInstaller.isInstalled -> {
				Toast.makeText(requireContext(), R.string.shizuku_not_installed, Toast.LENGTH_LONG).show()
			}
			!runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> {
				Toast.makeText(requireContext(), R.string.shizuku_not_running, Toast.LENGTH_LONG).show()
			}
			runCatching {
				Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
			}.getOrDefault(false) -> {
				settings.isShizukuInstallerEnabled = true
			}
			else -> runCatching {
				Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
				Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
			}.onFailure {
				Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
				Toast.makeText(requireContext(), R.string.shizuku_permission_denied, Toast.LENGTH_LONG).show()
			}
		}
	}

	private companion object {
		const val SHIZUKU_PERMISSION_REQUEST_CODE = 14045
	}
}

@Composable
private fun SourcesScreen(
	onOpenManage: () -> Unit,
	onOpenCatalog: () -> Unit,
	onOpenStores: () -> Unit,
	onShizukuChanged: (Boolean) -> Unit,
	enabledCount: Int,
	availableCount: Int,
	isLinksEnabled: Boolean,
	onSetLinksEnabled: (Boolean) -> Unit,
	isParsersUpdateSupported: Boolean,
	availableTypes: List<ContentType>,
	selectedTypes: Set<ContentType>,
	availableLocales: List<Locale>,
	selectedLocales: Set<Locale>,
	onApplyFilters: (Set<ContentType>, Set<Locale>) -> Unit,
) {
	val ctx = LocalContext.current
	val sortEntries = remember {
		SourcesSortOrder.entries.map { ctx.getString(it.titleResId) }
	}
	val sortValues = remember { SourcesSortOrder.entries.names().toList() }
	var sortOrder by rememberStringPref(AppSettings.KEY_SOURCES_ORDER, SourcesSortOrder.ALPHABETIC.name)
	var sourcesGrid by rememberBooleanPref(AppSettings.KEY_SOURCES_GRID, true)
	var enableAllSources by rememberBooleanPref(AppSettings.KEY_SOURCES_ENABLED_ALL, false)
	var mirrorSwitching by rememberBooleanPref(AppSettings.KEY_MIRROR_SWITCHING, false)
	var autoUpdateParsers by rememberBooleanPref(AppSettings.KEY_PARSERS_AUTO_UPDATE, true)

	var privateEnabled by rememberBooleanPref(AppSettings.KEY_PRIVATE_INSTALLER, false)
	var shizukuEnabled by rememberBooleanPref(AppSettings.KEY_SHIZUKU_INSTALLER, false)
	var autoUpdateExtensions by rememberBooleanPref(AppSettings.KEY_AUTO_UPDATE_EXTENSIONS, false)
	var updateNotifications by rememberBooleanPref(AppSettings.KEY_EXTENSION_UPDATE_NOTIFICATIONS, true)

	var showTypesDialog by remember { mutableStateOf(false) }
	var showLocalesDialog by remember { mutableStateOf(false) }

	val manageSubtitle = if (enabledCount >= 0) {
		ctx.resources.getQuantityStringSafe(R.plurals.items, enabledCount, enabledCount)
	} else {
		null
	}
	val catalogSubtitle = if (availableCount >= 0) {
		ctx.resources.getQuantityStringSafe(R.plurals.items, availableCount, availableCount)
	} else {
		stringResource(R.string.sources_catalog)
	}

	val typesSummary = if (selectedTypes.isEmpty()) {
		stringResource(R.string.none)
	} else {
		availableTypes.filter { it in selectedTypes }.joinToString(", ") { ctx.getString(it.titleResId) }
	}

	val localesSummary = when {
		selectedLocales.isEmpty() -> stringResource(R.string.none)
		selectedLocales.size == availableLocales.size -> stringResource(R.string.all_languages)
		else -> availableLocales.filter { it in selectedLocales }.joinToString(", ") { it.getDisplayName(ctx) }
	}

	if (showTypesDialog) {
		val typeEntries = availableTypes.map { ctx.getString(it.titleResId) }
		val selectedIndices = availableTypes.indices.filter { availableTypes[it] in selectedTypes }.toSet()
		MultiChoiceDialog(
			title = stringResource(R.string.categories),
			entries = typeEntries,
			selectedIndices = selectedIndices,
			onConfirm = { indices ->
				val nextTypes = indices.mapTo(HashSet()) { availableTypes[it] }
				val nextLocales = selectedLocales.ifEmpty { setOf(Locale.ROOT) }
				onApplyFilters(nextTypes, nextLocales)
			},
			onDismiss = { showTypesDialog = false },
			icon = R.drawable.ic_tag,
		)
	}

	if (showLocalesDialog) {
		val localeEntries = availableLocales.map { it.getDisplayName(ctx) }
		val selectedIndices = availableLocales.indices.filter { availableLocales[it] in selectedLocales }.toSet()
		MultiChoiceDialog(
			title = stringResource(R.string.languages),
			entries = localeEntries,
			selectedIndices = selectedIndices,
			onConfirm = { indices ->
				val nextLocales = indices.mapTo(HashSet()) { availableLocales[it] }
				val nextTypes = selectedTypes.ifEmpty { setOf(ContentType.MANGA) }
				onApplyFilters(nextTypes, nextLocales)
			},
			onDismiss = { showLocalesDialog = false },
			icon = R.drawable.ic_language,
		)
	}

	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.catalog)) {
				item { pos ->
					NavigationSettingsItem(
						title = stringResource(R.string.manage_sources),
						subtitle = manageSubtitle,
						icon = R.drawable.ic_manga_source,
						shape = pos.shape,
						onClick = onOpenManage,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.sources_catalog),
						subtitle = catalogSubtitle,
						icon = R.drawable.ic_list,
						shape = pos.shape,
						onClick = onOpenCatalog,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.extension_stores),
						subtitle = stringResource(R.string.manage_stores_summary),
						icon = R.drawable.ic_storefront,
						shape = pos.shape,
						onClick = onOpenStores,
					)
				}
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.sort_order),
						entries = sortEntries,
						entryValues = sortValues,
						selectedValue = sortOrder,
						onValueChange = { sortOrder = it },
						icon = R.drawable.ic_sort_asc,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.show_in_grid_view),
						checked = sourcesGrid,
						onCheckedChange = { sourcesGrid = it },
						icon = R.drawable.ic_grid,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.extensions)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.private_extensions),
						subtitle = stringResource(R.string.private_extensions_summary),
						checked = privateEnabled,
						onCheckedChange = {
							privateEnabled = it
							if (it) shizukuEnabled = false
						},
						icon = R.drawable.ic_shield,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.shizuku_title),
						subtitle = stringResource(R.string.shizuku_summary),
						checked = shizukuEnabled,
						onCheckedChange = onShizukuChanged,
						icon = R.drawable.ic_auth_key_large,
						shape = pos.shape,
						enabled = !privateEnabled,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.ext_auto_update_title),
						subtitle = stringResource(R.string.ext_auto_update_summary),
						checked = if (privateEnabled) true else autoUpdateExtensions,
						onCheckedChange = { autoUpdateExtensions = it },
						icon = R.drawable.ic_updated,
						shape = pos.shape,
						enabled = !privateEnabled && shizukuEnabled,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.ext_update_notifications_title),
						subtitle = stringResource(R.string.ext_update_notifications_summary),
						checked = updateNotifications,
						onCheckedChange = { updateNotifications = it },
						icon = R.drawable.ic_updated,
						shape = pos.shape,
						enabled = !privateEnabled,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.filter)) {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.categories),
						subtitle = typesSummary,
						icon = R.drawable.ic_tag,
						shape = pos.shape,
						onClick = { showTypesDialog = true },
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.languages),
						subtitle = localesSummary,
						icon = R.drawable.ic_language,
						shape = pos.shape,
						onClick = { showLocalesDialog = true },
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.remote_sources)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.enable_all_sources),
						subtitle = stringResource(R.string.enable_all_sources_summary),
						checked = enableAllSources,
						onCheckedChange = { enableAllSources = it },
						icon = R.drawable.ic_check,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.mirror_switching),
						subtitle = stringResource(R.string.mirror_switching_summary),
						checked = mirrorSwitching,
						onCheckedChange = { mirrorSwitching = it },
						icon = R.drawable.ic_sync_alt,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.handle_links),
						subtitle = stringResource(R.string.handle_links_summary),
						checked = isLinksEnabled,
						onCheckedChange = onSetLinksEnabled,
						icon = R.drawable.ic_open_external,
						shape = pos.shape,
					)
				}
				if (isParsersUpdateSupported) {
					item { pos ->
						SwitchSettingsItem(
							title = stringResource(R.string.sources_auto_update),
							subtitle = stringResource(R.string.sources_auto_update_summary),
							checked = autoUpdateParsers,
							onCheckedChange = { autoUpdateParsers = it },
							icon = R.drawable.ic_app_update,
							shape = pos.shape,
						)
					}
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
