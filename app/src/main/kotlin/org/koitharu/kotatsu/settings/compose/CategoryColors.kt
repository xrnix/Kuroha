package org.koitharu.kotatsu.settings.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Background + foreground tint pair used for the circular icon container on each
 * settings card. Chosen for high legibility and a cohesive mineral/jewel palette
 * across light and dark themes.
 */
@Immutable
data class CategoryIconColors(
	val container: Color,
	val onContainer: Color,
)

object CategoryPalette {
	// Light-mode mineral/jewel pastel containers with deep ink foregrounds.
	val Appearance = CategoryIconColors(Color(0xFFE5DEFA), Color(0xFF281B54))
	val Extensions = CategoryIconColors(Color(0xFFFAD8E2), Color(0xFF471625))
	val Reader = CategoryIconColors(Color(0xFFCFE6F7), Color(0xFF0A2E47))
	val Storage = CategoryIconColors(Color(0xFFC2EFE7), Color(0xFF07362F))
	val Downloads = CategoryIconColors(Color(0xFFFBE2BD), Color(0xFF3E2907))
	val Backup = CategoryIconColors(Color(0xFFCFEBD0), Color(0xFF103613))
	val Tracker = CategoryIconColors(Color(0xFFFAD8D6), Color(0xFF4D1516))
	val Services = CategoryIconColors(Color(0xFFDCE1FA), Color(0xFF182257))
	val About = CategoryIconColors(Color(0xFFD8E2EA), Color(0xFF1E272E))
	val Sync = CategoryIconColors(Color(0xFFCCE7F5), Color(0xFF0E3042))
	val Privacy = CategoryIconColors(Color(0xFFFADCCF), Color(0xFF471D0D))

	// Dark-mode variants — rich muted jewel containers with crisp pastel foregrounds.
	private val AppearanceDark = CategoryIconColors(Color(0xFF453B6B), Color(0xFFE5DEFA))
	private val ExtensionsDark = CategoryIconColors(Color(0xFF6B3848), Color(0xFFFAD8E2))
	private val ReaderDark = CategoryIconColors(Color(0xFF1F4E6D), Color(0xFFCFE6F7))
	private val StorageDark = CategoryIconColors(Color(0xFF1A524B), Color(0xFFC2EFE7))
	private val DownloadsDark = CategoryIconColors(Color(0xFF5F4418), Color(0xFFFBE2BD))
	private val BackupDark = CategoryIconColors(Color(0xFF2E5332), Color(0xFFCFEBD0))
	private val TrackerDark = CategoryIconColors(Color(0xFF732B2C), Color(0xFFFAD8D6))
	private val ServicesDark = CategoryIconColors(Color(0xFF333D79), Color(0xFFDCE1FA))
	private val AboutDark = CategoryIconColors(Color(0xFF3A434B), Color(0xFFD8E2EA))
	private val SyncDark = CategoryIconColors(Color(0xFF284B5C), Color(0xFFCCE7F5))
	private val PrivacyDark = CategoryIconColors(Color(0xFF6A3623), Color(0xFFFADCCF))

	@Composable
	fun forKey(key: String): CategoryIconColors {
		val dark = MaterialTheme.colorScheme.surface.let {
			(it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
		}
		return when (key) {
			"appearance" -> if (dark) AppearanceDark else Appearance
			"extensions", "sources" -> if (dark) ExtensionsDark else Extensions
			"reader" -> if (dark) ReaderDark else Reader
			"storage", "network" -> if (dark) StorageDark else Storage
			"downloads" -> if (dark) DownloadsDark else Downloads
			"backup" -> if (dark) BackupDark else Backup
			"tracker" -> if (dark) TrackerDark else Tracker
			"services" -> if (dark) ServicesDark else Services
			"privacy" -> if (dark) PrivacyDark else Privacy
			"about" -> if (dark) AboutDark else About
			"sync" -> if (dark) SyncDark else Sync
			else -> if (dark) AboutDark else About
		}
	}
}
