package org.koitharu.kotatsu.core.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

fun SupportSQLiteDatabase.safeAddColumn(table: String, column: String, definition: String) {
	var columnExists = false
	try {
		query("PRAGMA table_info(`$table`)").use { cursor ->
			val nameIndex = cursor.getColumnIndex("name")
			while (cursor.moveToNext()) {
				if (nameIndex != -1 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
					columnExists = true
					break
				}
			}
		}
	} catch (_: Throwable) {
	}

	if (!columnExists) {
		try {
			execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
		} catch (e: Throwable) {
			val msg = e.message.orEmpty()
			if (!msg.contains("duplicate column", ignoreCase = true) && !msg.contains("already exists", ignoreCase = true)) {
				throw e
			}
		}
	}
}
