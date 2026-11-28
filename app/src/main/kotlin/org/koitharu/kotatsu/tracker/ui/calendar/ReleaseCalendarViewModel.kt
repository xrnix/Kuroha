package org.koitharu.kotatsu.tracker.ui.calendar

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ReleaseCalendarEntry(
	val manga: Manga,
	val lastChapterDate: Long,
	val medianGapDays: Long,
	val nextExpectedDate: Long,
)

enum class ReleaseBucket {
	OVERDUE,
	TODAY,
	TOMORROW,
	THIS_WEEK,
	LATER,
}

data class ReleaseCalendarItem(
	val entry: ReleaseCalendarEntry,
	val bucket: ReleaseBucket,
	val dayDelta: Long,
)

data class ReleaseCalendarUiState(
	val isLoading: Boolean = true,
	val sections: Map<ReleaseBucket, List<ReleaseCalendarItem>> = emptyMap(),
)

@HiltViewModel
class ReleaseCalendarViewModel @Inject constructor(
	private val repository: TrackingRepository,
) : BaseViewModel() {

	private val _uiState = MutableStateFlow(ReleaseCalendarUiState())
	val uiState: StateFlow<ReleaseCalendarUiState> = _uiState.asStateFlow()

	init {
		refresh()
	}

	fun refresh() {
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.value = _uiState.value.copy(isLoading = true)
			val entries = runCatching { repository.getReleaseCalendarEntries() }.getOrDefault(emptyList())
			val zone = ZoneId.systemDefault()
			val today = LocalDate.now(zone)
			val grouped = entries.map { entry ->
				val targetDate = Instant.ofEpochMilli(entry.nextExpectedDate).atZone(zone).toLocalDate()
				val delta = ChronoUnit.DAYS.between(today, targetDate)
				val bucket = when {
					delta < 0L -> ReleaseBucket.OVERDUE
					delta == 0L -> ReleaseBucket.TODAY
					delta == 1L -> ReleaseBucket.TOMORROW
					delta <= 7L -> ReleaseBucket.THIS_WEEK
					else -> ReleaseBucket.LATER
				}
				ReleaseCalendarItem(entry = entry, bucket = bucket, dayDelta = delta)
			}.groupBy { it.bucket }
			_uiState.value = ReleaseCalendarUiState(
				isLoading = false,
				sections = grouped,
			)
		}
	}
}
