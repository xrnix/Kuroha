package org.koitharu.kotatsu.settings.sources.manage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getLocale
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import org.koitharu.kotatsu.settings.compose.MultiChoiceDialog
import org.koitharu.kotatsu.settings.compose.SettingsSwitch
import java.util.Locale

@AndroidEntryPoint
class AddBuiltInSourcesSheet : BottomSheetDialogFragment() {

	private val viewModel by viewModels<SourcesManageViewModel>({ requireParentFragment() })

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			KurohaTheme {
				val allSources by viewModel.allBuiltInSources.collectAsState()
				val selectedTypes by viewModel.selectedContentTypes.collectAsState()
				val selectedLocales by viewModel.selectedLocales.collectAsState()
				AddBuiltInSourcesContent(
					sources = allSources,
					availableTypes = viewModel.availableContentTypes,
					initialTypes = selectedTypes,
					availableLocales = viewModel.availableLocales,
					initialLocales = selectedLocales,
					onToggle = { source, enabled -> viewModel.setEnabled(source, enabled) },
					onEnableBatch = { batch -> viewModel.enableBatch(batch) },
					onApplyGlobalFilters = { types, locales -> viewModel.applySourceFilters(types, locales) },
					onDismiss = ::dismiss,
				)
			}
		}
	}

	override fun onStart() {
		super.onStart()
		(dialog as? BottomSheetDialog)?.let { sheetDialog ->
			sheetDialog.behavior.skipCollapsed = true
			sheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
		}
	}
}

