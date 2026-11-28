package org.koitharu.kotatsu.download.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.dialog.expressiveGroupItemShape
import org.koitharu.kotatsu.core.util.ext.HapticEffect
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.core.util.ext.rememberHapticEffect
import org.koitharu.kotatsu.core.util.ext.stableMangaCoverKey
import org.koitharu.kotatsu.download.ui.list.chapters.DownloadChapter
import org.koitharu.kotatsu.list.ui.model.EmptyState
import org.koitharu.kotatsu.list.ui.model.ListHeader
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.LoadingState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DownloadsScreen(
	items: List<ListModel>?,
	summary: DownloadsSummary,
	selectedFilter: DownloadFilter,
	filterCounts: Map<DownloadFilter, Int>,
	selectedIds: Set<Long>,
	coil: ImageLoader,
	bottomInset: Dp,
	onSelectFilter: (DownloadFilter) -> Unit,
	onItemClick: (DownloadItemModel) -> Unit,
	onItemLongClick: (DownloadItemModel) -> Unit,
	onExpandClick: (DownloadItemModel) -> Unit,
	onPauseClick: (DownloadItemModel) -> Unit,
	onResumeClick: (DownloadItemModel) -> Unit,
	onRetryClick: (DownloadItemModel) -> Unit,
	onCancelClick: (DownloadItemModel) -> Unit,
	onTogglePriority: (DownloadItemModel) -> Unit,
	onPauseAll: () -> Unit,
	onResumeAll: () -> Unit,
	onRetryAllFailed: () -> Unit,
	onCancelAll: () -> Unit,
	onBatchPause: () -> Unit,
	onBatchResume: () -> Unit,
	onBatchPrioritize: () -> Unit,
	onBatchCancel: () -> Unit,
	onBatchRemove: () -> Unit,
	onSelectAll: () -> Unit,
	onClearSelection: () -> Unit,
	onOpenDownloadSettings: () -> Unit,
	allDownloads: List<DownloadItemModel>,
) {
	val isSelecting = selectedIds.isNotEmpty()

	Column(modifier = Modifier.fillMaxSize()) {
		AnimatedVisibility(
			visible = isSelecting,
			enter = expandVertically() + fadeIn(),
			exit = shrinkVertically() + fadeOut(),
		) {
			SelectionActionBar(
				selectedCount = selectedIds.size,
				selectedItems = remember(selectedIds, allDownloads) {
					allDownloads.filter { it.id.mostSignificantBits in selectedIds }
				},
				onClearSelection = onClearSelection,
				onSelectAll = onSelectAll,
				onPrioritize = onBatchPrioritize,
				onPause = onBatchPause,
				onResume = onBatchResume,
				onCancel = onBatchCancel,
				onRemove = onBatchRemove,
			)
		}

		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp,
				end = 16.dp,
				top = 8.dp,
				bottom = bottomInset + 24.dp,
			),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			if (summary.hasOngoing && !isSelecting) {
				item(key = "hero_summary") {
					ExpressiveSummaryCard(
						summary = summary,
						onPauseAll = onPauseAll,
						onResumeAll = onResumeAll,
						onRetryAllFailed = onRetryAllFailed,
						onCancelAll = onCancelAll,
					)
				}
			}

			item(key = "filter_chips") {
				FilterChipRow(
					selectedFilter = selectedFilter,
					counts = filterCounts,
					onSelectFilter = onSelectFilter,
				)
			}

			if (items == null) {
				item(key = "loading") {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 48.dp),
						contentAlignment = Alignment.Center,
					) {
						CircularProgressIndicator()
					}
				}
			} else if (items.isEmpty() || items.firstOrNull() is EmptyState) {
				item(key = "empty") {
					ExpressiveEmptyDownloadsState(
						isFiltered = selectedFilter != DownloadFilter.ALL,
						onShowAll = { onSelectFilter(DownloadFilter.ALL) },
						onOpenSettings = onOpenDownloadSettings,
					)
				}
			} else {
				items(
					items = items,
					key = { model ->
						when (model) {
							is DownloadItemModel -> model.id.toString()
							is ListHeader -> "header_${model.hashCode()}"
							is LoadingState -> "loading_item"
							else -> model.hashCode()
						}
					},
				) { model ->
					when (model) {
						is ListHeader -> SectionHeader(model)
						is DownloadItemModel -> DownloadCard(
							item = model,
							coil = coil,
							isSelected = model.id.mostSignificantBits in selectedIds,
							isSelectionMode = isSelecting,
							onClick = { onItemClick(model) },
							onLongClick = { onItemLongClick(model) },
							onExpandClick = { onExpandClick(model) },
							onPauseClick = { onPauseClick(model) },
							onResumeClick = { onResumeClick(model) },
							onRetryClick = { onRetryClick(model) },
							onCancelClick = { onCancelClick(model) },
							onTogglePriority = { onTogglePriority(model) },
							modifier = Modifier.animateItem(),
						)
						else -> Unit
					}
				}
			}
		}
	}
}

