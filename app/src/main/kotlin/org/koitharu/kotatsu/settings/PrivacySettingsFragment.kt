package org.koitharu.kotatsu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.os.AppShortcutManager
import org.koitharu.kotatsu.core.prefs.AppProtectionTimeout
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.core.util.ext.viewLifecycleScope
import org.koitharu.kotatsu.parsers.util.names
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.ListSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import org.koitharu.kotatsu.settings.protect.showProtectMethodDialog
import org.koitharu.kotatsu.sourcescore.data.CommunitySettings
import org.koitharu.kotatsu.suggestions.ui.SuggestionsWorker
import javax.inject.Inject

@AndroidEntryPoint
class PrivacySettingsFragment : BaseComposeSettingsFragment(R.string.privacy_and_content) {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var appShortcutManager: AppShortcutManager

	@Inject
	lateinit var communitySettings: CommunitySettings

	@Inject
	lateinit var suggestionsScheduler: SuggestionsWorker.Scheduler

	private var pendingProtectState: Boolean? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				var telemetryEnabled by remember { mutableStateOf(communitySettings.isTelemetryEnabled) }
				PrivacyScreen(
					dynamicShortcutsAvailable = appShortcutManager.isDynamicShortcutsAvailable(),
					communityEnabled = communitySettings.isEnabled,
					telemetryEnabled = telemetryEnabled,
					onTelemetryChange = { checked ->
						communitySettings.isTelemetryEnabled = checked
						telemetryEnabled = checked
					},
					onSuggestionsNsfwChanged = {
						if (settings.isSuggestionsEnabled) {
							viewLifecycleScope.launch(Dispatchers.Default) {
								suggestionsScheduler.startNow()
							}
						}
					},
					onProtectToggle = ::onProtectToggle,
				)
			}
		}
	}

	override fun onDestroyView() {
		pendingProtectState = null
		super.onDestroyView()
	}

	private fun isAuthenticationSupported(): Boolean {
		val manager = context?.let { BiometricManager.from(it) } ?: return false
		return manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS
	}

	private fun onProtectToggle(enable: Boolean) {
		if (!enable) {
			settings.isAppProtectionEnabled = false
			settings.clearAppPassword()
			return
		}
		showProtectMethodDialog(
			activity = requireActivity(),
			deviceAuthSupported = isAuthenticationSupported(),
			onSelectDevice = { startProtectionAuthentication(true) },
			onPinConfirmed = { pin ->
				settings.updateAppPassword(pin)
			},
		)
	}

	private fun startProtectionAuthentication(requestedState: Boolean): Boolean {
		if (!isAuthenticationSupported() || !isAdded) return false
		val executor = context?.let { ContextCompat.getMainExecutor(it) } ?: return false
		pendingProtectState = requestedState
		val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
			override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
				val state = pendingProtectState ?: return
				settings.clearAppPassword()
				settings.isAppProtectionEnabled = state
				pendingProtectState = null
			}

			override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
				pendingProtectState = null
			}
		})
		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle(getString(R.string.app_name))
			.setSubtitle(getString(R.string.require_unlock))
			.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
			.setConfirmationRequired(false)
			.build()
		prompt.authenticate(promptInfo)
		return true
	}
}

