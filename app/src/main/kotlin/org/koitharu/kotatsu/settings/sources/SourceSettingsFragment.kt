package org.koitharu.kotatsu.settings.sources

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.ExceptionResolver
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.parser.EmptyMangaRepository
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.SourceSettings
import org.koitharu.kotatsu.core.ui.BaseActivityEntryPoint
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.withArgs
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.ListSettingsItem
import org.koitharu.kotatsu.settings.compose.LocalSettingsItemColor
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.TextInputDialog
import org.koitharu.kotatsu.settings.utils.validation.DomainValidator
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SourceSettingsFragment : BaseComposeSettingsFragment(0) {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel: SourceSettingsViewModel by viewModels()
	private lateinit var exceptionResolver: ExceptionResolver

	override fun onAttach(context: Context) {
		super.onAttach(context)
		val entryPoint = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(context)
		exceptionResolver = entryPoint.exceptionResolverFactory.create(this)
	}

	override fun onResume() {
		super.onResume()
		context?.let { ctx ->
			val title = viewModel.source.getTitle(ctx)
			(activity as? SettingsActivity)?.setSectionTitle(title) ?: run {
				activity?.title = title
			}
		}
		viewModel.onResume()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				val isEnabled by viewModel.isEnabled.collectAsState(initial = false)
				val isAuthorized by viewModel.isAuthorized.collectAsState()
				val username by viewModel.username.collectAsState()
				val browserUrl by viewModel.browserUrl.collectAsState()
				val isLoading by viewModel.isLoading.collectAsState(initial = false)

				SourceSettingsScreen(
					source = viewModel.source,
					isValidSource = viewModel.repository !is EmptyMangaRepository,
					isAllSourcesEnabled = settings.isAllSourcesEnabled,
					isEnabled = isEnabled,
					onSetEnabled = viewModel::setEnabled,
					repository = viewModel.repository as? ParserMangaRepository,
					isAuthorized = isAuthorized,
					username = username,
					browserUrl = browserUrl,
					isLoading = isLoading,
					onSignIn = { router.openSourceAuth(viewModel.source) },
					onClearCookies = viewModel::clearCookies,
					onOpenBrowser = { url, title ->
						router.openBrowser(
							url = url,
							source = viewModel.source,
							title = title,
						)
					},
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.onError.observeEvent(
			viewLifecycleOwner,
			SnackbarErrorObserver(
				view,
				this,
				exceptionResolver,
			) { viewModel.onResume() },
		)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(view))
	}

	companion object {
		fun newInstance(source: MangaSource) = SourceSettingsFragment().withArgs(1) {
			putString(AppRouter.KEY_SOURCE, source.name)
		}
	}
}

