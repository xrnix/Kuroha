package org.koitharu.kotatsu.details.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.list.AdapterDelegateClickListenerAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.drawableStart
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.getThemeColorStateList
import org.koitharu.kotatsu.core.util.ext.textAndVisible
import org.koitharu.kotatsu.databinding.ItemChapterBinding
import org.koitharu.kotatsu.details.ui.model.ChapterListItem
import org.koitharu.kotatsu.list.ui.model.ListModel
import com.google.android.material.R as materialR

fun chapterListItemAD(
	clickListener: OnListItemClickListener<ChapterListItem>,
	accentColorProvider: () -> Int? = { null },
) = adapterDelegateViewBinding<ChapterListItem, ListModel, ItemChapterBinding>(
	viewBinding = { inflater, parent -> ItemChapterBinding.inflate(inflater, parent, false) },
	on = { item, _, _ -> item is ChapterListItem && !item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	itemView.isFocusable = false
	itemView.isFocusableInTouchMode = false
	val density = context.resources.displayMetrics.density
	val currentElevation = 2f * density
	val colorPrimary = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
	val colorPrimaryList = context.getThemeColorStateList(androidx.appcompat.R.attr.colorPrimary)
	val colorContainerCurrent = context.getThemeColor(materialR.attr.colorSurfaceContainerHighest)
	val colorContainerUnread = context.getThemeColor(materialR.attr.colorSurfaceContainer)
	val colorContainerRead = context.getThemeColor(materialR.attr.colorSurfaceContainerLow)
	val textColorPrimary = context.getThemeColorStateList(android.R.attr.textColorPrimary)
	val textColorSecondary = context.getThemeColorStateList(materialR.attr.colorOnSurfaceVariant)
	val textColorHint = context.getThemeColorStateList(android.R.attr.textColorHint)
	val iconNew = ContextCompat.getDrawable(context, R.drawable.ic_new)
	val currentIndicatorDrawable = GradientDrawable().apply {
		shape = GradientDrawable.RECTANGLE
		cornerRadius = 2f * density
	}
	binding.root.strokeWidth = 0

	var lastVisualState = -1
	var lastCurrentAccent = 0
	var lastBookmarkAccent: Int? = null
	var hasBookmarkTint = false

	bind {
		binding.textViewTitle.text = item.getTitle(context.resources)
		binding.textViewDescription.textAndVisible = item.description
		val customAccent = accentColorProvider()
		when {
			item.isCurrent -> {
				val accent = customAccent ?: colorPrimary
				if (lastVisualState != 0 || lastCurrentAccent != accent) {
					lastVisualState = 0
					lastCurrentAccent = accent
					binding.root.setCardBackgroundColor(colorContainerCurrent)
					binding.root.cardElevation = currentElevation
					currentIndicatorDrawable.setColor(accent)
					if (binding.viewCurrentIndicator.background !== currentIndicatorDrawable) {
						binding.viewCurrentIndicator.background = currentIndicatorDrawable
					}
					binding.viewCurrentIndicator.isVisible = true
					if (binding.textViewTitle.drawableStart != null) {
						binding.textViewTitle.drawableStart = null
					}
					binding.textViewTitle.setTextColor(accent)
					binding.textViewDescription.setTextColor(accent)
					binding.textViewTitle.typeface = Typeface.DEFAULT_BOLD
					binding.textViewDescription.typeface = Typeface.DEFAULT_BOLD
					binding.textViewTitle.textSize = 16.5f
				}
			}

			item.isUnread -> {
				if (lastVisualState != 1) {
					lastVisualState = 1
					binding.root.setCardBackgroundColor(colorContainerUnread)
					binding.root.cardElevation = 0f
					binding.viewCurrentIndicator.background = null
					binding.viewCurrentIndicator.isVisible = false
					binding.textViewTitle.setTextColor(textColorPrimary)
					binding.textViewDescription.setTextColor(textColorSecondary)
					binding.textViewTitle.typeface = Typeface.DEFAULT
					binding.textViewDescription.typeface = Typeface.DEFAULT
					binding.textViewTitle.textSize = 15.5f
				}
				val targetStart = if (item.isNew) iconNew else null
				if (binding.textViewTitle.drawableStart !== targetStart) {
					binding.textViewTitle.drawableStart = targetStart
				}
			}

			else -> {
				if (lastVisualState != 2) {
					lastVisualState = 2
					binding.root.setCardBackgroundColor(colorContainerRead)
					binding.root.cardElevation = 0f
					binding.viewCurrentIndicator.background = null
					binding.viewCurrentIndicator.isVisible = false
					if (binding.textViewTitle.drawableStart != null) {
						binding.textViewTitle.drawableStart = null
					}
					binding.textViewTitle.setTextColor(textColorHint)
					binding.textViewDescription.setTextColor(textColorHint)
					binding.textViewTitle.typeface = Typeface.DEFAULT
					binding.textViewDescription.typeface = Typeface.DEFAULT
					binding.textViewTitle.textSize = 15.5f
				}
			}
		}
		binding.imageViewBookmarked.isVisible = item.isBookmarked
		if (item.isBookmarked && (!hasBookmarkTint || lastBookmarkAccent != customAccent)) {
			hasBookmarkTint = true
			lastBookmarkAccent = customAccent
			binding.imageViewBookmarked.imageTintList = customAccent?.let { ColorStateList.valueOf(it) }
				?: colorPrimaryList
		}
		binding.imageViewDownloaded.isVisible = item.isDownloaded
	}
}