private enum class BuiltInFilter(val label: String) {
	DISABLED("Disabled"),
	ALL("All"),
	ENABLED("Enabled"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBuiltInSourcesContent(
	sources: List<BuiltInSourceEntry>,
	availableTypes: List<ContentType>,
	initialTypes: Set<ContentType>,
	availableLocales: List<Locale>,
	initialLocales: Set<Locale>,
	onToggle: (ParserMangaSource, Boolean) -> Unit,
	onEnableBatch: (List<ParserMangaSource>) -> Unit,
	onApplyGlobalFilters: (Set<ContentType>, Set<Locale>) -> Unit,
	onDismiss: () -> Unit,
) {
	val context = LocalContext.current
	var query by remember { mutableStateOf("") }
	var statusFilter by remember { mutableStateOf(BuiltInFilter.DISABLED) }
	var filterTypes by remember { mutableStateOf<Set<ContentType>>(emptySet()) }
	var filterLocales by remember { mutableStateOf<Set<Locale>>(emptySet()) }
	var showTypesDialog by remember { mutableStateOf(false) }
	var showLocalesDialog by remember { mutableStateOf(false) }

	if (showTypesDialog) {
		val entries = availableTypes.map { context.getString(it.titleResId) }
		val selectedIndices = availableTypes.indices.filter { availableTypes[it] in filterTypes }.toSet()
		MultiChoiceDialog(
			title = stringResource(R.string.categories),
			entries = entries,
			selectedIndices = selectedIndices,
			onConfirm = { indices ->
				filterTypes = indices.mapTo(HashSet()) { availableTypes[it] }
			},
			onDismiss = { showTypesDialog = false },
			icon = R.drawable.ic_tag,
		)
	}

	if (showLocalesDialog) {
		val entries = availableLocales.map { it.getDisplayName(context) }
		val selectedIndices = availableLocales.indices.filter { availableLocales[it] in filterLocales }.toSet()
		MultiChoiceDialog(
			title = stringResource(R.string.languages),
			entries = entries,
			selectedIndices = selectedIndices,
			onConfirm = { indices ->
				filterLocales = indices.mapTo(HashSet()) { availableLocales[it] }
			},
			onDismiss = { showLocalesDialog = false },
			icon = R.drawable.ic_language,
		)
	}

	val filtered = remember(sources, query, statusFilter, filterTypes, filterLocales) {
		val q = query.trim()
		sources.filter { entry ->
			val matchesStatus = when (statusFilter) {
				BuiltInFilter.ALL -> true
				BuiltInFilter.DISABLED -> !entry.isEnabled
				BuiltInFilter.ENABLED -> entry.isEnabled
			}
			val matchesType = filterTypes.isEmpty() || entry.source.contentType in filterTypes
			val matchesLocale = filterLocales.isEmpty() || entry.source.getLocale() in filterLocales
			val matchesQuery = q.isEmpty() || entry.title.contains(q, ignoreCase = true)
			matchesStatus && matchesType && matchesLocale && matchesQuery
		}
	}

	val categoriesLabel = if (filterTypes.isEmpty()) {
		stringResource(R.string.categories)
	} else {
		"${stringResource(R.string.categories)} (${filterTypes.size})"
	}
	val languagesLabel = if (filterLocales.isEmpty()) {
		stringResource(R.string.languages)
	} else {
		"${stringResource(R.string.languages)} (${filterLocales.size})"
	}

	Surface(
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.navigationBarsPadding()
				.padding(top = 16.dp, bottom = 12.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp),
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "Built-in Sources",
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.SemiBold,
					)
					Text(
						text = "${filtered.size} shown • ${sources.count { it.isEnabled }}/${sources.size} enabled",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				val disabledInView = filtered.filter { !it.isEnabled }.map { it.source }
				if (disabledInView.isNotEmpty() && disabledInView.size <= 50) {
					TextButton(onClick = { onEnableBatch(disabledInView) }) {
						Text("Enable (${disabledInView.size})")
					}
				}
				TextButton(onClick = onDismiss) {
					Text(stringResource(android.R.string.ok))
				}
			}

			Spacer(Modifier.height(10.dp))

			OutlinedTextField(
				value = query,
				onValueChange = { query = it },
				singleLine = true,
				placeholder = { Text(stringResource(R.string.search)) },
				leadingIcon = {
					Icon(
						painter = painterResource(R.drawable.ic_search),
						contentDescription = null,
						modifier = Modifier.size(20.dp),
					)
				},
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp),
			)

			Spacer(Modifier.height(10.dp))

			SingleChoiceSegmentedButtonRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp),
			) {
				val entries = BuiltInFilter.entries
				entries.forEachIndexed { index, option ->
					SegmentedButton(
						selected = statusFilter == option,
						onClick = { statusFilter = option },
						shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
					) {
						Text(option.label, maxLines = 1)
					}
				}
			}

			Spacer(Modifier.height(8.dp))

			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp),
			) {
				FilterChip(
					selected = filterTypes.isNotEmpty(),
					onClick = { showTypesDialog = true },
					label = { Text(categoriesLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
					leadingIcon = {
						Icon(
							painter = painterResource(R.drawable.ic_tag),
							contentDescription = null,
							modifier = Modifier.size(16.dp),
						)
					},
					modifier = Modifier.weight(1f),
				)
				FilterChip(
					selected = filterLocales.isNotEmpty(),
					onClick = { showLocalesDialog = true },
					label = { Text(languagesLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
					leadingIcon = {
						Icon(
							painter = painterResource(R.drawable.ic_language),
							contentDescription = null,
							modifier = Modifier.size(16.dp),
						)
					},
					modifier = Modifier.weight(1f),
				)
				if (filterTypes.isNotEmpty() || filterLocales.isNotEmpty()) {
					TextButton(
						onClick = {
							val typesToApply = filterTypes.ifEmpty { initialTypes.ifEmpty { setOf(ContentType.MANGA) } }
							val localesToApply = filterLocales.ifEmpty { initialLocales.ifEmpty { setOf(Locale.ROOT) } }
							onApplyGlobalFilters(typesToApply, localesToApply)
						},
					) {
						Text("Apply")
					}
				}
			}

			Spacer(Modifier.height(6.dp))

			LazyColumn(
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 240.dp, max = 440.dp),
			) {
				items(filtered, key = { it.source.name }) { entry ->
					Surface(
						onClick = { onToggle(entry.source, !entry.isEnabled) },
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.surfaceContainer,
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = entry.title,
									style = MaterialTheme.typography.bodyLarge,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								Text(
									text = "${entry.localeLabel} • ${entry.contentTypeLabel}",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
							Spacer(Modifier.width(8.dp))
							SettingsSwitch(
								checked = entry.isEnabled,
								onCheckedChange = { onToggle(entry.source, it) },
							)
						}
					}
				}
			}
		}
	}
}