@Composable
private fun SourceSettingsScreen(
	source: MangaSource,
	isValidSource: Boolean,
	isAllSourcesEnabled: Boolean,
	isEnabled: Boolean,
	onSetEnabled: (Boolean) -> Unit,
	repository: ParserMangaRepository?,
	isAuthorized: Boolean?,
	username: String?,
	browserUrl: String?,
	isLoading: Boolean,
	onSignIn: () -> Unit,
	onClearCookies: () -> Unit,
	onOpenBrowser: (String, String) -> Unit,
) {
	val ctx = LocalContext.current
	val prefs = remember(source.name) {
		ctx.getSharedPreferences(source.name.replace(File.separatorChar, '$'), Context.MODE_PRIVATE)
	}
	val configKeys = remember(repository) {
		repository?.getConfigKeys().orEmpty()
	}

	val authProvider = remember(repository) { repository?.getAuthProvider() }
	val sourceTitle = remember(source) { source.getTitle(ctx) }

	SettingsScaffold {
		if (isValidSource && !isAllSourcesEnabled) {
			item {
				CompositionLocalProvider(
					LocalSettingsItemColor provides if (isEnabled) {
						MaterialTheme.colorScheme.primaryContainer
					} else {
						MaterialTheme.colorScheme.surfaceContainer
					},
				) {
					SwitchSettingsItem(
						title = stringResource(R.string.enable_source),
						checked = isEnabled,
						onCheckedChange = onSetEnabled,
						icon = R.drawable.ic_check,
						shape = RoundedCornerShape(24.dp),
					)
				}
			}
			item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		}

		if (!isValidSource) {
			item {
				SettingsItem(
					title = stringResource(R.string.unsupported_source),
					icon = R.drawable.ic_alert_outline,
					shape = RoundedCornerShape(24.dp),
				)
			}
			item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		}

		val visibleConfigKeys = configKeys.filter {
			it !is ConfigKey.DisableUpdateChecking && it !is ConfigKey.InterceptCloudflare
		}
		if (visibleConfigKeys.isNotEmpty()) {
			item {
				SettingsGroup(title = stringResource(R.string.connection)) {
					visibleConfigKeys.forEach { key ->
						item { pos ->
							ConfigKeyItem(key = key, prefs = prefs, shape = pos.shape)
						}
					}
				}
			}
			item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		}

		if (repository != null) {
			item {
				SettingsGroup(title = stringResource(R.string.network)) {
					if (authProvider != null) {
						item { pos ->
							SettingsItem(
								title = stringResource(R.string.sign_in),
								subtitle = username?.let { stringResource(R.string.logged_in_as, it) },
								icon = R.drawable.ic_user,
								shape = pos.shape,
								enabled = isAuthorized != true && !isLoading,
								onClick = onSignIn,
							)
						}
					}
					item { pos ->
						SettingsItem(
							title = stringResource(R.string.clear_cookies),
							subtitle = stringResource(R.string.clear_source_cookies_summary),
							icon = R.drawable.ic_data_privacy,
							shape = pos.shape,
							onClick = onClearCookies,
						)
					}
					if (browserUrl != null) {
						item { pos ->
							SettingsItem(
								title = stringResource(R.string.open_in_browser),
								subtitle = browserUrl,
								icon = R.drawable.ic_open_external,
								shape = pos.shape,
								onClick = { onOpenBrowser(browserUrl, sourceTitle) },
							)
						}
					}
				}
			}
			item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		}

		item {
			var noAutoCaptcha by rememberPrefBoolean(prefs, SourceSettings.KEY_NO_AUTO_CAPTCHA, false)
			var noCaptcha by rememberPrefBoolean(prefs, SourceSettings.KEY_NO_CAPTCHA, false)
			var slowdown by rememberPrefBoolean(prefs, SourceSettings.KEY_SLOWDOWN, false)

			SettingsGroup(title = stringResource(R.string.advanced)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.disable_captcha_auto_solve),
						subtitle = stringResource(R.string.disable_captcha_auto_solve_summary),
						icon = R.drawable.ic_disable,
						shape = pos.shape,
						checked = noAutoCaptcha,
						onCheckedChange = { noAutoCaptcha = it },
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.disable_captcha_notifications),
						subtitle = stringResource(R.string.disable_captcha_notifications_summary),
						icon = R.drawable.ic_notification,
						shape = pos.shape,
						checked = noCaptcha,
						onCheckedChange = { noCaptcha = it },
					)
				}
				if (isValidSource) {
					item { pos ->
						SwitchSettingsItem(
							title = stringResource(R.string.download_slowdown),
							subtitle = stringResource(R.string.download_slowdown_summary),
							icon = R.drawable.ic_timelapse,
							shape = pos.shape,
							checked = slowdown,
							onCheckedChange = { slowdown = it },
						)
					}
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}

