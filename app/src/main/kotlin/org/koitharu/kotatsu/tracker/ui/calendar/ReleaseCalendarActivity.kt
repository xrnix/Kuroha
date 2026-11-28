package org.koitharu.kotatsu.tracker.ui.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import kotlin.math.abs

@AndroidEntryPoint
class ReleaseCalendarActivity : ComponentActivity() {

	private val viewModel by viewModels<ReleaseCalendarViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			KurohaTheme {
				val state by viewModel.uiState.collectAsState()
				ReleaseCalendarScreen(
					state = state,
					onBack = { finish() },
					onRefresh = viewModel::refresh,
					onMangaClick = { item ->
						startActivity(AppRouter.detailsIntent(this, item.entry.manga))
					},
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseCalendarScreen(
	state: ReleaseCalendarUiState,
	onBack: () -> Unit,
	onRefresh: () -> Unit,
	onMangaClick: (ReleaseCalendarItem) -> Unit,
) {
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(text = stringResource(R.string.release_calendar)) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(
							painter = painterResource(R.drawable.ic_arrow_back),
							contentDescription = null,
						)
					}
				},
				actions = {
					IconButton(onClick = onRefresh) {
						Icon(
							painter = painterResource(R.drawable.ic_sync),
							contentDescription = stringResource(R.string.update),
						)
					}
				},
			)
		},
	) { padding ->
		when {
			state.isLoading -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(padding),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator()
				}
			}

			state.sections.isEmpty() -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(padding)
						.padding(24.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = stringResource(R.string.release_calendar_empty),
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			else -> {
				LazyColumn(
					modifier = Modifier
						.fillMaxSize()
						.padding(padding),
					contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					ReleaseBucket.entries.forEach { bucket ->
						val items = state.sections[bucket].orEmpty()
						if (items.isNotEmpty()) {
							item(key = "header_${bucket.name}") {
								Text(
									text = bucketTitle(bucket),
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.SemiBold,
									color = if (bucket == ReleaseBucket.OVERDUE) {
										MaterialTheme.colorScheme.error
									} else {
										MaterialTheme.colorScheme.primary
									},
									modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
								)
							}
							items(
								items = items,
								key = { it.entry.manga.id },
							) { item ->
								ReleaseCalendarRow(
									item = item,
									onClick = { onMangaClick(item) },
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ReleaseCalendarRow(
	item: ReleaseCalendarItem,
	onClick: () -> Unit,
) {
	Card(
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
		),
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			AsyncImage(
				model = item.entry.manga.coverUrl,
				contentDescription = item.entry.manga.title,
				contentScale = ContentScale.Crop,
				modifier = Modifier
					.size(width = 52.dp, height = 74.dp)
					.clip(RoundedCornerShape(10.dp)),
			)
			Spacer(modifier = Modifier.width(12.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = item.entry.manga.title,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = expectedLabel(item.dayDelta),
					style = MaterialTheme.typography.bodyMedium,
					color = if (item.dayDelta < 0L) {
						MaterialTheme.colorScheme.error
					} else {
						MaterialTheme.colorScheme.primary
					},
				)
				Spacer(modifier = Modifier.height(6.dp))
				Surface(
					shape = RoundedCornerShape(8.dp),
					color = MaterialTheme.colorScheme.secondaryContainer,
				) {
					Text(
						text = stringResource(R.string.release_cadence_days, item.entry.medianGapDays.toInt()),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun bucketTitle(bucket: ReleaseBucket): String = when (bucket) {
	ReleaseBucket.OVERDUE -> stringResource(R.string.release_overdue)
	ReleaseBucket.TODAY -> stringResource(R.string.release_today)
	ReleaseBucket.TOMORROW -> stringResource(R.string.release_tomorrow)
	ReleaseBucket.THIS_WEEK -> stringResource(R.string.release_this_week)
	ReleaseBucket.LATER -> stringResource(R.string.release_later)
}

@Composable
private fun expectedLabel(deltaDays: Long): String = when {
	deltaDays < 0L -> stringResource(R.string.release_overdue_days, abs(deltaDays).toInt())
	deltaDays == 0L -> stringResource(R.string.release_expected_today)
	deltaDays == 1L -> stringResource(R.string.release_expected_tomorrow)
	else -> stringResource(R.string.release_expected_in_days, deltaDays.toInt())
}