@Composable
private fun ExpressiveSummaryCard(
	summary: DownloadsSummary,
	onPauseAll: () -> Unit,
	onResumeAll: () -> Unit,
	onRetryAllFailed: () -> Unit,
	onCancelAll: () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	val haptic = rememberHapticEffect()
	val parts = buildList {
		if (summary.activeCount > 0) add(stringResource(R.string.download_summary_active, summary.activeCount))
		if (summary.queuedCount > 0) add(stringResource(R.string.download_summary_queued, summary.queuedCount))
		if (summary.pausedCount > 0) add(stringResource(R.string.download_summary_paused, summary.pausedCount))
		if (summary.failedCount > 0) add(stringResource(R.string.download_summary_failed, summary.failedCount))
	}

	Surface(
		shape = RoundedCornerShape(28.dp),
		color = colors.surfaceContainer,
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Surface(
					shape = CircleShape,
					color = colors.primary,
					contentColor = colors.onPrimary,
					modifier = Modifier.size(44.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							painter = painterResource(
								if (summary.activeCount > 0) R.drawable.ic_downloading else R.drawable.ic_download,
							),
							contentDescription = null,
							modifier = Modifier.size(22.dp),
						)
					}
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = when {
							summary.activeCount > 0 -> stringResource(R.string.manga_downloading_)
							summary.failedCount > 0 -> stringResource(R.string.error)
							else -> stringResource(R.string.paused)
						},
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold,
						color = colors.onSurface,
					)
					if (parts.isNotEmpty()) {
						Text(
							text = parts.joinToString(" • "),
							style = MaterialTheme.typography.bodySmall,
							color = colors.onSurfaceVariant,
						)
					}
				}
				if (summary.activeCount > 0 && !summary.hasIndeterminate) {
					Surface(
						shape = RoundedCornerShape(50),
						color = colors.primary,
						contentColor = colors.onPrimary,
					) {
						Text(
							text = "${(summary.overallProgress * 100f).roundToInt()}%",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Bold,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						)
					}
				}
			}

			if (summary.activeCount > 0 || summary.pausedCount > 0) {
				WavyProgressBar(
					progress = summary.overallProgress,
					isIndeterminate = summary.hasIndeterminate,
					isWavy = summary.activeCount > 0,
					indicatorColor = colors.primary,
					trackColor = colors.surfaceContainerLowest,
					modifier = Modifier.fillMaxWidth(),
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (summary.activeCount > 0) {
					FilledTonalButton(
						onClick = {
							haptic(HapticEffect.CONFIRM)
							onPauseAll()
						},
						shape = RoundedCornerShape(50),
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
					) {
						Icon(painterResource(R.drawable.ic_pause), contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(Modifier.width(6.dp))
						Text(stringResource(R.string.pause), maxLines = 1)
					}
				}
				if (summary.pausedCount > 0) {
					Button(
						onClick = {
							haptic(HapticEffect.CONFIRM)
							onResumeAll()
						},
						shape = RoundedCornerShape(50),
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
					) {
						Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(Modifier.width(6.dp))
						Text(stringResource(R.string.resume), maxLines = 1)
					}
				}
				if (summary.failedCount > 0) {
					FilledTonalButton(
						onClick = {
							haptic(HapticEffect.CONFIRM)
							onRetryAllFailed()
						},
						shape = RoundedCornerShape(50),
						colors = ButtonDefaults.filledTonalButtonColors(
							containerColor = colors.errorContainer,
							contentColor = colors.onErrorContainer,
						),
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
					) {
						Icon(painterResource(R.drawable.ic_retry), contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(Modifier.width(6.dp))
						Text(stringResource(R.string.retry), maxLines = 1)
					}
				}
				Spacer(Modifier.weight(1f))
				if (summary.activeCount + summary.queuedCount + summary.pausedCount > 0) {
					FilledTonalIconButton(
						onClick = {
							haptic(HapticEffect.CONFIRM)
							onCancelAll()
						},
						colors = IconButtonDefaults.filledTonalIconButtonColors(
							containerColor = colors.errorContainer.copy(alpha = 0.7f),
							contentColor = colors.onErrorContainer,
						),
					) {
						Icon(painterResource(R.drawable.ic_cancel_multiple), contentDescription = stringResource(R.string.cancel_all))
					}
				}
			}
		}
	}
}

