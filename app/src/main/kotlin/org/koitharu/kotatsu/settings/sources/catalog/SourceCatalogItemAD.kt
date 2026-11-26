package org.koitharu.kotatsu.settings.sources.catalog

import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.appendTag
import org.koitharu.kotatsu.core.model.getSummary
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.parser.favicon.extensionPackageFaviconUri
import org.koitharu.kotatsu.core.ui.image.FaviconDrawable
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.ui.widgets.applyConnectedActionShapes
import org.koitharu.kotatsu.core.util.ext.drawableStart
import org.koitharu.kotatsu.core.util.ext.getThemeDimensionPixelOffset
import org.koitharu.kotatsu.core.util.ext.setTextAndVisible
import org.koitharu.kotatsu.databinding.ItemEmptyHintBinding
import org.koitharu.kotatsu.databinding.ItemExtensionCatalogBinding
import org.koitharu.kotatsu.databinding.ItemSourceCatalogBinding
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.sourcescore.ui.HotSourceMarker
import androidx.appcompat.R as appcompatR

interface ExtensionActionListener {
	fun onExtensionActionClick(item: SourceCatalogItem.Extension)
	fun onExtensionSettingsClick(item: SourceCatalogItem.Extension)
	fun onExtensionItemClick(item: SourceCatalogItem.Extension)
	fun onExtensionHideClick(item: SourceCatalogItem.Extension)
}

fun sourceCatalogItemSourceAD(
	listener: OnListItemClickListener<SourceCatalogItem.Source>
) = adapterDelegateViewBinding<SourceCatalogItem.Source, ListModel, ItemSourceCatalogBinding>(
	{ layoutInflater, parent ->
		ItemSourceCatalogBinding.inflate(layoutInflater, parent, false)
	},
) {

	binding.imageViewAdd.setOnClickListener { v ->
		listener.onItemLongClick(item, v)
	}
	binding.root.setOnClickListener { v ->
		listener.onItemClick(item, v)
	}

	val hotMarker = HotSourceMarker(context)

	bind {
		binding.textViewTitle.text = hotMarker.decorate(item.source.getTitle(context), item.isHot, atStart = true)
		binding.textViewDescription.text = item.source.getSummary(context)
		binding.textViewDescription.drawableStart = if (item.source.isBroken) {
			ContextCompat.getDrawable(context, R.drawable.ic_off_small)
		} else {
			null
		}
		FaviconDrawable(context, R.style.FaviconDrawable_Small, item.source.name)
		binding.imageViewIcon.setImageAsync(item.source)
		binding.imageViewAdd.setImageResource(
			if (item.isInPreset) R.drawable.ic_check else R.drawable.ic_add,
		)
	}
}

fun sourceCatalogItemExtensionAD(
	listener: ExtensionActionListener,
) = adapterDelegateViewBinding<SourceCatalogItem.Extension, ListModel, ItemExtensionCatalogBinding>(
	{ layoutInflater, parent ->
		ItemExtensionCatalogBinding.inflate(layoutInflater, parent, false)
	},
) {

	binding.imageViewAdd.setOnClickListener {
		listener.onExtensionActionClick(item)
	}
	binding.imageViewSettings.setOnClickListener {
		listener.onExtensionSettingsClick(item)
	}
	binding.imageViewHide.setOnClickListener {
		listener.onExtensionHideClick(item)
	}
	binding.root.setOnClickListener {
		listener.onExtensionItemClick(item)
	}
	val basePadding = context.getThemeDimensionPixelOffset(
		appcompatR.attr.listPreferredItemPaddingEnd,
		binding.root.paddingStart,
	)
	val compactEndPadding = (basePadding - context.resources.getDimensionPixelOffset(R.dimen.margin_small)).coerceAtLeast(0)
	val outerCornerSize = context.resources.getDimensionPixelSize(R.dimen.extension_action_button_size) / 2f
	val innerCornerSize = 8f * context.resources.displayMetrics.density

	bind {
		val isInProgress = item.isInProgress
		val isPrivateMode = item.isPrivateMode
		binding.imageViewAdd.isVisible = true
		binding.imageViewAdd.isEnabled = !isInProgress
		binding.imageViewAdd.alpha = if (isInProgress) 0.45f else 1f
		binding.progressIcon.isVisible = isInProgress

		val isInstalled = if (isPrivateMode) {
			item.action == SourceCatalogItem.Extension.Action.DISABLE ||
				item.action == SourceCatalogItem.Extension.Action.UPDATE ||
				item.action == SourceCatalogItem.Extension.Action.UNINSTALL
		} else {
			item.action != SourceCatalogItem.Extension.Action.INSTALL
		}
		binding.imageViewSettings.isVisible = isInstalled && item.sourceName != null
		binding.imageViewSettings.isEnabled = !isInProgress
		binding.imageViewSettings.alpha = if (isInProgress) 0.45f else 1f
		binding.imageViewHide.isVisible = isInstalled
		binding.imageViewHide.isEnabled = !isInProgress
		binding.imageViewHide.alpha = if (isInProgress) 0.45f else 1f
		if (isInstalled) {
			val hideButton = binding.imageViewHide
			hideButton.setIconResource(if (item.isHidden) R.drawable.ic_eye_off else R.drawable.ic_eye)
			val hideDescription = context.getString(if (item.isHidden) R.string.unhide else R.string.hide)
			hideButton.contentDescription = hideDescription
			TooltipCompat.setTooltipText(hideButton, hideDescription)
		}
		binding.buttonGroupActions.isVisible = true
		binding.buttonGroupActions.applyConnectedActionShapes(outerCornerSize, innerCornerSize)
		binding.buttonGroupActions.post {
			binding.buttonGroupActions.applyConnectedActionShapes(outerCornerSize, innerCornerSize)
		}
		binding.root.updatePaddingRelative(end = compactEndPadding)
		binding.imageViewAdd.setIconResource(item.action.iconRes)
		val actionDescription = if (isInProgress) {
			context.getString(R.string.in_progress)
		} else {
			context.getString(item.action.titleRes)
		}
		binding.imageViewAdd.contentDescription = actionDescription
		TooltipCompat.setTooltipText(binding.imageViewAdd, actionDescription)
		binding.textViewTitle.text = item.title
		binding.textViewDescription.text = item.contentWarning.appendTag(context, item.subtitle)
		binding.textViewDescription.drawableStart = null
		val sourceIconName = item.sourceIconName
		val iconUrl = item.iconUrl
		if (isInstalled) {
			binding.imageViewIcon.setImageAsync(extensionPackageFaviconUri(item.packageName).toString())
		} else if (iconUrl != null) {
			binding.imageViewIcon.setImageAsync(iconUrl)
		} else if (sourceIconName != null) {
			binding.imageViewIcon.setImageAsync(MangaSource(sourceIconName))
		} else {
			binding.imageViewIcon.disposeImage()
			binding.imageViewIcon.setImageDrawable(
				FaviconDrawable(
					context = context,
					styleResId = R.style.FaviconDrawable_Small,
					name = item.title,
				),
			)
		}
	}
}

fun sourceCatalogItemHintAD() = adapterDelegateViewBinding<SourceCatalogItem.Hint, ListModel, ItemEmptyHintBinding>(
	{ inflater, parent -> ItemEmptyHintBinding.inflate(inflater, parent, false) },
) {

	binding.buttonRetry.isVisible = false

	bind {
		binding.icon.setImageAsync(item.icon)
		binding.textPrimary.setText(item.title)
		binding.textSecondary.setTextAndVisible(item.text)
	}
}
