package org.koitharu.kotatsu.main.ui.welcome

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.backup.MihonBackupManager
import org.koitharu.kotatsu.backup.MihonBackupManager.RestoreReport
import org.koitharu.kotatsu.core.LocalizedAppContext
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.LocaleComparator
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.mapSortedByCount
import org.koitharu.kotatsu.core.util.ext.sortedWithSafe
import org.koitharu.kotatsu.core.util.ext.toList
import org.koitharu.kotatsu.core.util.ext.toLocale
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.filter.ui.model.FilterProperty
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.sync.SyncFeature
import org.koitharu.kotatsu.sync.data.SyncSettings
import java.util.Locale
import javax.inject.Inject

data class RestoreBackupResult(
	val report: RestoreReport?,
	val error: Throwable?,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
	private val repository: MangaSourcesRepository,
	private val settings: AppSettings,
	private val storageManager: LocalStorageManager,
	private val backupManager: MihonBackupManager,
	private val syncFeature: SyncFeature,
	private val syncSettings: SyncSettings,
	@LocalizedAppContext context: Context,
) : BaseViewModel() {

	private val allSources = repository.allMangaSources
	private val localesGroups by lazy { allSources.groupBy { it.locale.toLocale() } }

	private var updateJob: Job

	val selectedTheme = MutableStateFlow(settings.theme)
	val selectedColorScheme = MutableStateFlow(settings.colorScheme)
	val isAmoledEnabled = MutableStateFlow(settings.isAmoledTheme)
	val storageSummary = MutableStateFlow<String?>(null)
	val signedInEmail = MutableStateFlow(syncSettings.accountEmail)
	val onBackupRestored = MutableEventFlow<RestoreBackupResult>()
	val onGoogleSignInLaunch = MutableEventFlow<PendingIntent>()
	val onGoogleSignInCompleted = MutableEventFlow<Boolean>()

	val locales = MutableStateFlow(
		FilterProperty<Locale>(
			availableItems = listOf(Locale.ROOT),
			selectedItems = setOf(Locale.ROOT),
			isLoading = true,
			error = null,
		),
	)

	val types = MutableStateFlow(
		FilterProperty(
			availableItems = listOf(ContentType.MANGA),
			selectedItems = setOf(ContentType.MANGA),
			isLoading = true,
			error = null,
		),
	)

	init {
		updateJob = launchJob(Dispatchers.Default) {
			refreshStorageSummaryInternal()
			val contentTypes = allSources.mapSortedByCount { it.contentType }
			types.value = types.value.copy(
				availableItems = contentTypes,
				isLoading = false,
			)
			val languages = localesGroups.keys.associateBy { x -> x.language }
			val selectedLocales = HashSet<Locale>(2)
			ConfigurationCompat.getLocales(context.resources.configuration).toList()
				.firstNotNullOfOrNull { lc -> languages[lc.language] }
				?.let { selectedLocales += it }
			selectedLocales += Locale.ROOT
			locales.value = locales.value.copy(
				availableItems = localesGroups.keys.sortedWithSafe(LocaleComparator()),
				selectedItems = selectedLocales,
				isLoading = false,
			)
			repository.clearNewSourcesBadge()
			commit()
		}
	}

	fun setTheme(mode: Int) {
		if (selectedTheme.value == mode) return
		selectedTheme.value = mode
		settings.setThemeMode(mode)
	}

	fun setColorScheme(colorScheme: ColorScheme) {
		if (selectedColorScheme.value == colorScheme) return
		selectedColorScheme.value = colorScheme
		settings.setColorSchemeValue(colorScheme)
	}

	fun setAmoledTheme(isEnabled: Boolean) {
		if (isAmoledEnabled.value == isEnabled) return
		isAmoledEnabled.value = isEnabled
		settings.setAmoledTheme(isEnabled)
	}

	fun refreshStorageSummary() {
		launchJob(Dispatchers.Default) {
			refreshStorageSummaryInternal()
		}
	}

	private suspend fun refreshStorageSummaryInternal() {
		storageSummary.value = storageManager.getDefaultWriteableDir()?.let {
			storageManager.getDirectoryDisplayName(it, isFullPath = true)
		}
	}

	fun restoreBackup(uri: Uri) {
		launchLoadingJob(Dispatchers.Default) {
			val result = runCatchingCancellable {
				backupManager.restoreBackup(uri, MihonBackupManager.Options())
			}
			onBackupRestored.call(
				RestoreBackupResult(
					report = result.getOrNull(),
					error = result.exceptionOrNull(),
				),
			)
		}
	}

	fun launchGoogleSignIn() {
		launchLoadingJob(Dispatchers.Default) {
			runCatchingCancellable { syncFeature.beginSignIn() }
				.onSuccess { consent ->
					if (consent != null) {
						onGoogleSignInLaunch.call(consent)
					} else {
						onGoogleSignedIn()
					}
				}
				.onFailure { onGoogleSignInCompleted.call(false) }
		}
	}

	fun handleGoogleSignInResult(data: Intent?) {
		launchLoadingJob(Dispatchers.Default) {
			if (runCatchingCancellable { syncFeature.finishSignIn(data) }.isSuccess) {
				onGoogleSignedIn()
			} else {
				onGoogleSignInCompleted.call(false)
			}
		}
	}

	private suspend fun onGoogleSignedIn() {
		syncFeature.onSignedIn()
		signedInEmail.value = syncSettings.accountEmail
		onGoogleSignInCompleted.call(true)
	}

	fun setLocaleChecked(locale: Locale, isChecked: Boolean) {
		val snapshot = locales.value
		locales.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + locale
			} else {
				snapshot.selectedItems - locale
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.Default) {
			prevJob.join()
			commit()
		}
	}

	fun setTypeChecked(type: ContentType, isChecked: Boolean) {
		val snapshot = types.value
		types.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + type
			} else {
				snapshot.selectedItems - type
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.Default) {
			prevJob.join()
			commit()
		}
	}

	fun completeOnboarding() {
		settings.isOnboardingCompleted = true
		launchJob(Dispatchers.Default) {
			repository.clearNewSourcesBadge()
		}
	}

	private suspend fun commit() {
		val languages = locales.value.selectedItems.mapToSet { it.language }
		val types = types.value.selectedItems
		val enabledSources = allSources.filterTo(HashSet<ParserMangaSource>()) { x ->
			x.contentType in types && x.locale in languages
		}
		repository.setSourcesEnabledExclusive(enabledSources)
	}
}