@Composable
private fun FilterChipRow(
	selectedFilter: DownloadFilter,
	counts: Map<DownloadFilter, Int>,
	onSelectFilter: (DownloadFilter) -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	val haptic = rememberHapticEffect()
	val entries = listOf(
		DownloadFilter.ALL to R.string.download_filter_all,
		DownloadFilter.ACTIVE to R.string.download_filter_active,
		DownloadFilter.PAUSED to R.string.download_filter_paused,
		DownloadFilter.FAILED to R.string.download_filter_failed,
		DownloadFilter.COMPLETED to R.string.download_filter_completed,
	)

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		items(entries, key = { it.first.name }) { (filter, labelRes) ->
			val count = counts[filter] ?: 0
			val selected = filter == selectedFilter
			FilterChip(
				selected = selected,
				onClick = {
					haptic(HapticEffect.CONFIRM)
					onSelectFilter(filter)
				},
				label = {
					val text = stringResource(labelRes)
					Text(
						text = if (count > 0) "$text ($count)" else text,
						fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
					)
				},
				shape = RoundedCornerShape(50),
				colors = FilterChipDefaults.filterChipColors(
					containerColor = colors.surfaceContainer,
					labelColor = colors.onSurfaceVariant,
					selectedContainerColor = colors.primary,
					selectedLabelColor = colors.onPrimary,
					selectedLeadingIconColor = colors.onPrimary,
				),
				border = null,
			)
		}
	}
}

