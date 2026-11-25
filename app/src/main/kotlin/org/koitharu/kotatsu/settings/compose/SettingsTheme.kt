package org.koitharu.kotatsu.settings.compose

import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.main.ui.nav.composeColorSchemeFromTheme

/**
 * Google Sans variable font with clean geometric letterforms across standard Material 3 weights.
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSans: FontFamily
	@Composable
	get() = remember {
		FontFamily(
			Font(R.font.google_sans, weight = FontWeight.Normal, variationSettings = weightOf(400)),
			Font(R.font.google_sans, weight = FontWeight(440), variationSettings = weightOf(440)),
			Font(R.font.google_sans, weight = FontWeight.Medium, variationSettings = weightOf(500)),
			Font(R.font.google_sans, weight = FontWeight.SemiBold, variationSettings = weightOf(600)),
			Font(R.font.google_sans, weight = FontWeight.Bold, variationSettings = weightOf(700)),
		)
	}

private fun weightOf(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

@Composable
private fun kurohaTypography(family: FontFamily = GoogleSans): Typography = remember(family) {
	val noPadding = PlatformTextStyle(includeFontPadding = false)
	Typography(
		displayLarge = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.02).sp,
			platformStyle = noPadding,
		),
		displayMedium = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
			platformStyle = noPadding,
		),
		displaySmall = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
			platformStyle = noPadding,
		),
		headlineLarge = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
			platformStyle = noPadding,
		),
		headlineMedium = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
			platformStyle = noPadding,
		),
		headlineSmall = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
			platformStyle = noPadding,
		),
		titleLarge = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Bold,
			fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
			platformStyle = noPadding,
		),
		titleMedium = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Normal,
			fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
			platformStyle = noPadding,
		),
		titleSmall = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Normal,
			fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
			platformStyle = noPadding,
		),
		bodyLarge = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Normal,
			fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp,
			platformStyle = noPadding,
		),
		bodyMedium = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Normal,
			fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
			platformStyle = noPadding,
		),
		bodySmall = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Normal,
			fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
			platformStyle = noPadding,
		),
		labelLarge = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Medium,
			fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp,
			platformStyle = noPadding,
		),
		labelMedium = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Medium,
			fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp,
			platformStyle = noPadding,
		),
		labelSmall = TextStyle(
			fontFamily = family, fontWeight = FontWeight.Medium,
			fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
			platformStyle = noPadding,
		),
	)
}

/**
 * The app's corner scale, mirroring the Views side (`ShapeAppearance.Kotatsu.Corner*` in
 * styles.xml) so a Compose dialog and an XML bottom sheet round identically.
 *
 * Compose's own defaults are medium=12dp / large=16dp, which is *not* what the rest of the app
 * uses — without this, every Compose card and sheet was subtly squarer than its Views twin.
 */
private val KurohaShapes = Shapes(
	extraSmall = RoundedCornerShape(4.dp),
	small = RoundedCornerShape(8.dp),
	medium = RoundedCornerShape(16.dp),
	large = RoundedCornerShape(24.dp),
	extraLarge = RoundedCornerShape(28.dp),
)

/**
 * MaterialExpressiveTheme wrapper that pulls colors from the host Android theme and uses
 * the project's rounded variable-font typography. Use this at the top of any
 * Compose subtree we host inside an existing Fragment/Activity so it inherits
 * the user's chosen theme (Dynamic, Monet, AMOLED, etc).
 *
 * Going through [MaterialExpressiveTheme] (rather than plain `MaterialTheme`) is what gives every
 * material3 component in the app the Expressive spring motion and shapes by default — sheets,
 * dialogs, switches, sliders, chips and the nav bar all pick it up with no per-screen work.
 * Read the springs back out via `MaterialTheme.motionScheme` instead of hand-writing `spring(...)`.
 */
@Composable
fun KurohaTheme(content: @Composable () -> Unit) {
	val ctx = LocalContext.current
	val isDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
		Configuration.UI_MODE_NIGHT_YES
	val scheme = remember(ctx, isDark) { composeColorSchemeFromTheme(ctx, isDark) }
	val family = GoogleSans
	val typography = kurohaTypography(family)
	MaterialExpressiveTheme(
		colorScheme = scheme,
		motionScheme = MotionScheme.expressive(),
		shapes = KurohaShapes,
		typography = typography,
		content = content,
	)
}
