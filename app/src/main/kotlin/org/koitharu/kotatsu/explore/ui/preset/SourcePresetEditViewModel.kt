package org.koitharu.kotatsu.explore.ui.preset

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.explore.data.SourcePreset
import org.koitharu.kotatsu.explore.data.SourcePresetsRepository
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import javax.inject.Inject

data class PresetSourceItem(
	val name: String,
	val title: String,
	val locale: String,
	val isMihon: Boolean,
	val isNsfw: Boolean,
)

@HiltViewModel
class SourcePresetEditViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val presetsRepository: SourcePresetsRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
	@ApplicationContext private val appContext: Context,
) : BaseViewModel() {

	private val presetId = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID

	val onSaved = MutableEventFlow<Unit>()
	val preset = MutableStateFlow<SourcePreset?>(null)
	val allSources = MutableStateFlow<List<PresetSourceItem>>(emptyList())
	val allLocales = MutableStateFlow<Set<String>>(emptySet())
	val selectedSources = MutableStateFlow<Set<String>>(emptySet())

	init {
		launchLoadingJob(Dispatchers.Default) {
			val skipNsfw = settings.isNsfwContentDisabled
			val combined = (sourcesRepository.allMangaSources + sourcesRepository.getMihonSources())
				.filter { !skipNsfw || !it.isNsfw() }
				.map { src ->
					val locale = when (src) {
						is ParserMangaSource -> src.locale
						is MihonMangaSource -> src.language
						else -> ""
					}
					PresetSourceItem(
						name = src.name,
						title = src.getTitle(appContext),
						locale = locale,
						isMihon = src is MihonMangaSource,
						isNsfw = src.isNsfw(),
					)
				}
				.sortedBy { it.title.lowercase() }

			allSources.value = combined
			allLocales.value = combined.mapNotNullTo(LinkedHashSet()) {
				it.locale.takeIf { l -> l.isNotEmpty() }
			}

			val existing = if (presetId != NO_ID) presetsRepository.getById(presetId) else null
			preset.value = existing
			selectedSources.value = existing?.sources
				?: sourcesRepository.getEnabledSources().mapTo(HashSet()) { it.name }
		}
	}

	fun snapshotEnabledSources() {
		launchJob(Dispatchers.Default) {
			selectedSources.value = sourcesRepository.getEnabledSources().mapTo(HashSet()) { it.name }
		}
	}

	fun toggleSource(name: String) {
		selectedSources.update { current ->
			if (name in current) current - name else current + name
		}
	}

	fun selectAll(names: Collection<String>) {
		selectedSources.update { it + names }
	}

	fun clearSelection(names: Collection<String>? = null) {
		if (names == null) {
			selectedSources.value = emptySet()
		} else {
			selectedSources.update { it - names.toSet() }
		}
	}

	fun save(title: String, selectedLanguages: Set<String>) {
		launchLoadingJob(Dispatchers.Default) {
			check(title.isNotEmpty())
			val sources = selectedSources.value
			if (presetId == NO_ID) {
				presetsRepository.createPreset(title, selectedLanguages, sources)
			} else {
				presetsRepository.updatePreset(presetId, title, selectedLanguages, sources)
			}
			onSaved.call(Unit)
		}
	}

	companion object {
		const val NO_ID = -1L
	}
}
