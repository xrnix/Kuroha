package org.koitharu.kotatsu.backups.ui.periodical

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.os.OpenDocumentTreeHelper
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.EditTextSettingsItem
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.ListSettingsItem
import org.koitharu.kotatsu.settings.compose.PlainInfoSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SliderSettingsItem
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberIntPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicalBackupSettingsFragment : BaseComposeSettingsFragment(R.string.periodic_backups),
	ActivityResultCallback<Uri?> {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var telegramBackupUploader: TelegramBackupUploader

	private val viewModel by viewModels<PeriodicalBackupSettingsViewModel>()

	private val outputSelectCall = OpenDocumentTreeHelper(this, this)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				val lastBackupDate by viewModel.lastBackupDate.collectAsState()
				val backupsDirectory by viewModel.backupsDirectory.collectAsState()
				val isTelegramCheckLoading by viewModel.isTelegramCheckLoading.collectAsState()

				PeriodicalBackupScreen(
					lastBackupDate = lastBackupDate,
					backupsDirectory = backupsDirectory,
					isTelegramAvailable = viewModel.isTelegramAvailable,
					isTelegramCheckLoading = isTelegramCheckLoading,
					onPickOutputDir = {
						if (!outputSelectCall.tryLaunch(null)) {
							Snackbar.make(this, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
						}
					},
					onOpenTelegramBot = {
						if (!telegramBackupUploader.openBotInApp(router)) {
							Snackbar.make(this, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
						}
					},
					onTestTelegram = viewModel::checkTelegram,
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(view, this))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(view))
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context?.contentResolver?.takePersistableUriPermission(result, takeFlags)
			settings.periodicalBackupDirectory = result
			viewModel.updateSummaryData()
		}
	}
}

@Composable
private fun PeriodicalBackupScreen(
	lastBackupDate: Date?,
	backupsDirectory: String?,
	isTelegramAvailable: Boolean,
	isTelegramCheckLoading: Boolean,
	onPickOutputDir: () -> Unit,
	onOpenTelegramBot: () -> Unit,
	onTestTelegram: () -> Unit,
) {
	val ctx = LocalContext.current

	var enabled by rememberBooleanPref(AppSettings.KEY_BACKUP_PERIODICAL_ENABLED, false)
	var freq by rememberStringPref(AppSettings.KEY_BACKUP_PERIODICAL_FREQUENCY, "7")
	var trimEnabled by rememberBooleanPref(AppSettings.KEY_BACKUP_PERIODICAL_TRIM, true)
	var maxCount by rememberIntPref(AppSettings.KEY_BACKUP_PERIODICAL_COUNT, 10)
	var tgEnabled by rememberBooleanPref(AppSettings.KEY_BACKUP_TG_ENABLED, false)
	var tgChatId by rememberStringPref(AppSettings.KEY_BACKUP_TG_CHAT, "")

	val freqEntries = remember {
		ctx.resources.getStringArray(R.array.backup_frequency).toList()
	}
	val freqValues = remember {
		ctx.resources.getStringArray(R.array.values_backup_frequency).toList()
	}

	val outputSummary = when (backupsDirectory) {
		null -> stringResource(R.string.invalid_value_message)
		"" -> null
		else -> backupsDirectory
	}

	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.periodic_backups)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.periodic_backups_enable),
						checked = enabled,
						onCheckedChange = { enabled = it },
						icon = R.drawable.ic_timer_run,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.backups_output_directory),
						subtitle = outputSummary,
						icon = if (backupsDirectory == null) R.drawable.ic_alert_outline else R.drawable.ic_folder_file,
						enabled = enabled,
						shape = pos.shape,
						onClick = onPickOutputDir,
					)
				}
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.backup_frequency),
						entries = freqEntries,
						entryValues = freqValues,
						selectedValue = freq,
						onValueChange = { freq = it },
						icon = R.drawable.ic_timer,
						enabled = enabled,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.delete_old_backups),
						subtitle = stringResource(R.string.delete_old_backups_summary),
						checked = trimEnabled,
						onCheckedChange = { trimEnabled = it },
						icon = R.drawable.ic_delete_all,
						enabled = enabled,
						shape = pos.shape,
					)
				}
				item { pos ->
					SliderSettingsItem(
						title = stringResource(R.string.max_backups_count),
						value = maxCount,
						valueFrom = 1,
						valueTo = 32,
						stepSize = 1,
						onValueChange = { maxCount = it },
						icon = R.drawable.ic_tag,
						enabled = enabled && trimEnabled,
						shape = pos.shape,
					)
				}
			}
		}

		if (lastBackupDate != null) {
			item {
				PlainInfoSettingsItem(
					text = stringResource(
						R.string.last_successful_backup,
						DateUtils.getRelativeTimeSpanString(lastBackupDate.time),
					),
					icon = R.drawable.ic_info_outline,
				)
			}
		}

		if (isTelegramAvailable) {
			item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
			item {
				SettingsGroup(title = stringResource(R.string.telegram_integration)) {
					item { pos ->
						SwitchSettingsItem(
							title = stringResource(R.string.send_backups_telegram),
							checked = tgEnabled,
							onCheckedChange = { tgEnabled = it },
							icon = R.drawable.ic_notification,
							enabled = enabled,
							shape = pos.shape,
						)
					}
					item { pos ->
						EditTextSettingsItem(
							title = stringResource(R.string.telegram_chat_id),
							value = tgChatId,
							hint = stringResource(R.string.telegram_chat_id_summary),
							onValueChange = { tgChatId = it },
							icon = R.drawable.ic_tag,
							enabled = enabled && tgEnabled,
							shape = pos.shape,
						)
					}
					item { pos ->
						ActionSettingsItem(
							title = stringResource(R.string.open_telegram_bot),
							subtitle = stringResource(R.string.open_telegram_bot_summary),
							icon = R.drawable.ic_open_external,
							enabled = enabled && tgEnabled,
							shape = pos.shape,
							onClick = onOpenTelegramBot,
						)
					}
					item { pos ->
						ActionSettingsItem(
							title = stringResource(R.string.test_connection),
							icon = R.drawable.ic_retry,
							enabled = enabled && tgEnabled && !isTelegramCheckLoading,
							shape = pos.shape,
							onClick = onTestTelegram,
						)
					}
				}
			}
		}

		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