@Composable
private fun PrivacyScreen(
	dynamicShortcutsAvailable: Boolean,
	communityEnabled: Boolean,
	telemetryEnabled: Boolean,
	onTelemetryChange: (Boolean) -> Unit,
	onSuggestionsNsfwChanged: () -> Unit,
	onProtectToggle: (Boolean) -> Unit,
) {
	val ctx = LocalContext.current

	var disableNsfw by rememberBooleanPref(AppSettings.KEY_DISABLE_NSFW, false)
	var incognitoNsfw by rememberStringPref(AppSettings.KEY_INCOGNITO_NSFW, TriStateOption.ASK.name)
	var shortcutsNoNsfw by rememberBooleanPref(AppSettings.KEY_SHORTCUTS_NO_NSFW, true)
	var trackerNoNsfw by rememberBooleanPref(AppSettings.KEY_TRACKER_NO_NSFW, false)
	var suggestionsExcludeNsfw by rememberBooleanPref(AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW, false)
	var discordSkipNsfw by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC_SKIP_NSFW, false)

	val shortcutsEnabled by rememberBooleanPref(AppSettings.KEY_SHORTCUTS, true)
	val trackerEnabled by rememberBooleanPref(AppSettings.KEY_TRACKER_ENABLED, true)
	val suggestionsEnabled by rememberBooleanPref(AppSettings.KEY_SUGGESTIONS, false)
	val discordEnabled by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC, false)

	var protectApp by rememberBooleanPref(AppSettings.KEY_PROTECT_APP, false)
	var protectAppTimeout by rememberStringPref(
		AppSettings.KEY_PROTECT_APP_TIMEOUT,
		AppProtectionTimeout.INSTANT.name,
	)
	var screenshotsPolicy by rememberStringPref(
		AppSettings.KEY_SCREENSHOTS_POLICY,
		ScreenshotsPolicy.ALLOW.name,
	)

	val incognitoEntries = remember {
		ctx.resources.getStringArray(R.array.incognito_nsfw_options).toList()
	}
	val incognitoValues = remember { TriStateOption.entries.names().toList() }

	val protectTimeoutEntries = remember {
		AppProtectionTimeout.entries.map { ctx.getString(it.titleResId) }
	}
	val protectTimeoutValues = remember { AppProtectionTimeout.entries.names().toList() }

	val screenshotsPolicyEntries = remember {
		ctx.resources.getStringArray(R.array.screenshots_policy).toList()
	}
	val screenshotsPolicyValues = remember { ScreenshotsPolicy.entries.names().toList() }

	LaunchedEffect(suggestionsExcludeNsfw) {
		onSuggestionsNsfwChanged()
	}

	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.adult_content)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.disable_nsfw),
						subtitle = stringResource(R.string.disable_nsfw_summary),
						checked = disableNsfw,
						onCheckedChange = { disableNsfw = it },
						icon = R.drawable.ic_sfw,
						shape = pos.shape,
					)
				}
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.incognito_for_nsfw),
						entries = incognitoEntries,
						entryValues = incognitoValues,
						selectedValue = incognitoNsfw,
						onValueChange = { incognitoNsfw = it },
						icon = R.drawable.ic_incognito,
						shape = pos.shape,
					)
				}
				if (dynamicShortcutsAvailable) {
					item { pos ->
						SwitchSettingsItem(
							title = stringResource(R.string.exclude_nsfw_from_shortcuts),
							subtitle = stringResource(R.string.exclude_nsfw_from_shortcuts_summary),
							checked = shortcutsNoNsfw,
							onCheckedChange = { shortcutsNoNsfw = it },
							icon = R.drawable.ic_shortcut,
							enabled = shortcutsEnabled,
							shape = pos.shape,
						)
					}
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.disable_nsfw_notifications),
						subtitle = stringResource(R.string.disable_nsfw_notifications_summary),
						checked = trackerNoNsfw,
						onCheckedChange = { trackerNoNsfw = it },
						icon = R.drawable.ic_notification,
						enabled = trackerEnabled,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.exclude_nsfw_from_suggestions),
						subtitle = stringResource(R.string.exclude_nsfw_from_suggestions_summary),
						checked = suggestionsExcludeNsfw,
						onCheckedChange = { suggestionsExcludeNsfw = it },
						icon = R.drawable.ic_suggestion,
						enabled = suggestionsEnabled,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.hide_nsfw_from_discord),
						subtitle = stringResource(R.string.rpc_skip_nsfw_summary),
						checked = discordSkipNsfw,
						onCheckedChange = { discordSkipNsfw = it },
						icon = R.drawable.ic_discord,
						enabled = discordEnabled,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.app_security)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.require_unlock),
						subtitle = stringResource(R.string.require_unlock_summary),
						checked = protectApp,
						onCheckedChange = onProtectToggle,
						icon = R.drawable.ic_lock,
						shape = pos.shape,
					)
				}
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.require_unlock_after),
						entries = protectTimeoutEntries,
						entryValues = protectTimeoutValues,
						selectedValue = protectAppTimeout,
						onValueChange = { protectAppTimeout = it },
						icon = R.drawable.ic_timer,
						enabled = protectApp,
						shape = pos.shape,
					)
				}
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.screenshots_policy),
						entries = screenshotsPolicyEntries,
						entryValues = screenshotsPolicyValues,
						selectedValue = screenshotsPolicy,
						onValueChange = { screenshotsPolicy = it },
						icon = R.drawable.ic_data_privacy,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.your_activity)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.community_telemetry),
						subtitle = stringResource(R.string.community_telemetry_summary),
						checked = telemetryEnabled,
						onCheckedChange = onTelemetryChange,
						icon = R.drawable.ic_trending_small,
						enabled = communityEnabled,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
