package org.koitharu.kotatsu.download.ui.worker

import android.os.Parcelable
import androidx.work.Data
import kotlinx.parcelize.Parcelize
import org.koitharu.kotatsu.core.prefs.DownloadFormat
import org.koitharu.kotatsu.parsers.util.find
import java.io.File

@Parcelize
class DownloadTask(
	val mangaId: Long,
	val isPaused: Boolean,
	val isSilent: Boolean,
	val chaptersIds: LongArray?,
	val destination: File?,
	val format: DownloadFormat?,
	val allowMeteredNetwork: Boolean,
	val priority: Int = PRIORITY_NORMAL,
) : Parcelable {

	constructor(data: Data) : this(
		mangaId = data.getLong(MANGA_ID, 0L),
		isPaused = data.getBoolean(START_PAUSED, false),
		isSilent = data.getBoolean(IS_SILENT, false),
		chaptersIds = data.getLongArray(CHAPTERS)?.takeUnless(LongArray::isEmpty),
		destination = data.getString(DESTINATION)?.let { File(it) },
		format = data.getString(FORMAT)?.let { DownloadFormat.entries.find(it) },
		allowMeteredNetwork = data.getBoolean(ALLOW_METERED, true),
		priority = data.getInt(PRIORITY, PRIORITY_NORMAL),
	)

	fun toData(): Data = Data.Builder()
		.putLong(MANGA_ID, mangaId)
		.putBoolean(START_PAUSED, isPaused)
		.putBoolean(IS_SILENT, isSilent)
		.putLongArray(CHAPTERS, chaptersIds ?: LongArray(0))
		.putString(DESTINATION, destination?.path)
		.putString(FORMAT, format?.name)
		.putBoolean(ALLOW_METERED, allowMeteredNetwork)
		.putInt(PRIORITY, priority)
		.build()

	fun copy(
		isPaused: Boolean = this.isPaused,
		priority: Int = this.priority,
	) = DownloadTask(
		mangaId = mangaId,
		isPaused = isPaused,
		isSilent = isSilent,
		chaptersIds = chaptersIds,
		destination = destination,
		format = format,
		allowMeteredNetwork = allowMeteredNetwork,
		priority = priority,
	)

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as DownloadTask

		if (mangaId != other.mangaId) return false
		if (isPaused != other.isPaused) return false
		if (isSilent != other.isSilent) return false
		if (!(chaptersIds contentEquals other.chaptersIds)) return false
		if (destination != other.destination) return false
		if (format != other.format) return false
		if (allowMeteredNetwork != other.allowMeteredNetwork) return false
		if (priority != other.priority) return false

		return true
	}

	override fun hashCode(): Int {
		var result = mangaId.hashCode()
		result = 31 * result + isPaused.hashCode()
		result = 31 * result + isSilent.hashCode()
		result = 31 * result + (chaptersIds?.contentHashCode() ?: 0)
		result = 31 * result + (destination?.hashCode() ?: 0)
		result = 31 * result + (format?.hashCode() ?: 0)
		result = 31 * result + allowMeteredNetwork.hashCode()
		result = 31 * result + priority
		return result
	}

	companion object {

		const val PRIORITY_NORMAL = 0
		const val PRIORITY_HIGH = 1

		private const val MANGA_ID = "manga_id"
		private const val IS_SILENT = "silent"
		private const val START_PAUSED = "paused"
		private const val CHAPTERS = "chapters"
		private const val DESTINATION = "dest"
		private const val FORMAT = "format"
		private const val ALLOW_METERED = "metered"
		private const val PRIORITY = "priority"
	}
}
