package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.google.android.material.color.DynamicColors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.os.AppIconStyle
import org.koitharu.kotatsu.parsers.util.find

@Keep
enum class ColorScheme(
	@StyleRes val styleResId: Int,
	@StringRes val titleResId: Int,
	@StyleRes val splashStyleResId: Int,
	private val splashShapeStyleResIds: IntArray,
	private val chiseledSplashShapeStyleResIds: IntArray,
) {

	EXPRESSIVE(
		R.style.ThemeOverlay_Kotatsu_Expressive,
		R.string.theme_name_expressive,
		R.style.Theme_Kotatsu_Splash_Expressive,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Expressive_Clover4,
			R.style.Theme_Kotatsu_Splash_Expressive_Flower6,
			R.style.Theme_Kotatsu_Splash_Expressive_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Expressive_Sunny,
			R.style.Theme_Kotatsu_Splash_Expressive_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Expressive_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Expressive_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Expressive_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Expressive_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Expressive_Chiseled_Diamond,
		),
	),
	KUROHA(
		R.style.ThemeOverlay_Kotatsu_Kuroha,
		R.string.theme_name_kuroha,
		R.style.Theme_Kotatsu_Splash_Kuroha,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Kuroha_Clover4,
			R.style.Theme_Kotatsu_Splash_Kuroha_Flower6,
			R.style.Theme_Kotatsu_Splash_Kuroha_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Kuroha_Sunny,
			R.style.Theme_Kotatsu_Splash_Kuroha_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Kuroha_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Kuroha_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Kuroha_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Kuroha_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Kuroha_Chiseled_Diamond,
		),
	),
	DEFAULT(
		R.style.ThemeOverlay_Kotatsu_Totoro,
		R.string.theme_name_totoro,
		R.style.Theme_Kotatsu_Splash_Totoro,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Totoro_Clover4,
			R.style.Theme_Kotatsu_Splash_Totoro_Flower6,
			R.style.Theme_Kotatsu_Splash_Totoro_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Totoro_Sunny,
			R.style.Theme_Kotatsu_Splash_Totoro_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Totoro_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Totoro_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Totoro_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Totoro_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Totoro_Chiseled_Diamond,
		),
	),
	MIKU(
		R.style.ThemeOverlay_Kotatsu_Miku,
		R.string.theme_name_miku,
		R.style.Theme_Kotatsu_Splash_Miku,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Miku_Clover4,
			R.style.Theme_Kotatsu_Splash_Miku_Flower6,
			R.style.Theme_Kotatsu_Splash_Miku_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Miku_Sunny,
			R.style.Theme_Kotatsu_Splash_Miku_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Miku_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Miku_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Miku_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Miku_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Miku_Chiseled_Diamond,
		),
	),
	RENA(
		R.style.ThemeOverlay_Kotatsu_Asuka,
		R.string.theme_name_asuka,
		R.style.Theme_Kotatsu_Splash_Asuka,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Asuka_Clover4,
			R.style.Theme_Kotatsu_Splash_Asuka_Flower6,
			R.style.Theme_Kotatsu_Splash_Asuka_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Asuka_Sunny,
			R.style.Theme_Kotatsu_Splash_Asuka_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Asuka_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Asuka_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Asuka_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Asuka_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Asuka_Chiseled_Diamond,
		),
	),
	FROG(
		R.style.ThemeOverlay_Kotatsu_Mion,
		R.string.theme_name_mion,
		R.style.Theme_Kotatsu_Splash_Mion,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Mion_Clover4,
			R.style.Theme_Kotatsu_Splash_Mion_Flower6,
			R.style.Theme_Kotatsu_Splash_Mion_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Mion_Sunny,
			R.style.Theme_Kotatsu_Splash_Mion_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Mion_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Mion_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Mion_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Mion_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Mion_Chiseled_Diamond,
		),
	),
	BLUEBERRY(
		R.style.ThemeOverlay_Kotatsu_Rikka,
		R.string.theme_name_rikka,
		R.style.Theme_Kotatsu_Splash_Rikka,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Rikka_Clover4,
			R.style.Theme_Kotatsu_Splash_Rikka_Flower6,
			R.style.Theme_Kotatsu_Splash_Rikka_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Rikka_Sunny,
			R.style.Theme_Kotatsu_Splash_Rikka_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Rikka_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Rikka_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Rikka_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Rikka_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Rikka_Chiseled_Diamond,
		),
	),
	SAKURA(
		R.style.ThemeOverlay_Kotatsu_Sakura,
		R.string.theme_name_sakura,
		R.style.Theme_Kotatsu_Splash_Sakura,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Sakura_Clover4,
			R.style.Theme_Kotatsu_Splash_Sakura_Flower6,
			R.style.Theme_Kotatsu_Splash_Sakura_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Sakura_Sunny,
			R.style.Theme_Kotatsu_Splash_Sakura_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Sakura_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Sakura_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Sakura_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Sakura_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Sakura_Chiseled_Diamond,
		),
	),
	MAMIMI(
		R.style.ThemeOverlay_Kotatsu_Mamimi,
		R.string.theme_name_mamimi,
		R.style.Theme_Kotatsu_Splash_Mamimi,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Mamimi_Clover4,
			R.style.Theme_Kotatsu_Splash_Mamimi_Flower6,
			R.style.Theme_Kotatsu_Splash_Mamimi_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Mamimi_Sunny,
			R.style.Theme_Kotatsu_Splash_Mamimi_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Mamimi_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Mamimi_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Mamimi_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Mamimi_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Mamimi_Chiseled_Diamond,
		),
	),
	KANADE(
		R.style.ThemeOverlay_Kotatsu_Kanade,
		R.string.theme_name_kanade,
		R.style.Theme_Kotatsu_Splash_Kanade,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Kanade_Clover4,
			R.style.Theme_Kotatsu_Splash_Kanade_Flower6,
			R.style.Theme_Kotatsu_Splash_Kanade_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Kanade_Sunny,
			R.style.Theme_Kotatsu_Splash_Kanade_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Kanade_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Kanade_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Kanade_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Kanade_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Kanade_Chiseled_Diamond,
		),
	),
	ITSUKA(
		R.style.ThemeOverlay_Kotatsu_Itsuka,
		R.string.theme_name_itsuka,
		R.style.Theme_Kotatsu_Splash_Itsuka,
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Itsuka_Clover4,
			R.style.Theme_Kotatsu_Splash_Itsuka_Flower6,
			R.style.Theme_Kotatsu_Splash_Itsuka_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Itsuka_Sunny,
			R.style.Theme_Kotatsu_Splash_Itsuka_Diamond,
		),
		intArrayOf(
			R.style.Theme_Kotatsu_Splash_Itsuka_Chiseled_Clover4,
			R.style.Theme_Kotatsu_Splash_Itsuka_Chiseled_Flower6,
			R.style.Theme_Kotatsu_Splash_Itsuka_Chiseled_SoftBurst,
			R.style.Theme_Kotatsu_Splash_Itsuka_Chiseled_Sunny,
			R.style.Theme_Kotatsu_Splash_Itsuka_Chiseled_Diamond,
		),
	),
	;

	@StyleRes
	fun randomSplashStyleResId(style: AppIconStyle = AppIconStyle.CALLIGRAPHIC): Int = when (style) {
		AppIconStyle.CALLIGRAPHIC -> splashShapeStyleResIds.random()
		AppIconStyle.CHISELED -> chiseledSplashShapeStyleResIds.random()
	}

	companion object {

		val default: ColorScheme
			get() = if (DynamicColors.isDynamicColorAvailable()) {
				EXPRESSIVE
			} else {
				KUROHA
			}

		fun getAvailableList(): List<ColorScheme> {
			val list = ColorScheme.entries.toMutableList()
			if (!DynamicColors.isDynamicColorAvailable()) {
				list.remove(EXPRESSIVE)
			}
			return list
		}

		fun safeValueOf(name: String): ColorScheme? {
			return ColorScheme.entries.find(name)
		}
	}
}
