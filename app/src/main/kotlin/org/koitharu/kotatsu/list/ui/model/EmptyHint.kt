package org.koitharu.kotatsu.list.ui.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class EmptyHint(
	@DrawableRes val icon: Int,
	@StringRes val textPrimary: Int,
	@StringRes val textSecondary: Int,
	@StringRes val actionStringRes: Int,
	@StringRes val secondaryActionStringRes: Int = 0,
) : ListModel {

	fun toState() = EmptyState(icon, textPrimary, textSecondary, actionStringRes)

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is EmptyHint && textPrimary == other.textPrimary
	}
}
