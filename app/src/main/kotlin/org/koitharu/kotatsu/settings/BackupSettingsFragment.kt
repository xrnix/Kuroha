package org.koitharu.kotatsu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.BackupUtils
import org.koitharu.kotatsu.backups.ui.backup.BackupService
import org.koitharu.kotatsu.backups.ui.periodical.PeriodicalBackupSettingsFragment
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.NavigationSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold

@AndroidEntryPoint
class BackupSettingsFragment : BaseComposeSettingsFragment(R.string.backup_restore) {

	private val createLocalBackupLauncher = registerForActivityResult(
		ActivityResultContracts.CreateDocument("application/zip"),
	) { uri ->
		if (uri != null) {
			if (BackupService.start(requireContext(), uri)) {
				Toast.makeText(requireContext(), R.string.creating_backup, Toast.LENGTH_SHORT).show()
			} else {
				view?.let {
					Snackbar.make(it, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
				}
			}
		}
	}

	private val restoreLocalBackupLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			router.showBackupRestoreDialog(uri)
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
				BackupScreen(
					onCreateBackup = {
						if (!createLocalBackupLauncher.tryLaunch(BackupUtils.generateFileName(requireContext()))) {
							Snackbar.make(this, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
						}
					},
					onRestoreLocal = {
						if (!restoreLocalBackupLauncher.tryLaunch(arrayOf("*/*"))) {
							Snackbar.make(this, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
						}
					},
					onOpenPeriodic = {
						(activity as? SettingsActivity)?.openFragment(
							PeriodicalBackupSettingsFragment::class.java,
							null,
							isFromRoot = false,
						)
					},
				)
			}
		}
	}
}

@Composable
private fun BackupScreen(
	onCreateBackup: () -> Unit,
	onRestoreLocal: () -> Unit,
	onOpenPeriodic: () -> Unit,
) {
	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.backup_restore)) {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.create_backup),
						subtitle = stringResource(R.string.create_backup_summary),
						icon = R.drawable.ic_save,
						shape = pos.shape,
						onClick = onCreateBackup,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.restore_backup),
						subtitle = stringResource(R.string.restore_summary),
						icon = R.drawable.ic_revert,
						shape = pos.shape,
						onClick = onRestoreLocal,
					)
				}
				item { pos ->
					NavigationSettingsItem(
						title = stringResource(R.string.periodic_backups),
						subtitle = stringResource(R.string.periodic_backups_summary),
						icon = R.drawable.ic_backup_restore,
						shape = pos.shape,
						onClick = onOpenPeriodic,
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
