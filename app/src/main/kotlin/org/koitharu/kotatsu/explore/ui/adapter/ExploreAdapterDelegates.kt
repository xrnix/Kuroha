package org.koitharu.kotatsu.explore.ui.adapter

import android.graphics.Outline
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.isVisible
import org.koitharu.kotatsu.core.ui.widgets.CarouselRecyclerView
import org.koitharu.kotatsu.core.ui.widgets.SafeCarouselLayoutManager
import com.google.android.material.carousel.MultiBrowseCarouselStrategy
import org.koitharu.kotatsu.databinding.ItemMangaCarouselBinding
import org.koitharu.kotatsu.explore.ui.ExploreViewModel
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getSummary
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.list.AdapterDelegateClickListenerAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.drawableStart
import org.koitharu.kotatsu.core.util.ext.recyclerView
import org.koitharu.kotatsu.core.util.ext.setProgressIcon
import org.koitharu.kotatsu.core.util.ext.setTooltipCompat
import org.koitharu.kotatsu.core.util.ext.textAndVisible
import org.koitharu.kotatsu.databinding.ItemExploreButtonsBinding
import org.koitharu.kotatsu.databinding.ItemExploreSourceGridBinding
import org.koitharu.kotatsu.databinding.ItemExploreSourceListBinding
import org.koitharu.kotatsu.databinding.ItemRecommendationBinding
import org.koitharu.kotatsu.databinding.ItemRecommendationMangaBinding
import org.koitharu.kotatsu.explore.ui.model.ExploreButtons
import org.koitharu.kotatsu.explore.ui.model.MangaSourceItem
import org.koitharu.kotatsu.explore.ui.model.RecommendationsItem
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.MangaCompactListModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.sourcescore.ui.HotSourceMarker

