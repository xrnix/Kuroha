package org.koitharu.kotatsu.download.ui.dialog

import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.DownloadFormat
import org.koitharu.kotatsu.core.ui.ComposeAlertDialogFragment
import org.koitharu.kotatsu.core.ui.dialog.DialogCornerAction
import org.koitharu.kotatsu.core.ui.dialog.ExpressiveDialogCard
import org.koitharu.kotatsu.core.ui.dialog.ExpressiveOptionGroupItem
import org.koitharu.kotatsu.core.ui.dialog.ExpressivePillButton
import org.koitharu.kotatsu.core.util.ext.findActivity
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.getQuantityStringSafe
import org.koitharu.kotatsu.core.util.ext.joinToStringWithLimit
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.main.ui.owners.BottomNavOwner
import org.koitharu.kotatsu.parsers.util.format

@AndroidEntryPoint
class DownloadDialogFragment : ComposeAlertDialogFragment() {

	private val viewModel by viewModels<DownloadDialogViewModel>()

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.onScheduled.observeEvent(viewLifecycleOwner, ::onDownloadScheduled)
		viewModel.onError.observeEvent(viewLifecycleOwner, ::onError)
	}

	@Composable
	override fun Content() {
		val context = LocalContext.current
		val colors = MaterialTheme.colorScheme
		val options by viewModel.chaptersSelectOptions.collectAsState()
		val isOptionsLoading by viewModel.isOptionsLoading.collectAsState()
		val isLoading by viewModel.isLoading.collectAsState()
		val defaultFormat by viewModel.defaultFormat.collectAsState()
		val destinations by viewModel.availableDestinations.collectAsState()

		val mangaSummary = remember {
			viewModel.manga.joinToStringWithLimit(context, 120) { it.title }
		}

		var selectedOption by rememberSaveable { mutableIntStateOf(OPT_WHOLE_MANGA) }
		var startNow by rememberSaveable { mutableStateOf(true) }
		var showMore by rememberSaveable { mutableStateOf(false) }

		var selectedFormatIndex by remember(defaultFormat) {
			mutableIntStateOf(defaultFormat?.ordinal ?: 0)
		}
		var selectedDestIndex by remember(destinations) {
			mutableIntStateOf(destinations.indexOfFirst { it.isChecked }.coerceAtLeast(0))
		}

		val optionEntries = buildList {
			add(OPT_WHOLE_MANGA)
			if (options.wholeBranch != null) add(OPT_WHOLE_BRANCH)
			if (options.firstChapters != null) add(OPT_FIRST_CHAPTERS)
			if (options.unreadChapters != null) add(OPT_UNREAD_CHAPTERS)
		}

		fun schedule(allowMetered: Boolean) {
			val macro = when (selectedOption) {
				OPT_WHOLE_MANGA -> options.wholeManga
				OPT_WHOLE_BRANCH -> options.wholeBranch ?: options.wholeManga
				OPT_FIRST_CHAPTERS -> options.firstChapters ?: options.wholeManga
				OPT_UNREAD_CHAPTERS -> options.unreadChapters ?: options.wholeManga
				else -> options.wholeManga
			}
			viewModel.confirm(
				startNow = startNow,
				chaptersMacro = macro,
				format = DownloadFormat.entries.getOrNull(selectedFormatIndex),
				destination = destinations.getOrNull(selectedDestIndex),
				allowMetered = allowMetered,
			)
		}

		ExpressiveDialogCard(
			icon = painterResource(R.drawable.ic_download),
			title = stringResource(R.string.save_manga),
			message = mangaSummary,
			cornerAction = DialogCornerAction(
				icon = painterResource(R.drawable.ic_close),
				label = stringResource(android.R.string.cancel),
				onClick = ::dismiss,
			),
			footer = {
				ExpressivePillButton(
					text = stringResource(R.string.save),
					icon = painterResource(R.drawable.ic_download),
					primary = true,
					enabled = !isLoading,
					onClick = { router.askForDownloadOverMeteredNetwork(::schedule) },
				)
			},
		) {
			optionEntries.forEachIndexed { idx, optId ->
				val isSelected = selectedOption == optId
				var menuExpanded by remember { mutableStateOf(false) }
				val title: String
				val subtitle: String?
				var hasDropdown = false
				when (optId) {
					OPT_WHOLE_MANGA -> {
						title = stringResource(R.string.download_option_whole_manga)
						val count = options.wholeManga.chaptersCount
						subtitle = if (count > 0) {
							context.resources.getQuantityStringSafe(R.plurals.chapters, count, count)
						} else {
							null
						}
					}

					OPT_WHOLE_BRANCH -> {
						val branchOpt = options.wholeBranch!!
						title = stringResource(
							R.string.download_option_all_chapters,
							branchOpt.selectedBranch ?: stringResource(R.string.unknown),
						)
						val count = branchOpt.chaptersCount
						subtitle = if (count > 0) {
							context.resources.getQuantityStringSafe(R.plurals.chapters, count, count)
						} else {
							null
						}
						hasDropdown = branchOpt.branches.size > 1
					}

					OPT_FIRST_CHAPTERS -> {
						val firstOpt = options.firstChapters!!
						title = stringResource(
							R.string.download_option_first_n_chapters,
							context.resources.getQuantityStringSafe(
								R.plurals.chapters,
								firstOpt.chaptersCount,
								firstOpt.chaptersCount,
							),
						)
						subtitle = firstOpt.branch
						hasDropdown = true
					}

					else -> {
						val unreadOpt = options.unreadChapters!!
						title = if (unreadOpt.chaptersCount == Int.MAX_VALUE) {
							stringResource(R.string.download_option_all_unread)
						} else {
							stringResource(
								R.string.download_option_next_unread_n_chapters,
								context.resources.getQuantityStringSafe(
									R.plurals.chapters,
									unreadOpt.chaptersCount,
									unreadOpt.chaptersCount,
								),
							)
						}
						subtitle = null
						hasDropdown = true
					}
				}

				ExpressiveOptionGroupItem(
					index = idx,
					count = optionEntries.size,
					onClick = { selectedOption = optId },
				) {
					RadioButton(selected = isSelected, onClick = null)
					Spacer(Modifier.width(10.dp))
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = title,
							style = MaterialTheme.typography.bodyLarge,
							fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
							color = if (isSelected) colors.primary else colors.onSurface,
						)
						if (!subtitle.isNullOrEmpty()) {
							Text(
								text = subtitle,
								style = MaterialTheme.typography.bodySmall,
								color = colors.onSurfaceVariant,
							)
						}
					}
					if (hasDropdown && isSelected) {
						Box {
							IconButton(onClick = { menuExpanded = true }) {
								Icon(
									painter = painterResource(R.drawable.ic_expand_more),
									contentDescription = null,
									tint = colors.primary,
								)
							}
							DropdownMenu(
								expanded = menuExpanded,
								onDismissRequest = { menuExpanded = false },
							) {
								when (optId) {
									OPT_WHOLE_BRANCH -> {
										val branches = options.wholeBranch?.branches?.keys?.toList().orEmpty()
										branches.forEach { branch ->
											DropdownMenuItem(
												text = { Text(branch ?: stringResource(R.string.unknown)) },
												onClick = {
													viewModel.setSelectedBranch(branch)
													menuExpanded = false
												},
											)
										}
									}

									OPT_FIRST_CHAPTERS -> {
										val max = options.firstChapters?.maxAvailableCount ?: 0
										chaptersCount(max).forEach { count ->
											DropdownMenuItem(
												text = { Text(count.format()) },
												onClick = {
													viewModel.setFirstChaptersCount(count)
													menuExpanded = false
												},
											)
										}
									}

									OPT_UNREAD_CHAPTERS -> {
										val max = options.unreadChapters?.maxAvailableCount ?: 0
										chaptersCount(max).forEach { count ->
											DropdownMenuItem(
												text = { Text(count.format()) },
												onClick = {
													viewModel.setUnreadChaptersCount(count)
													menuExpanded = false
												},
											)
										}
										DropdownMenuItem(
											text = { Text(stringResource(R.string.chapters_all)) },
											onClick = {
												viewModel.setUnreadChaptersCount(Int.MAX_VALUE)
												menuExpanded = false
											},
										)
									}
								}
							}
						}
					}
				}
			}

			if (isOptionsLoading) {
				Spacer(Modifier.height(8.dp))
				LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
			}

			if (viewModel.manga.size == 1) {
				Spacer(Modifier.height(10.dp))
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(horizontal = 4.dp),
				) {
					Icon(
						painter = painterResource(R.drawable.ic_tap),
						contentDescription = null,
						tint = colors.onSurfaceVariant,
						modifier = Modifier.size(18.dp),
					)
					Spacer(Modifier.width(10.dp))
					Text(
						text = stringResource(R.string.chapter_selection_hint),
						style = MaterialTheme.typography.bodySmall,
						color = colors.onSurfaceVariant,
					)
				}
			}

			Spacer(Modifier.height(10.dp))
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(16.dp))
					.background(colors.background)
					.clickable { startNow = !startNow }
					.padding(horizontal = 14.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = stringResource(R.string.start_download),
					style = MaterialTheme.typography.bodyLarge,
					color = colors.onSurface,
					modifier = Modifier.weight(1f),
				)
				Switch(
					checked = startNow,
					onCheckedChange = { startNow = it },
				)
			}

			Spacer(Modifier.height(8.dp))
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(16.dp))
					.background(colors.background)
					.clickable { showMore = !showMore }
					.padding(horizontal = 14.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = stringResource(R.string.more_options),
					style = MaterialTheme.typography.bodyMedium,
					color = colors.onSurfaceVariant,
					modifier = Modifier.weight(1f),
				)
				Icon(
					painter = painterResource(if (showMore) R.drawable.ic_arrow_up else R.drawable.ic_expand_more),
					contentDescription = null,
					tint = colors.onSurfaceVariant,
				)
			}

			AnimatedVisibility(visible = showMore) {
				Column(modifier = Modifier.padding(top = 8.dp)) {
					val formats = stringArrayResource(R.array.download_formats)
					var destMenuOpen by remember { mutableStateOf(false) }
					var formatMenuOpen by remember { mutableStateOf(false) }

					Box {
						ExpressiveOptionGroupItem(
							index = 0,
							count = 2,
							onClick = { destMenuOpen = true },
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = stringResource(R.string.destination_directory),
									style = MaterialTheme.typography.labelMedium,
									color = colors.primary,
								)
								Text(
									text = destinations.getOrNull(selectedDestIndex)?.title
										?: destinations.getOrNull(selectedDestIndex)?.titleRes?.let { stringResource(it) }
										?: "",
									style = MaterialTheme.typography.bodyMedium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
							Icon(
								painter = painterResource(R.drawable.ic_expand_more),
								contentDescription = null,
								tint = colors.onSurfaceVariant,
							)
						}
						DropdownMenu(
							expanded = destMenuOpen,
							onDismissRequest = { destMenuOpen = false },
						) {
							destinations.forEachIndexed { i, dir ->
								val label = dir.title ?: dir.titleRes?.let { stringResource(it) } ?: ""
								DropdownMenuItem(
									text = { Text(label) },
									onClick = {
										selectedDestIndex = i
										destMenuOpen = false
									},
								)
							}
						}
					}

					Box {
						ExpressiveOptionGroupItem(
							index = 1,
							count = 2,
							onClick = { formatMenuOpen = true },
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = stringResource(R.string.preferred_download_format),
									style = MaterialTheme.typography.labelMedium,
									color = colors.primary,
								)
								Text(
									text = formats.getOrNull(selectedFormatIndex) ?: "",
									style = MaterialTheme.typography.bodyMedium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
							Icon(
								painter = painterResource(R.drawable.ic_expand_more),
								contentDescription = null,
								tint = colors.onSurfaceVariant,
							)
						}
						DropdownMenu(
							expanded = formatMenuOpen,
							onDismissRequest = { formatMenuOpen = false },
						) {
							formats.forEachIndexed { i, label ->
								DropdownMenuItem(
									text = { Text(label) },
									onClick = {
										selectedFormatIndex = i
										formatMenuOpen = false
									},
								)
							}
						}
					}
				}
			}
		}
	}

	private fun onError(e: Throwable) {
		MaterialAlertDialogBuilder(context ?: return)
			.setNegativeButton(R.string.close, null)
			.setTitle(R.string.error)
			.setMessage(e.getDisplayMessage(resources))
			.show()
		dismiss()
	}

	private fun onDownloadScheduled(isStarted: Boolean) {
		val bundle = Bundle(1)
		bundle.putBoolean(ARG_STARTED, isStarted)
		setFragmentResult(RESULT_KEY, bundle)
		dismiss()
	}

	private fun chaptersCount(max: Int) = sequence {
		yield(1)
		var seed = 5
		var step = 5
		while (seed + step <= max) {
			yield(seed)
			step = when {
				seed < 20 -> 5
				seed < 60 -> 10
				else -> 20
			}
			seed += step
		}
		if (seed < max) {
			yield(max)
		}
	}

	private class SnackbarResultListener(
		private val host: View,
	) : FragmentResultListener {

		override fun onFragmentResult(requestKey: String, result: Bundle) {
			val isStarted = result.getBoolean(ARG_STARTED, true)
			val snackbar = Snackbar.make(
				host,
				if (isStarted) R.string.download_started else R.string.download_added,
				Snackbar.LENGTH_LONG,
			)
			(host.context.findActivity() as? BottomNavOwner)?.let {
				snackbar.anchorView = it.bottomNav
			}
			val router = AppRouter.from(host)
			if (router != null) {
				snackbar.setAction(R.string.details) { router.openDownloads() }
			}
			snackbar.show()
		}
	}

	companion object {
		private const val OPT_WHOLE_MANGA = 0
		private const val OPT_WHOLE_BRANCH = 1
		private const val OPT_FIRST_CHAPTERS = 2
		private const val OPT_UNREAD_CHAPTERS = 3

		private const val RESULT_KEY = "DOWNLOAD_STARTED"
		private const val ARG_STARTED = "started"

		fun registerCallback(
			fm: FragmentManager,
			lifecycleOwner: LifecycleOwner,
			snackbarHost: View,
		) = fm.setFragmentResultListener(RESULT_KEY, lifecycleOwner, SnackbarResultListener(snackbarHost))

		fun unregisterCallback(fm: FragmentManager) = fm.clearFragmentResultListener(RESULT_KEY)
	}
}
