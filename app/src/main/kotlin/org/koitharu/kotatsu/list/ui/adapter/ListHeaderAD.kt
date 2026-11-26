package org.koitharu.kotatsu.list.ui.adapter

import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.badge.BadgeDrawable
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.databinding.ItemHeaderBinding
import org.koitharu.kotatsu.list.ui.model.ListHeader
import org.koitharu.kotatsu.list.ui.model.ListModel

fun listHeaderAD(
	listener: ListHeaderClickListener?,
) = adapterDelegateViewBinding<ListHeader, ListModel, ItemHeaderBinding>(
	{ inflater, parent -> ItemHeaderBinding.inflate(inflater, parent, false) },
) {
	var badge: BadgeDrawable? = null

	if (listener != null) {
		binding.buttonMore.setOnClickListener {
			listener.onListHeaderClick(item, it)
		}
		binding.buttonSplitLeft.setOnClickListener {
			listener.onListHeaderClick(item, it)
		}
		binding.buttonSplitRight.setOnClickListener {
			listener.onListHeaderSecondaryClick(item, it)
		}
	}

	bind {
		binding.textViewTitle.text = item.getText(context)
		if (item.secondaryButtonTextRes != 0) {
			binding.buttonMore.isVisible = false
			binding.buttonMore.clearBadge(badge)
			binding.splitPill.isVisible = true
			binding.buttonSplitLeft.setText(item.buttonTextRes)
			binding.buttonSplitRight.setText(item.secondaryButtonTextRes)
		} else if (item.buttonTextRes == 0) {
			binding.splitPill.isVisible = false
			binding.buttonMore.isInvisible = true
			binding.buttonMore.text = null
			binding.buttonMore.clearBadge(badge)
		} else {
			binding.splitPill.isVisible = false
			binding.buttonMore.setText(item.buttonTextRes)
			binding.buttonMore.isVisible = true
			badge = itemView.bindBadge(badge, item.badge)
		}
	}
}
