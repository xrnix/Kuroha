package org.koitharu.kotatsu.stats.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.stats.domain.ReadingStats
import org.koitharu.kotatsu.stats.domain.StatsBucket
import org.koitharu.kotatsu.stats.domain.StatsBucketUnit
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.koitharu.kotatsu.stats.domain.StatsRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.NavigableMap
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class StatsRepository @Inject constructor(
	private val settings: AppSettings,
	private val db: MangaDatabase,
) {

	suspend fun getStatsSnapshot(period: StatsPeriod, categories: Set<Long>): ReadingStats {
		val zone = ZoneId.systemDefault()
		val boundedStarts = if (period == StatsPeriod.ALL) null else bucketStarts(period, zone, 0L)
		val fromDate = boundedStarts?.second?.first() ?: 0L
		val sessions = db.getStatsDao().getSessions(fromDate, categories)
		val (unit, starts) = boundedStarts
			?: bucketStarts(period, zone, sessions.firstOrNull()?.startedAt ?: System.currentTimeMillis())
		val durations = LongArray(starts.size)
		val activeDays = HashSet<LocalDate>()
		val perManga = HashMap<Long, MangaAggregate>()
		var total = 0L
		var chapterDuration = 0L
		var chapters = 0
		var pages = 0
		for (session in sessions) {
			total += session.duration
			chapters += session.chapters
			pages += session.pages
			if (session.chapters > 0) {
				chapterDuration += session.duration
			}
			val day = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
			activeDays += day
			perManga.getOrPut(session.mangaId) { MangaAggregate() }.add(session, day)
			val index = starts.binarySearch(session.startedAt).let { if (it >= 0) it else -it - 2 }
			if (index in durations.indices) {
				durations[index] += session.duration
			}
		}
		val (records, others) = buildRecords(fromDate, categories, perManga, total)
		val allSessions = if (fromDate == 0L) sessions else db.getStatsDao().getSessions(0L, categories)
		val (currentStreak, longestStreak) = calculateStreaks(allSessions, zone)
		return ReadingStats(
			period = period,
			records = records,
			otherRecords = others,
			buckets = starts.mapIndexed { i, start -> StatsBucket(start, durations[i]) },
			bucketUnit = unit,
			totalDuration = total,
			chapterDuration = chapterDuration,
			chapters = chapters,
			pages = pages,
			activeDays = activeDays.size,
			currentStreak = currentStreak,
			longestStreak = longestStreak,
		)
	}

	private suspend fun buildRecords(
		fromDate: Long,
		categories: Set<Long>,
		perManga: Map<Long, MangaAggregate>,
		total: Long,
	): Pair<List<StatsRecord>, List<StatsRecord>> {
		if (perManga.isEmpty()) {
			return emptyList<StatsRecord>() to emptyList()
		}
		val entities = db.getStatsDao().getDurationStats(fromDate, categories)
		val result = ArrayList<StatsRecord>(entities.size)
		val others = ArrayList<StatsRecord>()
		var otherDuration = 0L
		var otherPages = 0
		var otherChapters = 0
		for ((entity, duration) in entities) {
			val aggregate = perManga[entity.id]
			val record = StatsRecord(
				manga = entity.toManga(emptySet(), null),
				duration = duration,
				pages = aggregate?.pages ?: 0,
				chapters = aggregate?.chapters ?: 0,
				daysRead = aggregate?.days?.size ?: 0,
				firstReadAt = aggregate?.firstReadAt ?: 0L,
			)
			val isTooSmall = total > 0L && duration.toDouble() / total < 0.01
			if (isTooSmall || result.size >= 10) {
				others += record
				otherDuration += duration
				otherPages += record.pages
				otherChapters += record.chapters
			} else {
				result += record
			}
		}
		if (otherDuration != 0L) {
			result += StatsRecord(
				manga = null,
				duration = otherDuration,
				pages = otherPages,
				chapters = otherChapters,
				daysRead = 0,
				firstReadAt = others.minOfOrNull { it.firstReadAt } ?: 0L,
			)
		}
		return result to others
	}

	private fun bucketStarts(
		period: StatsPeriod,
		zone: ZoneId,
		firstSessionAt: Long,
	): Pair<StatsBucketUnit, LongArray> {
		val now = ZonedDateTime.now(zone)
		fun starts(count: Int, unit: StatsBucketUnit, anchor: ZonedDateTime, step: (ZonedDateTime, Long) -> ZonedDateTime) =
			unit to LongArray(count) { i -> step(anchor, -(count - 1L - i)).toInstant().toEpochMilli() }
		return when (period) {
			StatsPeriod.DAY -> starts(24, StatsBucketUnit.HOUR, now.truncatedTo(ChronoUnit.HOURS)) { a, d -> a.plusHours(d) }
			StatsPeriod.WEEK -> starts(7, StatsBucketUnit.DAY, now.toLocalDate().atStartOfDay(zone)) { a, d -> a.plusDays(d) }
			StatsPeriod.MONTH -> starts(30, StatsBucketUnit.DAY, now.toLocalDate().atStartOfDay(zone)) { a, d -> a.plusDays(d) }
			StatsPeriod.MONTHS_3 -> starts(
				13,
				StatsBucketUnit.WEEK,
				now.toLocalDate().with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1).atStartOfDay(zone),
			) { a, d -> a.plusWeeks(d) }
			StatsPeriod.YEAR -> starts(
				12,
				StatsBucketUnit.MONTH,
				now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone),
			) { a, d -> a.plusMonths(d) }
			StatsPeriod.ALL -> {
				val firstMonth = Instant.ofEpochMilli(firstSessionAt)
					.atZone(zone).toLocalDate().withDayOfMonth(1)
				val thisMonth = now.toLocalDate().withDayOfMonth(1)
				val months = ChronoUnit.MONTHS.between(firstMonth, thisMonth).toInt() + 1
				starts(
					months.coerceAtLeast(1),
					StatsBucketUnit.MONTH,
					thisMonth.atStartOfDay(zone),
				) { a, d -> a.plusMonths(d) }
			}
		}
	}

	private fun calculateStreaks(sessions: List<StatsEntity>, zone: ZoneId): Pair<Int, Int> {
		val days = sessions
			.mapTo(TreeSet()) { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
		if (days.isEmpty()) return 0 to 0
		var longest = 0
		var run = 0
		var previous: LocalDate? = null
		for (day in days) {
			run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
			if (run > longest) longest = run
			previous = day
		}
		val today = LocalDate.now(zone)
		val current = if (days.last() == today || days.last() == today.minusDays(1)) run else 0
		return current to longest
	}

	private class MangaAggregate {
		var pages: Int = 0
			private set
		var chapters: Int = 0
			private set
		var firstReadAt: Long = Long.MAX_VALUE
			private set
		val days = HashSet<LocalDate>()

		fun add(session: StatsEntity, day: LocalDate) {
			pages += session.pages
			chapters += session.chapters
			if (session.startedAt < firstReadAt) {
				firstReadAt = session.startedAt
			}
			days += day
		}
	}

	suspend fun getReadingStats(period: StatsPeriod, categories: Set<Long>): List<StatsRecord> {
		val fromDate = if (period == StatsPeriod.ALL) {
			0L
		} else {
			System.currentTimeMillis() - TimeUnit.DAYS.toMillis(period.days.toLong())
		}
		val stats = db.getStatsDao().getDurationStats(fromDate, null, categories)
		val result = ArrayList<StatsRecord>(stats.size)
		var other = StatsRecord(null, 0)
		val total = stats.values.sum()
		for ((mangaEntity, duration) in stats) {
			val manga = mangaEntity.toManga(emptySet(), null)
			val percent = duration.toDouble() / total
			if (percent < 0.05) {
				other = other.copy(duration = other.duration + duration)
			} else {
				result += StatsRecord(
					manga = manga,
					duration = duration,
				)
			}
		}
		if (other.duration != 0L) {
			result += other
		}
		return result
	}

	suspend fun getTimePerPage(mangaId: Long): Long = db.withTransaction {
		val dao = db.getStatsDao()
		val pages = dao.getReadPagesCount(mangaId)
		val time = if (pages >= 10) {
			dao.getAverageTimePerPage(mangaId)
		} else {
			dao.getAverageTimePerPage()
		}
		time
	}

	suspend fun getTotalPagesRead(mangaId: Long): Int {
		return db.getStatsDao().getReadPagesCount(mangaId)
	}

	suspend fun getMangaTimeline(mangaId: Long): NavigableMap<Long, Int> {
		val entities = db.getStatsDao().findAll(mangaId)
		val map = TreeMap<Long, Int>()
		for (e in entities) {
			map[e.startedAt] = e.pages
		}
		return map
	}

	suspend fun clearStats() {
		db.getStatsDao().clear()
	}

	fun observeHasStats(mangaId: Long): Flow<Boolean> = settings.observeAsFlow(AppSettings.KEY_STATS_ENABLED) {
		isStatsEnabled
	}.flatMapLatest { isEnabled ->
		if (isEnabled) {
			db.getStatsDao().observeRowCount(mangaId).map { it > 0 }
		} else {
			flowOf(false)
		}
	}.distinctUntilChanged()
}
