package org.koitharu.kotatsu.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.EditTextSettingsItem
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.PlainInfoSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import org.koitharu.kotatsu.suggestions.ui.SuggestionsWorker
import javax.inject.Inject

@AndroidEntryPoint
class SuggestionsSettingsFragment : BaseComposeSettingsFragment(R.string.suggestions),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var suggestionsScheduler: SuggestionsWorker.Scheduler

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		settings.subscribe(this)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				SuggestionsScreen()
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		settings.unsubscribe(this)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (settings.isSuggestionsEnabled && (key == AppSettings.KEY_SUGGESTIONS
				|| key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS)
		) {
			updateSuggestions()
		}
	}

	private fun updateSuggestions() {
		lifecycleScope.launch(Dispatchers.Default) {
			suggestionsScheduler.startNow()
		}
	}
}

@Composable
private fun SuggestionsScreen() {
	var suggestionsEnabled by rememberBooleanPref(AppSettings.KEY_SUGGESTIONS, true)
	var includeDisabledSources by rememberBooleanPref(AppSettings.KEY_SUGGESTIONS_DISABLED_SOURCES, false)
	var excludedTags by rememberStringPref(AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS, "")
	var excludeNsfw by rememberBooleanPref(AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW, false)

	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.suggestions_enable),
						checked = suggestionsEnabled,
						onCheckedChange = { suggestionsEnabled = it },
						icon = R.drawable.ic_suggestion,
						main = true,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.suggestions)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.include_disabled_sources),
						subtitle = stringResource(R.string.suggestions_disabled_sources_summary),
						checked = includeDisabledSources,
						onCheckedChange = { includeDisabledSources = it },
						icon = R.drawable.ic_manga_source,
						shape = pos.shape,
						enabled = suggestionsEnabled,
					)
				}
				item { pos ->
					EditTextSettingsItem(
						title = stringResource(R.string.suggestions_excluded_genres),
						value = excludedTags,
						hint = stringResource(R.string.suggestions_excluded_genres_summary),
						dialogMessage = stringResource(R.string.suggestions_excluded_genres_summary),
						icon = R.drawable.ic_tag,
						onValueChange = { excludedTags = it },
						shape = pos.shape,
						enabled = suggestionsEnabled,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.exclude_nsfw_from_suggestions),
						subtitle = stringResource(R.string.exclude_nsfw_from_suggestions_summary),
						checked = excludeNsfw,
						onCheckedChange = { excludeNsfw = it },
						icon = R.drawable.ic_disable,
						shape = pos.shape,
						enabled = suggestionsEnabled,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup {
				item {
					PlainInfoSettingsItem(
						text = stringResource(R.string.suggestions_info),
						icon = R.drawable.ic_info_outline,
					)
				}
			}
		}
	}
}