@Composable
private fun ConfigKeyItem(
	key: ConfigKey<*>,
	prefs: SharedPreferences,
	shape: Shape,
) {
	when (key) {
		is ConfigKey.Domain -> {
			var domain by rememberPrefString(prefs, key.key, "")
			var showDialog by remember { mutableStateOf(false) }
			SettingsItem(
				title = stringResource(R.string.domain),
				subtitle = domain.ifEmpty { stringResource(R.string.default_s, key.defaultValue) },
				icon = R.drawable.ic_language,
				shape = shape,
				onClick = { showDialog = true },
			)
			if (showDialog) {
				TextInputDialog(
					title = stringResource(R.string.domain),
					initialValue = domain,
					hint = key.defaultValue,
					keyboardType = KeyboardType.Uri,
					isValid = { it.trim().isEmpty() || DomainValidator.isValidDomain(it.trim()) },
					onConfirm = { domain = it.trim() },
					onDismiss = { showDialog = false },
				)
			}
		}

		is ConfigKey.UserAgent -> {
			var userAgent by rememberPrefString(prefs, key.key, "")
			var showDialog by remember { mutableStateOf(false) }
			SettingsItem(
				title = stringResource(R.string.user_agent),
				subtitle = userAgent.ifEmpty { stringResource(R.string.default_s, key.defaultValue) },
				icon = R.drawable.ic_info_outline,
				shape = shape,
				onClick = { showDialog = true },
			)
			if (showDialog) {
				TextInputDialog(
					title = stringResource(R.string.user_agent),
					initialValue = userAgent,
					hint = key.defaultValue,
					isValid = {
						val trimmed = it.trim()
						trimmed.isEmpty() || runCatching {
							okhttp3.Headers.Builder().add("User-Agent", trimmed)
						}.isSuccess
					},
					onConfirm = { userAgent = it.trim() },
					onDismiss = { showDialog = false },
				)
			}
		}

		is ConfigKey.ShowSuspiciousContent -> {
			var showSuspicious by rememberPrefBoolean(prefs, key.key, key.defaultValue)
			SwitchSettingsItem(
				title = stringResource(R.string.show_suspicious_content),
				icon = R.drawable.ic_alert_outline,
				shape = shape,
				checked = showSuspicious,
				onCheckedChange = { showSuspicious = it },
			)
		}

		is ConfigKey.SplitByTranslations -> {
			var split by rememberPrefBoolean(prefs, key.key, key.defaultValue)
			SwitchSettingsItem(
				title = stringResource(R.string.split_by_translations),
				subtitle = stringResource(R.string.split_by_translations_summary),
				icon = R.drawable.ic_language,
				shape = shape,
				checked = split,
				onCheckedChange = { split = it },
			)
		}

		is ConfigKey.PreferredImageServer -> {
			val ctx = LocalContext.current
			var selected by rememberPrefString(prefs, key.key, key.defaultValue.orEmpty())
			val entries = remember(key) {
				key.presetValues.values.map { it ?: ctx.getString(R.string.automatic) }
			}
			val entryValues = remember(key) {
				key.presetValues.keys.map { it.orEmpty() }
			}
			ListSettingsItem(
				title = stringResource(R.string.image_server),
				entries = entries,
				entryValues = entryValues,
				selectedValue = selected,
				onValueChange = { selected = it },
				icon = R.drawable.ic_sync_alt,
				shape = shape,
			)
		}

		is ConfigKey.DisableUpdateChecking,
		is ConfigKey.InterceptCloudflare -> Unit
	}
}

@Composable
private fun rememberPrefBoolean(
	prefs: SharedPreferences,
	key: String,
	defaultValue: Boolean,
): androidx.compose.runtime.MutableState<Boolean> {
	val state = remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
	return remember(state) {
		object : androidx.compose.runtime.MutableState<Boolean> {
			override var value: Boolean
				get() = state.value
				set(newValue) {
					state.value = newValue
					prefs.edit { putBoolean(key, newValue) }
				}

			override fun component1() = value
			override fun component2(): (Boolean) -> Unit = { value = it }
		}
	}
}

@Composable
private fun rememberPrefString(
	prefs: SharedPreferences,
	key: String,
	defaultValue: String,
): androidx.compose.runtime.MutableState<String> {
	val state = remember(prefs, key) { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
	return remember(state) {
		object : androidx.compose.runtime.MutableState<String> {
			override var value: String
				get() = state.value
				set(newValue) {
					state.value = newValue
					prefs.edit { putString(key, newValue) }
				}

			override fun component1() = value
			override fun component2(): (String) -> Unit = { value = it }
		}
	}
}
