package org.koitharu.kotatsu.details.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Typeface
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.list.AdapterDelegateClickListenerAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.getThemeColorStateList
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.getThemeResId
import org.koitharu.kotatsu.core.util.ext.setTooltipCompat
import org.koitharu.kotatsu.databinding.ItemChapterGridBinding
import org.koitharu.kotatsu.details.ui.model.ChapterListItem
import org.koitharu.kotatsu.list.ui.model.ListModel
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

fun chapterGridItemAD(
	clickListener: OnListItemClickListener<ChapterListItem>,
) = adapterDelegateViewBinding<ChapterListItem, ListModel, ItemChapterGridBinding>(
	viewBinding = { inflater, parent -> ItemChapterGridBinding.inflate(inflater, parent, false) },
	on = { item, _, _ -> item is ChapterListItem && item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	val isExpressive = context.getThemeResId(R.attr.detailsChapterGridItemStyle, 0) ==
		R.style.Widget_Kotatsu_Details_ChapterGridCard_Expressive
	val titleTypeface = binding.textViewTitle.typeface

	val expressiveColors = if (isExpressive) {
		binding.imageViewNew.imageTintList = ColorStateList.valueOf(
			context.getThemeColor(appcompatR.attr.colorError),
		)
		val currentContent = context.getThemeColor(materialR.attr.colorOnPrimaryContainer)
		val newContent = context.getThemeColor(materialR.attr.colorOnSecondaryContainer)
		val unreadContent = context.getThemeColor(materialR.attr.colorOnSurface)
		val readContent = context.getThemeColor(materialR.attr.colorOnSurfaceVariant)
		arrayOf(
			Triple(context.getThemeColor(materialR.attr.colorPrimaryContainer), currentContent, ColorStateList.valueOf(currentContent)),
			Triple(context.getThemeColor(materialR.attr.colorSecondaryContainer), newContent, ColorStateList.valueOf(newContent)),
			Triple(context.getThemeColor(materialR.attr.colorSurfaceContainer), unreadContent, ColorStateList.valueOf(unreadContent)),
			Triple(context.getThemeColor(materialR.attr.colorSurfaceContainerLow), readContent, ColorStateList.valueOf(readContent)),
		)
	} else {
		null
	}
	val textColorPrimary = if (!isExpressive) context.getThemeColorStateList(android.R.attr.textColorPrimary) else null
	val textColorHint = if (!isExpressive) context.getThemeColorStateList(android.R.attr.textColorHint) else null

	var lastVisualState = -1

	bind { payloads ->
		if (payloads.isEmpty()) {
			binding.textViewTitle.text = item.chapter.numberString() ?: "?"
			itemView.setTooltipCompat(item.chapter.title)
		}
		binding.imageViewNew.isVisible = item.isNew
		binding.imageViewCurrent.isVisible = item.isCurrent
		binding.imageViewBookmarked.isVisible = item.isBookmarked
		binding.imageViewDownloaded.isVisible = item.isDownloaded

		if (expressiveColors != null) {
			val stateIndex = when {
				item.isCurrent -> 0
				item.isUnread && item.isNew -> 1
				item.isUnread -> 2
				else -> 3
			}
			val isBold = item.isCurrent || item.isNew
			val visualKey = (stateIndex shl 1) or (if (isBold) 1 else 0)
			if (lastVisualState != visualKey) {
				lastVisualState = visualKey
				val (containerColor, contentColor, tint) = expressiveColors[stateIndex]
				binding.root.setCardBackgroundColor(containerColor)
				binding.textViewTitle.setTextColor(contentColor)
				binding.textViewTitle.setTypeface(
					titleTypeface,
					if (isBold) Typeface.BOLD else Typeface.NORMAL,
				)
				binding.imageViewCurrent.imageTintList = tint
				binding.imageViewBookmarked.imageTintList = tint
				binding.imageViewDownloaded.imageTintList = tint
			}
		} else {
			val visualKey = when {
				item.isCurrent -> 0
				item.isUnread -> 1
				else -> 2
			}
			if (lastVisualState != visualKey) {
				lastVisualState = visualKey
				when (visualKey) {
					0 -> {
						binding.textViewTitle.setTextColor(textColorPrimary)
						binding.textViewTitle.setTypeface(titleTypeface, Typeface.BOLD)
					}

					1 -> {
						binding.textViewTitle.setTextColor(textColorPrimary)
						binding.textViewTitle.setTypeface(titleTypeface, Typeface.NORMAL)
					}

					else -> {
						binding.textViewTitle.setTextColor(textColorHint)
						binding.textViewTitle.setTypeface(titleTypeface, Typeface.NORMAL)
					}
				}
			}
		}
	}
}

