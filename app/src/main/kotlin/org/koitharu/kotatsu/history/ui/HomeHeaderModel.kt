package org.koitharu.kotatsu.history.ui

import org.koitharu.kotatsu.list.ui.model.ListModel

data class HomeHeaderModel(
	val todayDurationMs: Long,
	val streakDays: Int,
	val isStatsVisible: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is HomeHeaderModel
}
