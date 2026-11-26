package org.koitharu.kotatsu.main.ui.welcome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.ui.BaseActivityEntryPoint
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.settings.compose.KurohaTheme

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

	private val viewModel by viewModels<WelcomeViewModel>()

	private var permissionStates by mutableStateOf(OnboardingPermissions(false, false, false))
	private var syncStepDone by mutableIntStateOf(0)

	private val restoreMihonLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) viewModel.restoreBackup(uri)
	}

	private val restoreKurohaLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) router.showBackupRestoreDialog(uri)
	}

	private val googleSignInLauncher = registerForActivityResult(
		ActivityResultContracts.StartIntentSenderForResult(),
	) { result ->
		if (result.resultCode == RESULT_OK) {
			viewModel.handleGoogleSignInResult(result.data)
		}
	}

	private val installPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { refreshPermissionStates() }

	private val notificationsPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) { refreshPermissionStates() }

	private val batteryPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { refreshPermissionStates() }

	override fun onCreate(savedInstanceState: Bundle?) {
		val settings = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(applicationContext).settings
		setTheme(settings.colorScheme.styleResId)
		if (settings.isAmoledTheme) {
			setTheme(R.style.ThemeOverlay_Kotatsu_Amoled)
		}
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)

		viewModel.onGoogleSignInLaunch.observeEvent(this) { consent ->
			googleSignInLauncher.launch(IntentSenderRequest.Builder(consent).build())
		}
		viewModel.onGoogleSignInCompleted.observeEvent(this) { success ->
			Toast.makeText(
				this,
				if (success) R.string.sync_completed else R.string.sync_error,
				Toast.LENGTH_LONG,
			).show()
			if (success) syncStepDone++
		}
		viewModel.onBackupRestored.observeEvent(this) { result ->
			val msg = result.report?.let {
				getString(R.string.restore_completed)
			} ?: result.error?.getDisplayMessage(resources)?.toString()
				?: getString(R.string.error_occurred)
			Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
			if (result.report != null) syncStepDone++
		}

		refreshPermissionStates()

		setContent {
			KurohaTheme {
				val selectedTheme by viewModel.selectedTheme.collectAsState()
				val selectedColorScheme by viewModel.selectedColorScheme.collectAsState()
				val isAmoledEnabled by viewModel.isAmoledEnabled.collectAsState()
				val storageSummary by viewModel.storageSummary.collectAsState()
				val signedInEmail by viewModel.signedInEmail.collectAsState()
				val isLoading by viewModel.isLoading.collectAsState()
				val locales by viewModel.locales.collectAsState()
				val types by viewModel.types.collectAsState()

				OnboardingScreen(
					selectedTheme = selectedTheme,
					selectedColorScheme = selectedColorScheme,
					isAmoledEnabled = isAmoledEnabled,
					storageSummary = storageSummary,
					signedInEmail = signedInEmail,
					isLoading = isLoading,
					permissions = permissionStates,
					syncStepDone = syncStepDone,
					locales = locales,
					types = types,
					actions = OnboardingActions(
						onThemeChange = { mode ->
							viewModel.setTheme(mode)
							AppCompatDelegate.setDefaultNightMode(mode)
						},
						onColorSchemeChange = { name ->
							ColorScheme.entries.find { it.name == name }?.let {
								viewModel.setColorScheme(it)
								recreate()
							}
						},
						onAmoledChange = { enabled ->
							viewModel.setAmoledTheme(enabled)
							recreate()
						},
						onSelectDestination = { router.openDirectoriesSettings() },
						onPermissionInstall = { requestInstallPermission() },
						onPermissionNotifications = { requestNotificationsPermission() },
						onPermissionBattery = { requestBatteryPermission() },
						onSignInGoogle = { viewModel.launchGoogleSignIn() },
						onRestoreKuroha = {
							restoreKurohaLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
						},
						onRestoreMihon = {
							restoreMihonLauncher.launch(arrayOf("application/*", "*/*"))
						},
						onLocaleToggle = { locale, checked ->
							viewModel.setLocaleChecked(locale, checked)
						},
						onTypeToggle = { type, checked ->
							viewModel.setTypeChecked(type, checked)
						},
						onSkip = { finishOnboarding() },
						onFinish = { finishOnboarding() },
					),
				)
			}
		}
	}

	override fun onResume() {
		super.onResume()
		refreshPermissionStates()
		viewModel.refreshStorageSummary()
	}

	private fun refreshPermissionStates() {
		val hasInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			packageManager.canRequestPackageInstalls()
		} else {
			true
		}
		val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
				PackageManager.PERMISSION_GRANTED
		} else {
			true
		}
		val pm = getSystemService(PowerManager::class.java)
		val hasBattery = pm?.isIgnoringBatteryOptimizations(packageName) == true
		permissionStates = OnboardingPermissions(
			hasInstall = hasInstall,
			hasNotifications = hasNotifications,
			hasBattery = hasBattery,
		)
	}

	private fun requestInstallPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
				data = Uri.parse("package:$packageName")
			}
			installPermissionLauncher.launch(intent)
		}
	}

	private fun requestNotificationsPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}

	private fun requestBatteryPermission() {
		val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
			data = Uri.parse("package:$packageName")
		}
		runCatching { batteryPermissionLauncher.launch(intent) }
	}

	private fun finishOnboarding() {
		viewModel.completeOnboarding()
		finish()
	}
}