@Composable
private fun SelectionActionBar(
	selectedCount: Int,
	selectedItems: List<DownloadItemModel>,
	onClearSelection: () -> Unit,
	onSelectAll: () -> Unit,
	onPrioritize: () -> Unit,
	onPause: () -> Unit,
	onResume: () -> Unit,
	onCancel: () -> Unit,
	onRemove: () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	val canPause = selectedItems.isNotEmpty() && selectedItems.all { it.canPause }
	val canResume = selectedItems.isNotEmpty() && selectedItems.all { it.canResume }
	val canPrioritize = selectedItems.any { it.canPrioritize }
	val canCancel = selectedItems.any { !it.workState.isFinished }
	val canRemove = selectedItems.isNotEmpty() && selectedItems.all { it.workState.isFinished }

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Surface(
			shape = RoundedCornerShape(50),
			color = colors.primaryContainer,
			contentColor = colors.onPrimaryContainer,
		) {
			Row(
				modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				IconButton(
					onClick = onClearSelection,
					modifier = Modifier.size(32.dp),
				) {
					Icon(
						painterResource(R.drawable.ic_close),
						contentDescription = stringResource(android.R.string.cancel),
						modifier = Modifier.size(18.dp),
					)
				}
				Text(
					text = selectedCount.toString(),
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
				)
			}
		}

		Spacer(Modifier.weight(1f))

		Row(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (canPrioritize) {
				FilledTonalIconButton(
					onClick = onPrioritize,
					colors = IconButtonDefaults.filledTonalIconButtonColors(
						containerColor = colors.surfaceContainerHigh,
						contentColor = colors.onSurface,
					),
				) {
					Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.download_prioritize))
				}
			}
			if (canPause) {
				FilledTonalIconButton(
					onClick = onPause,
					colors = IconButtonDefaults.filledTonalIconButtonColors(
						containerColor = colors.surfaceContainerHigh,
						contentColor = colors.onSurface,
					),
				) {
					Icon(painterResource(R.drawable.ic_pause), contentDescription = stringResource(R.string.pause))
				}
			}
			if (canResume) {
				FilledTonalIconButton(
					onClick = onResume,
					colors = IconButtonDefaults.filledTonalIconButtonColors(
						containerColor = colors.primaryContainer,
						contentColor = colors.onPrimaryContainer,
					),
				) {
					Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.resume))
				}
			}
			if (canCancel) {
				FilledTonalIconButton(
					onClick = onCancel,
					colors = IconButtonDefaults.filledTonalIconButtonColors(
						containerColor = colors.errorContainer,
						contentColor = colors.onErrorContainer,
					),
				) {
					Icon(painterResource(R.drawable.ic_cancel_multiple), contentDescription = stringResource(android.R.string.cancel))
				}
			}
			if (canRemove) {
				FilledTonalIconButton(
					onClick = onRemove,
					colors = IconButtonDefaults.filledTonalIconButtonColors(
						containerColor = colors.errorContainer,
						contentColor = colors.onErrorContainer,
					),
				) {
					Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.remove))
				}
			}
			FilledTonalIconButton(
				onClick = onSelectAll,
				colors = IconButtonDefaults.filledTonalIconButtonColors(
					containerColor = colors.surfaceContainerHigh,
					contentColor = colors.onSurface,
				),
			) {
				Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(android.R.string.selectAll))
			}
		}
	}
}

