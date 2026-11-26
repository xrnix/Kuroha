package org.koitharu.kotatsu.settings.discord

import android.content.Intent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.scrobbling.discord.ui.DiscordAuthActivity
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.EditTextSettingsItem
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref

@AndroidEntryPoint
class DiscordSettingsFragment : BaseComposeSettingsFragment(R.string.discord) {

	private val viewModel by viewModels<DiscordSettingsViewModel>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				val tokenState by viewModel.tokenState.collectAsState()
				DiscordScreen(
					tokenState = tokenState,
					onOpenSignIn = {
						startActivity(Intent(requireContext(), DiscordAuthActivity::class.java))
					},
				)
			}
		}
	}
}

@Composable
private fun DiscordScreen(
	tokenState: Pair<TokenState, String?>,
	onOpenSignIn: () -> Unit,
) {
	var enabled by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC, false)
	var token by rememberStringPref(AppSettings.KEY_DISCORD_TOKEN, "")
	var discordSkipNsfw by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC_SKIP_NSFW, false)

	val (state, stateToken) = tokenState
	val tokenSubtitle = when (state) {
		TokenState.EMPTY -> stringResource(R.string.discord_token_summary)
		TokenState.REQUIRED -> stringResource(R.string.discord_token_summary)
		TokenState.INVALID -> stringResource(R.string.invalid_token, stateToken.orEmpty())
		TokenState.VALID -> if (stateToken.isNullOrEmpty()) stringResource(R.string.discord_token_summary) else stateToken
		TokenState.CHECKING -> stringResource(R.string.loading_)
	}
	val tokenIcon = if (state == TokenState.REQUIRED || state == TokenState.INVALID) {
		R.drawable.ic_alert_outline
	} else {
		R.drawable.ic_lock
	}

	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.discord_share_reading),
						checked = enabled,
						onCheckedChange = { enabled = it },
						icon = R.drawable.ic_discord,
						main = true,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.accounts_and_sync)) {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.sign_in),
						subtitle = stringResource(R.string.discord_rpc_summary),
						icon = R.drawable.ic_open_external,
						onClick = onOpenSignIn,
						shape = pos.shape,
						enabled = enabled,
					)
				}
				item { pos ->
					EditTextSettingsItem(
						title = stringResource(R.string.discord_token),
						value = token,
						hint = stringResource(R.string.discord_token_hint),
						dialogMessage = stringResource(R.string.discord_token_description, stringResource(R.string.sign_in)),
						icon = tokenIcon,
						isPassword = true,
						onValueChange = { token = it },
						shape = pos.shape,
						enabled = enabled,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.privacy)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.hide_nsfw_from_discord),
						subtitle = stringResource(R.string.rpc_skip_nsfw_summary),
						checked = discordSkipNsfw,
						onCheckedChange = { discordSkipNsfw = it },
						icon = R.drawable.ic_disable,
						shape = pos.shape,
						enabled = enabled,
					)
				}
			}
		}
	}
}
