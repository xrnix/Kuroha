package org.koitharu.kotatsu.history.ui

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.viewbinding.ViewBinding
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.settings.compose.KurohaTheme
import java.util.concurrent.TimeUnit

private class ComposeViewBinding(val composeView: ComposeView) : ViewBinding {
	override fun getRoot() = composeView
}

fun homeHeaderAD(
	onStatsClick: () -> Unit,
) = adapterDelegateViewBinding<HomeHeaderModel, ListModel, ComposeViewBinding>(
	viewBinding = { _, parent ->
		ComposeViewBinding(
			ComposeView(parent.context).apply {
				layoutParams = ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT,
				)
				setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			},
		)
	},
) {
	var state by mutableStateOf<HomeHeaderModel?>(null)
	binding.composeView.setContent {
		KurohaTheme {
			state?.let { model ->
				HomeHeaderRow(
					model = model,
					onStatsClick = onStatsClick,
				)
			}
		}
	}
	bind {
		state = item
	}
}

@Composable
private fun HomeHeaderRow(
	model: HomeHeaderModel,
	onStatsClick: () -> Unit,
) {
	if (!model.isStatsVisible) return
	val minutesToday = TimeUnit.MILLISECONDS.toMinutes(model.todayDurationMs).toInt()
	val label = buildList {
		if (model.streakDays > 0) add(stringResource(R.string.home_streak_days, model.streakDays))
		if (minutesToday > 0) add(stringResource(R.string.home_today_minutes, minutesToday))
	}.joinToString(" · ").ifEmpty { stringResource(R.string.home_start_reading) }

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.surfaceContainerHigh,
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.clip(CircleShape)
				.clickable(onClick = onStatsClick),
		) {
			Row(
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(
					painter = painterResource(R.drawable.ic_bolt),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(16.dp),
				)
				Spacer(Modifier.width(6.dp))
				Text(
					text = label,
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Medium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}
