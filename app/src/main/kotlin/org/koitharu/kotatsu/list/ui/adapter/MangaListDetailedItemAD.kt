package org.koitharu.kotatsu.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.ui.list.AdapterDelegateClickListenerAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.core.util.ext.textAndVisible
import org.koitharu.kotatsu.databinding.ItemMangaListDetailsBinding
import org.koitharu.kotatsu.list.ui.ListModelDiffCallback
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.MangaDetailedListModel

fun mangaListDetailedItemAD(
	clickListener: MangaDetailsClickListener,
	titleClickListener: OnListItemClickListener<MangaListModel>? = null,
) = adapterDelegateViewBinding<MangaDetailedListModel, ListModel, ItemMangaListDetailsBinding>(
	{ inflater, parent -> ItemMangaListDetailsBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener)
		.attach(itemView)
	if (titleClickListener != null) {
		binding.textViewTitle.attachTitleClickToRead(itemView) { view ->
			titleClickListener.onItemClick(item, view)
		}
	}

	var lastItem: MangaDetailedListModel? = null

	bind { payloads ->
		val prev = lastItem
		lastItem = item
		if (prev == null || prev.title != item.title) {
			binding.textViewTitle.text = item.title
		}
		if (prev == null || prev.source != item.source) {
			binding.textViewSource.text = item.source.getTitle(context)
		}
		binding.imageViewFavicon.setImageAsync(item.source)
		if (prev == null || prev.manga.authors != item.manga.authors) {
			binding.textViewAuthor.textAndVisible = item.manga.authors.joinToString(", ")
		}
		binding.corners.progressView.setProgress(
			value = item.progress,
			animate = ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED in payloads,
		)
		binding.iconsViewBottom.setMangaBadges(item.isSaved, item.isFavorite)
		binding.corners.imageViewPin.isVisible = item.isPinned
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		if (prev == null || prev.tags != item.tags) {
			binding.textViewTags.text = item.tags.joinToString(separator = ", ") { it.title ?: "" }
		}
		binding.corners.badge.number = item.counter
		binding.corners.badge.isVisible = item.counter > 0
	}
}
