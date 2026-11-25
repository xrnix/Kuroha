package org.koitharu.kotatsu.core.ui.image

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip
import coil3.Image
import coil3.asImage
import coil3.getExtra
import coil3.request.ImageRequest
import com.google.android.material.color.MaterialColors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.util.KotatsuColors
import org.koitharu.kotatsu.core.util.ext.hasFocusStateSpecified
import org.koitharu.kotatsu.core.util.ext.mangaSourceKey

open class FaviconDrawable(
	context: Context,
	@StyleRes styleResId: Int,
	name: String,
) : PaintDrawable() {

	override val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
	protected var currentBackgroundColor = Color.WHITE
		private set
	private var colorBackground: ColorStateList = ColorStateList.valueOf(currentBackgroundColor)
	protected var colorForeground = Color.DKGRAY
	protected var currentForegroundColor = Color.DKGRAY
	protected var currentStrokeColor = Color.LTGRAY
		private set
	private var colorStroke: ColorStateList = ColorStateList.valueOf(currentStrokeColor)
	private val letter = name.take(1).uppercase()
	private var cornerSize = 0f
	private var intrinsicSize = -1
	private val textBounds = Rect()
	private val tempRect = Rect()
	private val boundsF = RectF()
	private val clipPath = Path()

	init {
		context.withStyledAttributes(styleResId, R.styleable.FaviconFallbackDrawable) {
			colorBackground = getColorStateList(R.styleable.FaviconFallbackDrawable_backgroundColor) ?: colorBackground
			colorStroke = getColorStateList(R.styleable.FaviconFallbackDrawable_strokeColor) ?: colorStroke
			cornerSize = getDimension(R.styleable.FaviconFallbackDrawable_cornerSize, cornerSize)
			paint.strokeWidth = 0f
			intrinsicSize = getDimensionPixelSize(R.styleable.FaviconFallbackDrawable_drawableSize, intrinsicSize)
		}
		paint.textAlign = Paint.Align.CENTER
		paint.isFakeBoldText = true
		val pair = PALETTE[(name.hashCode() and 0x7fffffff) % PALETTE.size]
		colorBackground = ColorStateList.valueOf(pair.first)
		colorForeground = pair.second
		currentForegroundColor = pair.second
		onStateChange(state)
	}

	override fun draw(canvas: Canvas) {
		if (cornerSize > 0f) {
			canvas.withClip(clipPath) {
				doDraw(canvas)
			}
		} else {
			doDraw(canvas)
		}
	}

	override fun onBoundsChange(bounds: Rect) {
		super.onBoundsChange(bounds)
		boundsF.set(bounds)
		val side = minOf(bounds.width(), bounds.height()).toFloat().coerceAtLeast(1f)
		paint.textSize = side * 0.46f
		paint.getTextBounds(letter, 0, letter.length, textBounds)
		clipPath.reset()
		clipPath.addRoundRect(boundsF, cornerSize, cornerSize, Path.Direction.CW)
		clipPath.close()
	}

	override fun getIntrinsicWidth(): Int = intrinsicSize

	override fun getIntrinsicHeight(): Int = intrinsicSize

	override fun isOpaque(): Boolean = cornerSize == 0f && colorBackground.isOpaque

	override fun isStateful(): Boolean = colorStroke.isStateful || colorBackground.isStateful

	@RequiresApi(Build.VERSION_CODES.S)
	override fun hasFocusStateSpecified(): Boolean =
		colorBackground.hasFocusStateSpecified() || colorStroke.hasFocusStateSpecified()

	override fun onStateChange(state: IntArray): Boolean {
		val prevStrokeColor = currentStrokeColor
		val prevBackgroundColor = currentBackgroundColor
		currentStrokeColor = colorStroke.getColorForState(state, colorStroke.defaultColor)
		currentBackgroundColor = colorBackground.getColorForState(state, colorBackground.defaultColor)
		if (currentBackgroundColor != prevBackgroundColor) {
			currentForegroundColor = MaterialColors.harmonize(colorForeground, currentBackgroundColor)
		}
		return prevBackgroundColor != currentBackgroundColor || prevStrokeColor != currentStrokeColor
	}

	private fun doDraw(canvas: Canvas) {
		// background
		paint.color = currentBackgroundColor
		paint.style = Paint.Style.FILL
		canvas.drawPaint(paint)
		// centered monogram letter
		paint.color = currentForegroundColor
		val cx = boundsF.centerX()
		val ty = boundsF.centerY() - textBounds.exactCenterY()
		canvas.drawText(letter, cx, ty, paint)
	}

	companion object {

		private val PALETTE = arrayOf(
			0xFF1F4E6D.toInt() to 0xFFCFE6F7.toInt(), // Ocean Cobalt
			0xFF453B6B.toInt() to 0xFFE5DEFA.toInt(), // Deep Amethyst
			0xFF1A524B.toInt() to 0xFFC2EFE7.toInt(), // Emerald Teal
			0xFF6B3848.toInt() to 0xFFFAD8E2.toInt(), // Terracotta Rose
			0xFF5F4418.toInt() to 0xFFFBE2BD.toInt(), // Warm Amber
			0xFF2E5332.toInt() to 0xFFCFEBD0.toInt(), // Forest Sage
			0xFF333D79.toInt() to 0xFFDCE1FA.toInt(), // Royal Indigo
			0xFF6A3623.toInt() to 0xFFFADCCF.toInt(), // Warm Copper
		)
	}

	class Factory(
		@StyleRes private val styleResId: Int,
	) : ((ImageRequest) -> Image?) {

		override fun invoke(request: ImageRequest): Image? {
			val source = request.getExtra(mangaSourceKey) ?: return null
			val context = request.context
			val title = source.getTitle(context)
			return FaviconDrawable(context, styleResId, title).asImage()
		}
	}
}