@Composable
private fun SectionHeader(header: ListHeader) {
	val context = LocalContext.current
	val label = remember(header) {
		header.getText(context)?.toString().orEmpty()
	}
	if (label.isNotEmpty()) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.primary,
			modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadCard(
	item: DownloadItemModel,
	coil: ImageLoader,
	isSelected: Boolean,
	isSelectionMode: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onExpandClick: () -> Unit,
	onPauseClick: () -> Unit,
	onResumeClick: () -> Unit,
	onRetryClick: () -> Unit,
	onCancelClick: () -> Unit,
	onTogglePriority: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val colors = MaterialTheme.colorScheme
	val context = LocalContext.current
	val haptic = rememberHapticEffect()
	val chapters by item.chapters.collectAsState()
	val isRunning = item.workState == WorkInfo.State.RUNNING
	val isQueued = item.workState == WorkInfo.State.ENQUEUED || item.workState == WorkInfo.State.BLOCKED
	val isFailed = item.workState == WorkInfo.State.FAILED
	val isSucceeded = item.workState == WorkInfo.State.SUCCEEDED
	val isCancelled = item.workState == WorkInfo.State.CANCELLED
	val isCompact = item.workState.isFinished && !isFailed
	val hasInlineActions = !isSelectionMode && (item.canPause || item.canResume || item.canRetry || item.canPrioritize || !item.workState.isFinished)

	val statusText = when {
		isRunning && item.isPaused -> stringResource(R.string.paused)
		isRunning && item.isStuck -> stringResource(R.string.stuck)
		isRunning -> stringResource(R.string.manga_downloading_)
		isQueued -> stringResource(R.string.queued)
		isSucceeded -> stringResource(R.string.download_complete)
		isCancelled -> stringResource(R.string.canceled)
		isFailed -> item.error ?: stringResource(R.string.error)
		else -> ""
	}

	val chevronRotation by animateFloatAsState(
		targetValue = if (item.isExpanded) 180f else 0f,
		label = "chapterExpand",
	)

	val coverRequest = remember(item.manga?.id, item.manga?.coverUrl) {
		val manga = item.manga
		ImageRequest.Builder(context)
			.data(manga?.coverUrl)
			.mangaSourceExtra(manga?.source)
			.stableMangaCoverKey(manga, manga?.coverUrl)
			.build()
	}

	Surface(
		shape = RoundedCornerShape(if (isCompact) 20.dp else 24.dp),
		color = if (isSelected) colors.secondaryContainer else colors.surfaceContainer,
		border = if (isSelected) BorderStroke(1.5.dp, colors.primary) else null,
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(if (isCompact) 20.dp else 24.dp))
			.combinedClickable(
				onClick = onClick,
				onLongClick = {
					haptic(HapticEffect.CONFIRM)
					onLongClick()
				},
			),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = if (isCompact) 10.dp else 14.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Box(
					modifier = Modifier
						.size(
							width = if (isCompact) 44.dp else 56.dp,
							height = if (isCompact) 60.dp else 76.dp,
						)
						.clip(RoundedCornerShape(if (isCompact) 12.dp else 14.dp))
						.background(colors.surfaceContainerLow),
				) {
					AsyncImage(
						model = coverRequest,
						imageLoader = coil,
						contentDescription = item.manga?.title,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}

				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(3.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							text = item.manga?.title ?: stringResource(R.string.unknown),
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
							color = colors.onSurface,
							maxLines = if (isCompact) 1 else 2,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f, fill = false),
						)
						if (item.isHighPriority && !item.workState.isFinished) {
							Surface(
								shape = RoundedCornerShape(50),
								color = colors.tertiaryContainer,
								contentColor = colors.onTertiaryContainer,
							) {
								Text(
									text = stringResource(R.string.download_high_priority),
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.SemiBold,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
								)
							}
						}
					}

					Text(
						text = statusText,
						style = MaterialTheme.typography.bodySmall,
						color = if (isFailed) colors.error else colors.onSurfaceVariant,
						maxLines = if (isCompact) 1 else 2,
						overflow = TextOverflow.Ellipsis,
					)

					val etaText = remember(item.eta, item.workState, item.isPaused) { item.getEtaString()?.toString() }
					val metaParts = buildList {
						if (item.chaptersDownloaded > 0) {
							add(
								context.resources.getQuantityString(
									R.plurals.chapters,
									item.chaptersDownloaded,
									item.chaptersDownloaded,
								),
							)
						}
						if (!etaText.isNullOrEmpty()) add(etaText)
					}
					if (metaParts.isNotEmpty()) {
						Text(
							text = metaParts.joinToString(" • "),
							style = MaterialTheme.typography.labelSmall,
							color = colors.onSurfaceVariant,
						)
					}
				}

				if ((isRunning || isQueued) && item.max > 0) {
					Surface(
						shape = RoundedCornerShape(50),
						color = if (item.canPause) colors.primaryContainer else colors.surfaceContainerHigh,
						contentColor = if (item.canPause) colors.onPrimaryContainer else colors.onSurfaceVariant,
					) {
						Text(
							text = "${(item.percent * 100f).roundToInt()}%",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
						)
					}
				} else if (!hasInlineActions && !isSelectionMode && !chapters.isNullOrEmpty()) {
					ChapterExpandPill(
						downloaded = chapters!!.count { it.isDownloaded },
						total = chapters!!.size,
						chevronRotation = chevronRotation,
						onClick = onExpandClick,
					)
				}
			}

			if (isRunning || isQueued || isFailed) {
				Spacer(Modifier.height(10.dp))
				WavyProgressBar(
					progress = item.percent.coerceIn(0f, 1f),
					isIndeterminate = item.isIndeterminate && isRunning && !item.isPaused,
					isWavy = item.canPause,
					indicatorColor = if (isFailed) colors.error else colors.primary,
					trackColor = colors.surfaceContainerLowest,
					modifier = Modifier.fillMaxWidth(),
				)
			}

			if (hasInlineActions) {
				Spacer(Modifier.height(10.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (item.canPause) {
						FilledTonalButton(
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onPauseClick()
							},
							shape = RoundedCornerShape(50),
							contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						) {
							Icon(painterResource(R.drawable.ic_pause), contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(Modifier.width(6.dp))
							Text(stringResource(R.string.pause), style = MaterialTheme.typography.labelMedium)
						}
					}
					if (item.canResume) {
						Button(
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onResumeClick()
							},
							shape = RoundedCornerShape(50),
							contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						) {
							Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(Modifier.width(6.dp))
							Text(stringResource(R.string.resume), style = MaterialTheme.typography.labelMedium)
						}
					}
					if (item.canRetry) {
						FilledTonalButton(
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onRetryClick()
							},
							shape = RoundedCornerShape(50),
							colors = ButtonDefaults.filledTonalButtonColors(
								containerColor = colors.errorContainer,
								contentColor = colors.onErrorContainer,
							),
							contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						) {
							Icon(painterResource(R.drawable.ic_retry), contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(Modifier.width(6.dp))
							Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelMedium)
						}
					}
					if (item.canPrioritize) {
						FilledTonalButton(
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onTogglePriority()
							},
							shape = RoundedCornerShape(50),
							colors = ButtonDefaults.filledTonalButtonColors(
								containerColor = if (item.isHighPriority) colors.tertiaryContainer else colors.surfaceContainerHigh,
								contentColor = if (item.isHighPriority) colors.onTertiaryContainer else colors.onSurface,
							),
							contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						) {
							Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(Modifier.width(4.dp))
							Text(
								text = stringResource(
									if (item.isHighPriority) R.string.download_normal_priority else R.string.download_prioritize,
								),
								style = MaterialTheme.typography.labelMedium,
							)
						}
					}
					if (!item.workState.isFinished) {
						IconButton(
							onClick = {
								haptic(HapticEffect.CONFIRM)
								onCancelClick()
							},
							modifier = Modifier.size(34.dp),
						) {
							Icon(
								painterResource(R.drawable.ic_cancel_multiple),
								contentDescription = stringResource(android.R.string.cancel),
								tint = colors.onSurfaceVariant,
								modifier = Modifier.size(18.dp),
							)
						}
					}
					Spacer(Modifier.weight(1f))
					if (!chapters.isNullOrEmpty()) {
						ChapterExpandPill(
							downloaded = chapters!!.count { it.isDownloaded },
							total = chapters!!.size,
							chevronRotation = chevronRotation,
							onClick = onExpandClick,
						)
					}
				}
			}

			AnimatedVisibility(
				visible = item.isExpanded && !chapters.isNullOrEmpty(),
				enter = expandVertically(
					animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
				) + fadeIn(animationSpec = tween(durationMillis = 180)),
				exit = shrinkVertically(
					animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
				) + fadeOut(animationSpec = tween(durationMillis = 140)),
			) {
				val chapterList = chapters.orEmpty()
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 10.dp)
						.heightIn(max = 230.dp)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					chapterList.forEachIndexed { index, ch ->
						ChapterRow(
							chapter = ch,
							index = index,
							count = chapterList.size,
							seed = item.id.mostSignificantBits,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ChapterExpandPill(
	downloaded: Int,
	total: Int,
	chevronRotation: Float,
	onClick: () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	Surface(
		shape = RoundedCornerShape(50),
		color = colors.surfaceContainerHigh,
		modifier = Modifier
			.clip(RoundedCornerShape(50))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = "$downloaded/$total",
				style = MaterialTheme.typography.labelSmall,
				color = colors.onSurfaceVariant,
			)
			Icon(
				painter = painterResource(R.drawable.ic_expand_more),
				contentDescription = stringResource(R.string.expand),
				tint = colors.onSurfaceVariant,
				modifier = Modifier
					.size(16.dp)
					.graphicsLayer { rotationZ = chevronRotation },
			)
		}
	}
}

