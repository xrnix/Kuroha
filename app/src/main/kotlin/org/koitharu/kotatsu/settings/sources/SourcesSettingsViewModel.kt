package org.koitharu.kotatsu.settings.sources

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.core.parser.bundle.ParsersUpdater
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.LocaleComparator
import org.koitharu.kotatsu.core.util.ext.mapSortedByCount
import org.koitharu.kotatsu.core.util.ext.sortedWithSafe
import org.koitharu.kotatsu.core.util.ext.toLocale
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.util.mapToSet
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SourcesSettingsViewModel @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
	@ApplicationContext private val context: Context,
	private val parsersUpdater: ParsersUpdater,
) : BaseViewModel() {

	/** Whether this build, on this phone, takes newer parsers than its own at all. */
	val isParsersUpdateSupported: Boolean
		get() = parsersUpdater.isSupported

	private val linksHandlerActivity = ComponentName(context, "org.koitharu.kotatsu.details.ui.DetailsByLinkActivity")

	private val allSources: Set<ParserMangaSource>
		get() = sourcesRepository.allMangaSources

	val availableContentTypes: List<ContentType> by lazy {
		allSources.mapSortedByCount { it.contentType }
	}

	val availableLocales: List<Locale> by lazy {
		allSources.groupBy { it.locale.toLocale() }.keys.sortedWithSafe(LocaleComparator())
	}

	val selectedContentTypes: StateFlow<Set<ContentType>> = sourcesRepository.observeEnabledSources()
		.map { list ->
			list.mapNotNullTo(HashSet()) { (it.mangaSource as? ParserMangaSource)?.contentType }
		}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptySet())

	val selectedLocales: StateFlow<Set<Locale>> = sourcesRepository.observeEnabledSources()
		.map { list ->
			list.mapNotNullTo(HashSet()) { (it.mangaSource as? ParserMangaSource)?.locale?.toLocale() }
		}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptySet())

	val enabledSourcesCount = sourcesRepository.observeEnabledSourcesCount()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, -1)

	val availableSourcesCount = sourcesRepository.observeAvailableSourcesCount()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, -1)

	val isLinksEnabled = MutableStateFlow(isLinksEnabled())

	fun applySourceFilters(types: Set<ContentType>, locales: Set<Locale>) {
		viewModelScope.launch(Dispatchers.Default) {
			if (settings.isAllSourcesEnabled) {
				settings.isAllSourcesEnabled = false
			}
			val languages = locales.mapToSet { it.language }
			val enabledSources = allSources.filterTo(HashSet<ParserMangaSource>()) { x ->
				x.contentType in types && x.locale in languages
			}
			sourcesRepository.setSourcesEnabledExclusive(enabledSources)
		}
	}

	fun setLinksEnabled(isEnabled: Boolean) {
		context.packageManager.setComponentEnabledSetting(
			linksHandlerActivity,
			if (isEnabled) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED,
			PackageManager.DONT_KILL_APP,
		)
		isLinksEnabled.value = isLinksEnabled()
	}

	private fun isLinksEnabled(): Boolean {
		val state = context.packageManager.getComponentEnabledSetting(linksHandlerActivity)
		return state == COMPONENT_ENABLED_STATE_ENABLED || state == COMPONENT_ENABLED_STATE_DEFAULT
	}
}
