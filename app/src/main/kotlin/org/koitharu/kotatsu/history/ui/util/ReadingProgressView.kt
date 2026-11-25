package org.koitharu.kotatsu.history.ui.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.ProgressIndicatorMode.*
import org.koitharu.kotatsu.list.domain.ReadingProgress
import kotlin.math.roundToInt

/**
 * A Material 3 Expressive circular progress badge that displays the read progress
 * as a ring border with the number or completion checkmark in the center.
 */
class ReadingProgressView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

	private var isSmall: Boolean = false

	private val primaryColor = MaterialColors.getColor(
		this,
		androidx.appcompat.R.attr.colorPrimary,
		0xFF25E2B6.toInt(),
	)

	private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = MaterialColors.getColor(
			this@ReadingProgressView,
			com.google.android.material.R.attr.colorSurfaceContainerHigh,
			0xE61A2324.toInt(),
		)
		style = Paint.Style.FILL
	}

	private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = ColorUtils.setAlphaComponent(primaryColor, 55)
		style = Paint.Style.STROKE
	}

	private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = primaryColor
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}

	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
		color = primaryColor
		textAlign = Paint.Align.CENTER
		typeface = ResourcesCompat.getFont(context, R.font.google_sans_medium)
			?: Typeface.create("sans-serif-medium", Typeface.BOLD)
	}

	private val rectF = RectF()

	private val checkIcon = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.also {
		it.setTint(primaryColor)
	}

	private var text: String = ""
	private var isCompleted: Boolean = false

	var progress: ReadingProgress? = null
		set(value) {
			field = value
			val oldCompleted = isCompleted
			isCompleted = value != null && value.mode != NONE && (
				value.isCompleted() || (value.mode == CHAPTERS_READ && value.totalChapters > 0 && (value.percent * value.totalChapters).roundToInt() >= value.totalChapters)
			)
			val newText = when (value?.mode) {
				null, NONE -> ""
				PERCENT_READ -> if (value.percent in 0f..1f) {
					ReadingProgress.percentToString(value.percent)
				} else {
					""
				}
				PERCENT_LEFT -> if (value.percent in 0f..1f) {
					ReadingProgress.percentToString(value.percentLeft)
				} else {
					""
				}
				CHAPTERS_READ -> if (value.percent in 0f..1f && value.totalChapters > 0) {
					val read = (value.percent * value.totalChapters).roundToInt()
					read.toString()
				} else {
					""
				}
				CHAPTERS_LEFT -> if (value.percent in 0f..1f && value.totalChapters > 0) {
					val left = value.totalChapters - (value.percent * value.totalChapters).roundToInt()
					left.toString()
				} else {
					""
				}
			}
			if (newText != text || isCompleted != oldCompleted) {
				text = newText
				requestLayout()
				invalidate()
			}
		}

	val progressFraction: Float
		get() {
			val p = progress ?: return 0f
			return when (p.mode) {
				NONE -> 0f
				PERCENT_READ, CHAPTERS_READ -> p.percent.coerceIn(0f, 1f)
				PERCENT_LEFT, CHAPTERS_LEFT -> p.percentLeft.coerceIn(0f, 1f)
			}
		}

	init {
		if (isInEditMode) {
			progress = ReadingProgress(0.29f, 20, PERCENT_READ)
		}
	}

	fun setProgress(percent: Float, @Suppress("UNUSED_PARAMETER") animate: Boolean) {
		progress = ReadingProgress(percent, 1, PERCENT_READ)
	}

	fun setProgress(value: ReadingProgress?, @Suppress("UNUSED_PARAMETER") animate: Boolean) {
		progress = value
	}

	/** Picks a smaller circular size for compact (small) grid cells. */
	fun setSmall(small: Boolean) {
		if (isSmall != small) {
			isSmall = small
			requestLayout()
			invalidate()
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (text.isEmpty() && !isCompleted) {
			setMeasuredDimension(0, 0)
			return
		}
		val diameter = dp(if (isSmall) DIAMETER_SMALL_DP else DIAMETER_NORMAL_DP).roundToInt()
		setMeasuredDimension(
			resolveSize(diameter, widthMeasureSpec),
			resolveSize(diameter, heightMeasureSpec),
		)
	}

	override fun onDraw(canvas: Canvas) {
		if (text.isEmpty() && !isCompleted) {
			return
		}
		val w = width.toFloat()
		val h = height.toFloat()
		val size = minOf(w, h)
		val centerX = w / 2f
		val centerY = h / 2f
		val strokeW = dp(if (isSmall) STROKE_SMALL_DP else STROKE_NORMAL_DP)
		trackPaint.strokeWidth = strokeW
		progressPaint.strokeWidth = strokeW

		val halfStroke = strokeW / 2f
		val innerRadius = size / 2f - halfStroke

		// 1. Elevated container background
		canvas.drawCircle(centerX, centerY, innerRadius, bgPaint)

		// 2. Circular border track ring
		rectF.set(centerX - innerRadius, centerY - innerRadius, centerX + innerRadius, centerY + innerRadius)
		canvas.drawOval(rectF, trackPaint)

		// 3. Progress arc along the circular border
		val fraction = if (isCompleted) 1f else progressFraction
		val sweepAngle = fraction * 360f
		if (sweepAngle >= 359.5f) {
			canvas.drawOval(rectF, progressPaint)
		} else if (sweepAngle > 0.5f) {
			canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)
		}

		// 4. Center content: checkmark if completed, number otherwise
		if (isCompleted && checkIcon != null) {
			val iconSize = (size * 0.52f).roundToInt()
			val left = (centerX - iconSize / 2f).roundToInt()
			val top = (centerY - iconSize / 2f).roundToInt()
			checkIcon.setBounds(left, top, left + iconSize, top + iconSize)
			checkIcon.draw(canvas)
		} else {
			val baseTextSp = if (isSmall) {
				if (text.length >= 3) 8f else 9.5f
			} else {
				if (text.length >= 3) 9f else 11f
			}
			textPaint.textSize = sp(baseTextSp)
			val fm = textPaint.fontMetrics
			val baseline = centerY - (fm.ascent + fm.descent) / 2f
			canvas.drawText(text, centerX, baseline, textPaint)
		}
	}

	private fun dp(value: Float) = TypedValue.applyDimension(
		TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
	)

	private fun sp(value: Float) = TypedValue.applyDimension(
		TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics,
	)

	companion object {

		const val DIAMETER_NORMAL_DP = 26f
		const val DIAMETER_SMALL_DP = 22f
		const val STROKE_NORMAL_DP = 2.5f
		const val STROKE_SMALL_DP = 2.0f

		/** The chip's diameter regardless of its text, so other cover badges can match it. */
		fun pillHeight(resources: Resources, small: Boolean): Int {
			val dm = resources.displayMetrics
			val dpVal = if (small) DIAMETER_SMALL_DP else DIAMETER_NORMAL_DP
			return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal, dm).roundToInt()
		}
	}
}