private val m3eBadgeShapes: List<Shape> = listOf(
	CircleShape,
	RoundedCornerShape(8.dp),
	polarShape { t -> 0.84f + 0.16f * abs(cos(2f * (t - PI.toFloat() / 4f))).pow(1.35f) }, // Clover4
	polarShape { t -> 0.85f + 0.15f * cos(6f * t) }, // Flower6
	polarShape { t -> 0.83f + 0.17f * abs(cos(4f * t)).pow(1.5f) }, // SoftBurst
)

private fun polarShape(rNorm: (Float) -> Float): Shape = GenericShape { size, _ ->
	val cx = size.width / 2f
	val cy = size.height / 2f
	val maxR = min(cx, cy)
	val steps = 48
	for (i in 0 until steps) {
		val t = (2.0 * PI * i / steps).toFloat()
		val r = maxR * rNorm(t)
		val x = cx + r * cos(t)
		val y = cy + r * sin(t)
		if (i == 0) moveTo(x, y) else lineTo(x, y)
	}
	close()
}

private fun badgeShapeFor(seed: Long, chapterNumber: String?, index: Int): Shape {
	var h = seed xor (chapterNumber.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor (index.toLong() * 0xBF58476D1CE4E5B9uL.toLong())
	h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L
	h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
	h = h xor (h ushr 31)
	val idx = ((h and 0x7FFFFFFFL) % m3eBadgeShapes.size).toInt()
	return m3eBadgeShapes[idx]
}

@Composable
private fun ChapterRow(
	chapter: DownloadChapter,
	index: Int,
	count: Int,
	seed: Long,
) {
	val colors = MaterialTheme.colorScheme
	val badgeShape = remember(seed, chapter.number, index) {
		badgeShapeFor(seed, chapter.number, index)
	}
	Surface(
		shape = expressiveGroupItemShape(index, count),
		color = colors.surfaceContainerLowest,
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Surface(
				shape = badgeShape,
				color = if (chapter.isDownloaded) colors.primary else colors.surfaceContainerHigh,
				contentColor = if (chapter.isDownloaded) colors.onPrimary else colors.onSurfaceVariant,
				modifier = Modifier.defaultMinSize(minWidth = 30.dp, minHeight = 30.dp),
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
				) {
					Text(
						text = chapter.number.orEmpty(),
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
					)
				}
			}
			Text(
				text = chapter.name?.takeIf { it.isNotBlank() }
					?: stringResource(R.string.chapter_number, chapter.number.orEmpty()),
				style = MaterialTheme.typography.bodySmall,
				color = if (chapter.isDownloaded) colors.onSurface else colors.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			if (chapter.isDownloaded) {
				Icon(
					painter = painterResource(R.drawable.ic_check),
					contentDescription = null,
					tint = colors.primary,
					modifier = Modifier.size(16.dp),
				)
			}
		}
	}
}