fun exploreButtonsAD(
	clickListener: View.OnClickListener,
) = adapterDelegateViewBinding<ExploreButtons, ListModel, ItemExploreButtonsBinding>(
	{ layoutInflater, parent -> ItemExploreButtonsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.buttonDownloads.setOnClickListener(clickListener)
	binding.buttonLocal.setOnClickListener(clickListener)
	binding.buttonRandom.setOnClickListener(clickListener)
	binding.buttonPresets.setOnClickListener(clickListener)

	bind {
		if (item.isRandomLoading) {
			binding.buttonRandom.setProgressIcon()
		} else {
			binding.buttonRandom.setIconResource(R.drawable.ic_dice)
		}
		binding.buttonRandom.isClickable = !item.isRandomLoading
		val presetName = item.activePresetName
		if (presetName != null) {
			binding.buttonPresets.text = presetName
		} else {
			binding.buttonPresets.setText(R.string.tap_grid_presets)
		}
	}
}

fun exploreRecommendationItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<RecommendationsItem, ListModel, ItemRecommendationBinding>(
	{ layoutInflater, parent -> ItemRecommendationBinding.inflate(layoutInflater, parent, false) },
) {

	val adapter = binding.recyclerView.setUpMangaCarousel(itemClickListener)

	bind {
		adapter.items = item.manga.ifEmpty { carouselSkeleton }
	}
}

fun recommendationMangaItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<MangaCompactListModel, MangaCompactListModel, ItemRecommendationMangaBinding>(
	{ layoutInflater, parent -> ItemRecommendationMangaBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		itemClickListener.onItemClick(item.manga, v)
	}
	bind {
		binding.textViewTitle.text = item.manga.title
		binding.textViewSubtitle.textAndVisible = item.subtitle
		binding.imageViewCover.setImageAsync(item.manga.coverUrl, item.manga.source)
	}
}


fun exploreSourceListItemAD(
	listener: OnListItemClickListener<MangaSourceItem>,
) = adapterDelegateViewBinding<MangaSourceItem, ListModel, ItemExploreSourceListBinding>(
	{ layoutInflater, parent ->
		ItemExploreSourceListBinding.inflate(
			layoutInflater,
			parent,
			false,
		)
	},
	on = { item, _, _ -> item is MangaSourceItem && !item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, listener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, R.drawable.ic_pin_small)
	val hotMarker = HotSourceMarker(context)

	bind {
		binding.textViewTitle.text = hotMarker.decorate(item.source.getTitle(context), item.isTrending, atStart = true)
		binding.textViewTitle.drawableStart = if (item.source.isPinned) iconPinned else null
		binding.textViewSubtitle.text = item.source.getSummary(context)
		binding.imageViewIcon.setImageAsync(item.source)
	}
}

fun exploreSourceGridItemAD(
	listener: OnListItemClickListener<MangaSourceItem>,
) = adapterDelegateViewBinding<MangaSourceItem, ListModel, ItemExploreSourceGridBinding>(
	{ layoutInflater, parent ->
		ItemExploreSourceGridBinding.inflate(
			layoutInflater,
			parent,
			false,
		)
	},
	on = { item, _, _ -> item is MangaSourceItem && item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, listener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, R.drawable.ic_pin_small)
	val hotMarker = HotSourceMarker(context)

	bind {
		val title = item.source.getTitle(context)
		itemView.setTooltipCompat(
			buildSpannedString {
				bold {
					append(title)
				}
				appendLine()
				append(item.source.getSummary(context))
			},
		)
		binding.textViewTitle.text = hotMarker.decorate(title, item.isTrending, atStart = true)
		binding.textViewTitle.drawableStart = if (item.source.isPinned) iconPinned else null
		binding.imageViewIcon.setImageAsync(item.source)
	}
}

/** Shared by Explore's suggestions and the details page's related titles, so both look the same. */
fun CarouselRecyclerView.setUpMangaCarousel(
	itemClickListener: OnListItemClickListener<Manga>,
): BaseListAdapter<ListModel> {
	val adapter = BaseListAdapter<ListModel>()
		.addDelegate(ListItemType.MANGA_CAROUSEL, recommendationCarouselItemAD(itemClickListener))
		.addDelegate(ListItemType.STATE_LOADING, carouselSkeletonItemAD())
	this.adapter = adapter
	layoutManager = SafeCarouselLayoutManager(MultiBrowseCarouselStrategy())
	return adapter
}

private fun recommendationCarouselItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<MangaListModel, ListModel, ItemMangaCarouselBinding>(
	{ layoutInflater, parent -> ItemMangaCarouselBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		itemClickListener.onItemClick(item.toMangaWithOverride(), v)
	}
	binding.progressView.isVisible = false
	binding.badge.isVisible = false
	binding.clipCoverToMask()

	bind {
		binding.textViewTitle.text = item.title
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
	}
}

private fun carouselSkeletonItemAD() =
	adapterDelegateViewBinding<CarouselSkeletonItem, ListModel, ItemMangaCarouselBinding>(
		{ layoutInflater, parent -> ItemMangaCarouselBinding.inflate(layoutInflater, parent, false) },
	) {
		binding.progressView.isVisible = false
		binding.badge.isVisible = false
		binding.clipCoverToMask()
	}

private data class CarouselSkeletonItem(val index: Int) : ListModel {
	override fun areItemsTheSame(other: ListModel) = other is CarouselSkeletonItem && other.index == index
}

private val carouselSkeleton = List(ExploreViewModel.SUGGESTIONS_COUNT, ::CarouselSkeletonItem)

private fun ItemMangaCarouselBinding.clipCoverToMask() {
	val cover = imageViewCover
	val coverRadius = cover.shapeAppearanceModel.bottomLeftCornerSize.getCornerSize(RectF())
	var maskLeft = 0f
	var maskRight = Float.MAX_VALUE
	cover.clipToOutline = true
	cover.outlineProvider = object : ViewOutlineProvider() {
		override fun getOutline(view: View, outline: Outline) {
			outline.setRoundRect(
				maskLeft.roundToInt().coerceAtLeast(0),
				0,
				maskRight.roundToInt().coerceAtMost(view.width),
				view.height,
				coverRadius,
			)
		}
	}
	carouselItem.setOnMaskChangedListener { maskRect ->
		maskLeft = maskRect.left
		maskRight = maskRect.right
		cover.invalidateOutline()
	}
}
