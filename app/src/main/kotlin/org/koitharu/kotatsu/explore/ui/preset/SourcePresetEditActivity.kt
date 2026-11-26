package org.koitharu.kotatsu.explore.ui.preset

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BaseActivityEntryPoint
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.toLocale
import org.koitharu.kotatsu.settings.compose.KurohaTheme

private enum class EngineFilter { ALL, KOTATSU, MIHON }

@AndroidEntryPoint
class SourcePresetEditActivity : AppCompatActivity() {

	private val viewModel by viewModels<SourcePresetEditViewModel>()
	private var errorMessage by mutableStateOf<String?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		val settings = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(applicationContext).settings
		setTheme(settings.colorScheme.styleResId)
		if (settings.isAmoledTheme) {
			setTheme(R.style.ThemeOverlay_Kotatsu_Amoled)
		}
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		viewModel.onSaved.observeEvent(this) { finishAfterTransition() }
		viewModel.onError.observeEvent(this) { throwable ->
			errorMessage = throwable.getDisplayMessage(resources)
		}

		setContent {
			KurohaTheme {
				SourcePresetEditScreen(
					viewModel = viewModel,
					errorMessage = errorMessage,
					onClose = { finishAfterTransition() },
				)
			}
		}
	}

	companion object {
		fun newIntent(context: Context, presetId: Long = SourcePresetEditViewModel.NO_ID): Intent {
			return Intent(context, SourcePresetEditActivity::class.java)
				.putExtra(AppRouter.KEY_ID, presetId)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePresetEditScreen(
	viewModel: SourcePresetEditViewModel,
	errorMessage: String?,
	onClose: () -> Unit,
) {
	val context = LocalContext.current
	val preset by viewModel.preset.collectAsState()
	val allSources by viewModel.allSources.collectAsState()
	val allLocales by viewModel.allLocales.collectAsState()
	val selectedSources by viewModel.selectedSources.collectAsState()
	val isLoading by viewModel.isLoading.collectAsState()

	var title by rememberSaveable { mutableStateOf("") }
	var selectedLanguages by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var engineFilter by rememberSaveable { mutableStateOf(EngineFilter.ALL) }
	var initializedFromPreset by rememberSaveable { mutableStateOf(false) }

	LaunchedEffect(preset) {
		val loaded = preset
		if (loaded != null && !initializedFromPreset) {
			title = loaded.title
			selectedLanguages = loaded.languages
			initializedFromPreset = true
		}
	}

	val sortedLocales = remember(allLocales) {
		allLocales.sortedBy { it.toLocale().getDisplayName(context) }
	}

	val filteredSources = remember(allSources, selectedLanguages, searchQuery, engineFilter) {
		val q = searchQuery.trim().lowercase()
		allSources.filter { item ->
			val matchesEngine = when (engineFilter) {
				EngineFilter.ALL -> true
				EngineFilter.KOTATSU -> !item.isMihon
				EngineFilter.MIHON -> item.isMihon
			}
			val matchesLang = selectedLanguages.isEmpty() || item.locale in selectedLanguages
			val matchesQuery = q.isEmpty() || item.title.lowercase().contains(q) || item.name.lowercase().contains(q)
			matchesEngine && matchesLang && matchesQuery
		}
	}

	Scaffold(
		modifier = Modifier.imePadding(),
		containerColor = MaterialTheme.colorScheme.surface,
		topBar = {
			TopAppBar(
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				title = {
					Text(
						text = stringResource(if (preset == null) R.string.create_preset else R.string.edit_preset),
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				},
				navigationIcon = {
					FilledTonalIconButton(
						onClick = onClose,
						modifier = Modifier.padding(start = 8.dp, end = 4.dp),
					) {
						Icon(
							painter = painterResource(R.drawable.ic_close),
							contentDescription = null,
							modifier = Modifier.size(20.dp),
						)
					}
				},
				actions = {
					Button(
						onClick = { viewModel.save(title.trim(), selectedLanguages) },
						enabled = title.isNotBlank() && !isLoading,
						shape = CircleShape,
						contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
						modifier = Modifier.padding(end = 12.dp),
					) {
						Icon(
							painter = painterResource(R.drawable.ic_check),
							contentDescription = null,
							modifier = Modifier.size(18.dp),
						)
						Spacer(Modifier.width(6.dp))
						Text(
							text = stringResource(R.string.save),
							fontWeight = FontWeight.SemiBold,
						)
					}
				},
			)
		},
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			if (isLoading) {
				item(key = "loading") {
					LinearProgressIndicator(
						modifier = Modifier
							.fillMaxWidth()
							.clip(CircleShape),
					)
				}
			}

			// Expressive Preset Name + Snapshot Card
			item(key = "header_card") {
				Surface(
					shape = RoundedCornerShape(24.dp),
					color = MaterialTheme.colorScheme.surfaceContainerLow,
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						TextField(
							value = title,
							onValueChange = { title = it },
							placeholder = { Text(stringResource(R.string.preset_name)) },
							singleLine = true,
							shape = RoundedCornerShape(16.dp),
							colors = TextFieldDefaults.colors(
								focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
								unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
								focusedIndicatorColor = Color.Transparent,
								unfocusedIndicatorColor = Color.Transparent,
							),
							modifier = Modifier.fillMaxWidth(),
						)

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							FilledTonalButton(
								onClick = { viewModel.snapshotEnabledSources() },
								shape = CircleShape,
								modifier = Modifier.weight(1f),
							) {
								Text(
									text = stringResource(R.string.preset_snapshot_enabled),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									fontWeight = FontWeight.Medium,
								)
							}
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.primaryContainer,
							) {
								Text(
									text = context.resources.getQuantityString(
										R.plurals.sources_count,
										selectedSources.size,
										selectedSources.size,
									),
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.onPrimaryContainer,
									modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
								)
							}
						}

						errorMessage?.let {
							Text(
								text = it,
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.bodySmall,
							)
						}
					}
				}
			}

			// Search + Engine Segmented Filter + Languages
			item(key = "filters") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 4.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					TextField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						placeholder = { Text(stringResource(R.string.preset_search_sources)) },
						leadingIcon = {
							Icon(
								painter = painterResource(R.drawable.ic_search_by),
								contentDescription = null,
								modifier = Modifier.size(20.dp),
							)
						},
						trailingIcon = if (searchQuery.isNotEmpty()) {
							{
								IconButton(onClick = { searchQuery = "" }) {
									Icon(
										painter = painterResource(R.drawable.ic_close),
										contentDescription = null,
										modifier = Modifier.size(18.dp),
									)
								}
							}
						} else null,
						singleLine = true,
						shape = CircleShape,
						colors = TextFieldDefaults.colors(
							focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
							unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
							focusedIndicatorColor = Color.Transparent,
							unfocusedIndicatorColor = Color.Transparent,
						),
						modifier = Modifier.fillMaxWidth(),
					)

					val engineOptions = listOf(
						EngineFilter.ALL to stringResource(R.string.preset_engine_all),
						EngineFilter.KOTATSU to stringResource(R.string.preset_engine_kotatsu),
						EngineFilter.MIHON to stringResource(R.string.preset_engine_mihon),
					)
					SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
						engineOptions.forEachIndexed { index, (filter, label) ->
							SegmentedButton(
								selected = engineFilter == filter,
								onClick = { engineFilter = filter },
								shape = SegmentedButtonDefaults.itemShape(
									index = index,
									count = engineOptions.size,
								),
							) {
								Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
							}
						}
					}

					if (sortedLocales.isNotEmpty()) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							sortedLocales.forEach { locale ->
								val checked = locale in selectedLanguages
								FilterChip(
									selected = checked,
									onClick = {
										selectedLanguages = if (checked) {
											selectedLanguages - locale
										} else {
											selectedLanguages + locale
										}
									},
									shape = CircleShape,
									colors = FilterChipDefaults.filterChipColors(
										selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
										selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
									),
									label = { Text(locale.toLocale().getDisplayName(context)) },
								)
							}
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = stringResource(R.string.sources),
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(start = 4.dp),
						)
						Row {
							TextButton(onClick = { viewModel.selectAll(filteredSources.map { it.name }) }) {
								Text(stringResource(R.string.preset_select_all))
							}
							TextButton(onClick = { viewModel.clearSelection(filteredSources.map { it.name }) }) {
								Text(stringResource(R.string.preset_clear_all))
							}
						}
					}
				}
			}

			// Grouped M3 Expressive Source Cards
			itemsIndexed(filteredSources, key = { _, item -> item.name }) { index, item ->
				val checked = item.name in selectedSources
				val containerColor by animateColorAsState(
					targetValue = if (checked) {
						MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
					} else {
						MaterialTheme.colorScheme.surfaceContainerLow
					},
					label = "sourceCardBg",
				)
				val itemShape = when {
					filteredSources.size == 1 -> RoundedCornerShape(22.dp)
					index == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
					index == filteredSources.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
					else -> RoundedCornerShape(8.dp)
				}

				Surface(
					shape = itemShape,
					color = containerColor,
					modifier = Modifier
						.fillMaxWidth()
						.clip(itemShape)
						.clickable { viewModel.toggleSource(item.name) },
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = checked,
							onCheckedChange = { viewModel.toggleSource(item.name) },
						)
						Spacer(Modifier.width(10.dp))
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = item.title,
								style = MaterialTheme.typography.bodyLarge,
								fontWeight = FontWeight.Medium,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							if (item.locale.isNotEmpty()) {
								Text(
									text = item.locale.toLocale().getDisplayName(context),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
						Spacer(Modifier.width(8.dp))
						Surface(
							shape = CircleShape,
							color = if (item.isMihon) {
								MaterialTheme.colorScheme.tertiaryContainer
							} else {
								MaterialTheme.colorScheme.surfaceContainerHighest
							},
						) {
							Text(
								text = stringResource(
									if (item.isMihon) R.string.preset_engine_mihon else R.string.preset_engine_kotatsu,
								),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								color = if (item.isMihon) {
									MaterialTheme.colorScheme.onTertiaryContainer
								} else {
									MaterialTheme.colorScheme.onSurfaceVariant
								},
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
							)
						}
					}
				}
			}

			item(key = "bottom_spacer") {
				Spacer(Modifier.height(16.dp))
			}
		}
	}
}