@Composable
private fun ExpressiveEmptyDownloadsState(
	isFiltered: Boolean,
	onShowAll: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp, vertical = 56.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = colors.primaryContainer,
			contentColor = colors.onPrimaryContainer,
			modifier = Modifier.size(84.dp),
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(
					painter = painterResource(R.drawable.ic_download),
					contentDescription = null,
					modifier = Modifier.size(38.dp),
				)
			}
		}
		Text(
			text = stringResource(
				if (isFiltered) R.string.download_empty_filter else R.string.text_downloads_list_holder,
			),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			color = colors.onSurface,
			textAlign = TextAlign.Center,
		)
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			if (isFiltered) {
				Button(
					onClick = onShowAll,
					shape = RoundedCornerShape(50),
				) {
					Text(stringResource(R.string.download_filter_all))
				}
			}
			FilledTonalButton(
				onClick = onOpenSettings,
				shape = RoundedCornerShape(50),
			) {
				Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp))
				Text(stringResource(R.string.download_settings_action))
			}
		}
	}
}

@Composable
private fun WavyProgressBar(
	progress: Float,
	isIndeterminate: Boolean,
	isWavy: Boolean,
	indicatorColor: Color,
	trackColor: Color,
	modifier: Modifier = Modifier,
) {
	val transition = rememberInfiniteTransition(label = "wavyProgress")
	val phase by transition.animateFloat(
		initialValue = 0f,
		targetValue = (2.0 * PI).toFloat(),
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1100, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
		),
		label = "wavePhase",
	)
	val indeterminateOffset by transition.animateFloat(
		initialValue = -0.35f,
		targetValue = 1.0f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1300, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
		),
		label = "indeterminateOffset",
	)

	Canvas(
		modifier = modifier.height(10.dp),
	) {
		val strokePx = 4.dp.toPx()
		val cy = size.height / 2f
		val width = size.width
		if (width <= 0f) return@Canvas

		val startX: Float
		val endX: Float
		if (isIndeterminate) {
			startX = (indeterminateOffset * width).coerceIn(0f, width)
			endX = ((indeterminateOffset + 0.35f) * width).coerceIn(0f, width)
		} else {
			startX = 0f
			endX = (progress.coerceIn(0f, 1f) * width)
		}

		// Background track
		if (endX < width) {
			val trackStart = if (endX > 0f) (endX + 4.dp.toPx()).coerceAtMost(width) else 0f
			if (trackStart < width) {
				drawLine(
					color = trackColor,
					start = Offset(trackStart, cy),
					end = Offset(width, cy),
					strokeWidth = strokePx,
					cap = StrokeCap.Round,
				)
			}
		}
		if (isIndeterminate && startX > 4.dp.toPx()) {
			drawLine(
				color = trackColor,
				start = Offset(0f, cy),
				end = Offset(startX - 4.dp.toPx(), cy),
				strokeWidth = strokePx,
				cap = StrokeCap.Round,
			)
		}

		// Active indicator (wavy when active, flat when paused/failed)
		if (endX - startX > 1f) {
			if (isWavy) {
				val amplitude = 2.5.dp.toPx()
				val wavelength = 20.dp.toPx()
				val path = Path()
				var x = startX
				var first = true
				val step = 2.5f
				while (x <= endX) {
					val y = cy + amplitude * sin((x / wavelength) * (2f * PI.toFloat()) - phase)
					if (first) {
						path.moveTo(x, y)
						first = false
					} else {
						path.lineTo(x, y)
					}
					x += step
				}
				val endY = cy + amplitude * sin((endX / wavelength) * (2f * PI.toFloat()) - phase)
				path.lineTo(endX, endY)
				drawPath(
					path = path,
					color = indicatorColor,
					style = Stroke(width = strokePx, cap = StrokeCap.Round),
				)
			} else {
				drawLine(
					color = indicatorColor,
					start = Offset(startX, cy),
					end = Offset(endX, cy),
					strokeWidth = strokePx,
					cap = StrokeCap.Round,
				)
			}
		}
	}
}
