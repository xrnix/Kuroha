package org.koitharu.kotatsu.list.ui.size

import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.widgets.IconsView
import org.koitharu.kotatsu.history.ui.util.ReadingProgressView
import kotlin.math.roundToInt

class DynamicItemSizeResolver(
	resources: Resources,
	private val lifecycleOwner: LifecycleOwner,
	private val settings: AppSettings,
	private val adjustWidth: Boolean,
	private val sizeKey: String = AppSettings.KEY_GRID_SIZE,
	private val sizeOf: (AppSettings) -> Int = { it.gridSize },
) : ItemSizeResolver {

	private val gridWidth = resources.getDimension(R.dimen.preferred_grid_width)
	private val scaleFactor: Float
		get() = sizeOf(settings) / 100f

	override val cellWidth: Int
		get() = (gridWidth * if (adjustWidth) scaleFactor.coerceAtMost(MAX_ROW_SCALE) else scaleFactor).roundToInt()

	override fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
		iconsView: IconsView?,
	) {
		val observer = SizeObserver(view, textView, progressView, iconsView)
		view.addOnAttachStateChangeListener(observer)
		lifecycleOwner.lifecycle.addObserver(observer)
		if (view.isAttachedToWindow) {
			observer.onViewAttachedToWindow(view)
		}
	}

	private inner class SizeObserver(
		private val view: View,
		private val textView: TextView?,
		private val progressView: ReadingProgressView?,
		private val iconsView: IconsView?,
	) : DefaultLifecycleObserver, SharedPreferences.OnSharedPreferenceChangeListener, View.OnAttachStateChangeListener {

		private val widthThreshold = view.resources.getDimensionPixelSize(R.dimen.small_grid_width)
		private var isRegistered = false
		private var prevWidth = 0
		private var prevTextAppearance = 0

		override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
			if (key == sizeKey) {
				update()
			}
		}

		override fun onDestroy(owner: LifecycleOwner) {
			unregister()
			view.removeOnAttachStateChangeListener(this)
			owner.lifecycle.removeObserver(this)
		}

		override fun onViewAttachedToWindow(v: View) {
			update()
			register()
		}

		override fun onViewDetachedFromWindow(v: View) {
			unregister()
		}

		private fun register() {
			if (!isRegistered) {
				settings.subscribe(this)
				isRegistered = true
			}
		}

		private fun unregister() {
			if (isRegistered) {
				settings.unsubscribe(this)
				isRegistered = false
			}
		}

		private fun update() {
			val newWidth = cellWidth
			if (prevWidth != newWidth) {
				prevWidth = newWidth
				textView?.adjustTextAppearance(newWidth)
				if (adjustWidth) {
					view.updateLayoutParams {
						width = newWidth
					}
				}
			}
			progressView?.adjustSize(newWidth)
			iconsView?.setSmall(newWidth < widthThreshold)
		}

		private fun ReadingProgressView.adjustSize(width: Int) {
			setSmall(width < widthThreshold)
		}

		private fun TextView.adjustTextAppearance(width: Int) {
			val textAppearanceResId = if (width < widthThreshold) {
				R.style.TextAppearance_Kotatsu_GridTitle_Small_Overlay
			} else {
				R.style.TextAppearance_Kotatsu_GridTitle_Overlay
			}
			if (textAppearanceResId != prevTextAppearance) {
				prevTextAppearance = textAppearanceResId
				TextViewCompat.setTextAppearance(this, textAppearanceResId)
			}
		}
	}

	private companion object {

		const val MAX_ROW_SCALE = 1.5f
	}
}
