package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.koitharu.kotatsu.sync.data.db.SyncTriggers

/** Migration from DB 28 to 29: add enhanced fields and Google Drive sync change tracking */
class Migration28To29 : Migration(28, 29) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.safeAddColumn("stats", "chapters", "INTEGER NOT NULL DEFAULT 0")
		db.safeAddColumn("favourite_categories", "download_new_chapters", "INTEGER NOT NULL DEFAULT 0")
		db.safeAddColumn("track_logs", "chapter_ids", "TEXT NOT NULL DEFAULT ''")
		db.safeAddColumn("preferences", "description_override", "TEXT")
		db.safeAddColumn("preferences", "merge_scanlators", "INTEGER NOT NULL DEFAULT 0")
		db.safeAddColumn("preferences", "merged_scanlators", "TEXT")

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS `sync_rows` (`tbl` TEXT NOT NULL, `pk` TEXT NOT NULL, `v` INTEGER NOT NULL, " +
				"`deleted` INTEGER NOT NULL, `sync_key` TEXT, PRIMARY KEY(`tbl`, `pk`))",
		)
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS `sync_prefs` (`file` TEXT NOT NULL, `key` TEXT NOT NULL, `hash` INTEGER, " +
				"`v` INTEGER NOT NULL, PRIMARY KEY(`file`, `key`))",
		)
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS `sync_remote` (`file_id` TEXT NOT NULL, `md5` TEXT NOT NULL, PRIMARY KEY(`file_id`))",
		)
		SyncTriggers.backfill(db)
	}
}
