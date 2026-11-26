package org.koitharu.kotatsu.settings.sources.catalog

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.updatePaddingRelative
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.ui.list.fastscroll.FastScroller
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.getThemeDimensionPixelOffset
import org.koitharu.kotatsu.databinding.ItemButtonFooterBinding
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.list.ui.adapter.ListHeaderClickListener
import org.koitharu.kotatsu.list.ui.adapter.listHeaderAD
import org.koitharu.kotatsu.list.ui.adapter.loadingStateAD
import org.koitharu.kotatsu.list.ui.model.ButtonFooter
import org.koitharu.kotatsu.list.ui.model.ListModel
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class SourcesCatalogAdapter(
	sourceClickListener: OnListItemClickListener<SourceCatalogItem.Source>? = null,
	extensionActionListener: ExtensionActionListener? = null,
	headerClickListener: ListHeaderClickListener? = null,
	onUpdateAll: (() -> Unit)? = null,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	constructor(clickListener: OnListItemClickListener<SourceCatalogItem.Source>) : this(
		sourceClickListener = clickListener,
		extensionActionListener = null,
		headerClickListener = null,
		onUpdateAll = null,
	)

	constructor(
		extensionActionListener: ExtensionActionListener,
		headerClickListener: ListHeaderClickListener,
		onUpdateAll: () -> Unit,
	) : this(
		sourceClickListener = null,
		extensionActionListener = extensionActionListener,
		headerClickListener = headerClickListener,
		onUpdateAll = onUpdateAll,
	)

	init {
		sourceClickListener?.let { addDelegate(ListItemType.NAV_ITEM, sourceCatalogItemSourceAD(it)) }
		extensionActionListener?.let { addDelegate(ListItemType.NAV_ITEM, sourceCatalogItemExtensionAD(it)) }
		addDelegate(ListItemType.HINT_EMPTY, sourceCatalogItemHintAD())
		headerClickListener?.let { addDelegate(ListItemType.HEADER, listHeaderAD(it)) }
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		onUpdateAll?.let { addDelegate(ListItemType.FOOTER_BUTTON, updateAllFooterAD(it)) }
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		val item = items.getOrNull(position)
		return (item as? SourceCatalogItem.Extension)?.title?.take(1)
			?: (item as? SourceCatalogItem.Source)?.source?.title?.take(1)
	}
}

private fun updateAllFooterAD(
	onUpdateAll: () -> Unit,
) = adapterDelegateViewBinding<ButtonFooter, ListModel, ItemButtonFooterBinding>(
	{ inflater, parent -> ItemButtonFooterBinding.inflate(inflater, parent, false) },
) {
	binding.button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.bg_extension_action_button)
	binding.button.setTextColor(context.getThemeColor(R.attr.colorTopBarIcon))
	(binding.button as? com.google.android.material.button.MaterialButton)?.strokeColor = ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorOutlineVariant))
	binding.root.updatePaddingRelative(
		start = context.getThemeDimensionPixelOffset(appcompatR.attr.listPreferredItemPaddingStart),
		end = context.getThemeDimensionPixelOffset(appcompatR.attr.listPreferredItemPaddingEnd),
	)
	binding.button.setOnClickListener { onUpdateAll() }
	bind { binding.button.setText(item.textResId) }
}
