package org.koitharu.kotatsu.core.model

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import eu.kanade.tachiyomi.source.online.HttpSource
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.parser.external.ExternalMangaSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.core.util.ext.isHttpUrl
import org.koitharu.kotatsu.core.util.ext.toLocale
import org.koitharu.kotatsu.core.util.ext.toLocaleOrNull
import org.koitharu.kotatsu.core.parser.bundle.ActiveParsers
import org.koitharu.kotatsu.extensions.runtime.getExternalExtensionLanguageDisplayName
import org.koitharu.kotatsu.mihon.MihonExtensionLoader
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.util.splitTwoParts
import java.util.Locale

typealias MangaSource = org.koitharu.kotatsu.parsers.model.MangaSource

data object LocalMangaSource : MangaSource {
	override val name = "LOCAL"
}

data object UnknownMangaSource : MangaSource {
	override val name = "UNKNOWN"
}

data object TestMangaSource : MangaSource {
	override val name = "TEST"
}

class MissingMangaSource(
	override val name: String,
	val cachedTitle: String? = null,
) : MangaSource {

	override fun equals(other: Any?): Boolean = other is MissingMangaSource && other.name == name

	override fun hashCode(): Int = name.hashCode()

	override fun toString(): String = "MissingMangaSource(name=$name, cachedTitle=$cachedTitle)"
}

fun MangaSource(name: String?): MangaSource {
	when (name ?: return UnknownMangaSource) {
		UnknownMangaSource.name -> return UnknownMangaSource
		LocalMangaSource.name -> return LocalMangaSource
		TestMangaSource.name -> return TestMangaSource
	}
	if (name.startsWith("content:")) {
		val parts = name.substringAfter(':').splitTwoParts('/') ?: return UnknownMangaSource
		return ExternalMangaSource(packageName = parts.first, authority = parts.second)
	}
	if (name.startsWith("MIHON_")) {
		val sourceId = name.removePrefix("MIHON_").substringBefore(':').toLongOrNull()
		if (sourceId != null) {
			return MihonExtensionManager.getById(sourceId) ?: MissingMangaSource(name)
		}
		return MihonExtensionManager.getByName(name) ?: MissingMangaSource(name)
	}
	ActiveParsers.sources.forEach {
		if (it.name == name) return it
	}
	return MissingMangaSource(name)
}

fun MangaSource(name: String?, sourceTitle: String?): MangaSource {
	val source = MangaSource(name)
	return if (source is MissingMangaSource && !sourceTitle.isNullOrBlank()) {
		MissingMangaSource(source.name, sourceTitle)
	} else {
		source
	}
}

fun Collection<String>.toMangaSources() = map(::MangaSource)

fun MangaSource.isNsfw(): Boolean = when (this) {
	is MangaSourceInfo -> mangaSource.isNsfw()
	is ParserMangaSource -> contentType == ContentType.HENTAI
	is MihonMangaSource -> contentWarning == ContentWarning.NSFW
	else -> false
}

fun MangaSource.isSafe(): Boolean = when (val source = unwrap()) {
	is ParserMangaSource -> source.contentType != ContentType.HENTAI
	is MihonMangaSource -> source.contentWarning == ContentWarning.SAFE
	else -> true
}

fun List<MangaSource>.forSuggestions(excludeNsfw: Boolean): List<MangaSource> =
	if (excludeNsfw) filter { it.isSafe() }.ifEmpty { this } else this

val MangaSource.homeUrl: String?
	get() = when (val source = unwrap()) {
		is MihonMangaSource -> (source.catalogueSource as? HttpSource)?.baseUrl
		else -> null
	}?.takeIf { it.isHttpUrl() }

val MangaSource.isBroken: Boolean
	get() = when (unwrap()) {
		is MissingMangaSource -> true
		UnknownMangaSource -> true
		else -> false
	}

val MangaSource.isLocal: Boolean
	get() = unwrap() == LocalMangaSource

val MangaSource.isNovelSource: Boolean
	get() = false

@get:StringRes
val ContentType.titleResId
	get() = when (this) {
		ContentType.MANGA -> R.string.content_type_manga
		ContentType.HENTAI -> R.string.content_type_hentai
		ContentType.COMICS -> R.string.content_type_comics
		ContentType.OTHER -> R.string.content_type_other
		ContentType.MANHWA -> R.string.content_type_manhwa
		ContentType.MANHUA -> R.string.content_type_manhua
		ContentType.NOVEL -> R.string.content_type_novel
		ContentType.ONE_SHOT -> R.string.content_type_one_shot
		ContentType.DOUJINSHI -> R.string.content_type_doujinshi
		ContentType.IMAGE_SET -> R.string.content_type_image_set
		ContentType.ARTIST_CG -> R.string.content_type_artist_cg
		ContentType.GAME_CG -> R.string.content_type_game_cg
	}

tailrec fun MangaSource.unwrap(): MangaSource = if (this is MangaSourceInfo) {
	mangaSource.unwrap()
} else {
	this
}

fun MangaSource.getLocale(): Locale? = (unwrap() as? ParserMangaSource)?.locale?.toLocaleOrNull()

fun MangaSource.getSummary(context: Context): String? = when (val source = unwrap()) {
	is ParserMangaSource -> {
		val type = context.getString(source.contentType.titleResId)
		val locale = source.locale.toLocale().getDisplayName(context)
		context.getString(R.string.source_summary_pattern, type, locale)
	}

	is MihonMangaSource -> buildString {
		append(getExternalExtensionLanguageDisplayName(source.language))
		runCatching { context.packageManager.getPackageInfo(source.pkgName, 0).versionName }
			.getOrNull()
			?.let { append(" • ").append(it) }
		val settings = AppSettings(context.applicationContext)
		val repoLabel = settings.getExtensionStoreLabel(
			source.pkgName,
			MihonExtensionLoader.getPackageSignatures(context, source.pkgName),
		)
		repoLabel?.let { append(" • ").append(it) }
	}

	is ExternalMangaSource -> context.getString(R.string.external_source)

	is MissingMangaSource -> {
		if (source.name.startsWith("MIHON_")) {
			context.getString(R.string.external_source)
		} else {
			null
		}
	}

	else -> null
}

fun MangaSource.getTitle(context: Context): String = when (val source = unwrap()) {
	is ParserMangaSource -> source.title
	LocalMangaSource -> context.getString(R.string.local_storage)
	TestMangaSource -> context.getString(R.string.test_parser)
	is MihonMangaSource -> source.displayName
	is MissingMangaSource -> source.resolveDisplayName(context)
	is ExternalMangaSource -> source.resolveName(context)
	else -> context.getString(R.string.unknown)
}

fun MangaSource.isExternalSource(): Boolean = when (val source = unwrap()) {
	is MihonMangaSource -> true
	is ExternalMangaSource -> true
	is MissingMangaSource -> source.name.startsWith("MIHON_")
	else -> false
}

fun MangaSource.getStoredTitleOrNull(): String? = when (val source = unwrap()) {
	is ParserMangaSource -> source.title
	is MihonMangaSource -> source.displayName
	is MissingMangaSource -> source.cachedDisplayNameOrNull() ?: source.liveDisplayNameOrNull()
	LocalMangaSource -> null
	else -> null
}

private fun MissingMangaSource.liveDisplayNameOrNull(): String? {
	if (!name.startsWith("MIHON_")) return null
	val id = name.removePrefix("MIHON_").substringBefore(':').toLongOrNull() ?: return null
	return MihonExtensionManager.getById(id)?.displayName
}

private fun MissingMangaSource.resolveDisplayName(context: Context): String {
	if (name.startsWith("MIHON_")) {
		val cachedName = cachedDisplayNameOrNull()
		return if (cachedName != null) {
			context.getString(R.string.missing_extension_source_pattern, cachedName)
		} else {
			context.getString(R.string.missing_extension_source_pattern, context.getString(R.string.unknown))
		}
	}
	return name
}

private fun MissingMangaSource.cachedDisplayNameOrNull(): String? {
	cachedTitle?.ifBlank { null }?.let { return it }
	if (!name.startsWith("MIHON_")) return null
	val parts = name.removePrefix("MIHON_").split(':', limit = 2)
	return parts.getOrNull(1)?.ifBlank { null }
}

fun SpannableStringBuilder.appendIcon(textView: TextView, @DrawableRes resId: Int): SpannableStringBuilder {
	val icon = ContextCompat.getDrawable(textView.context, resId) ?: return this
	icon.setTintList(textView.textColors)
	val size = textView.lineHeight
	icon.setBounds(0, 0, size, size)
	val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		ImageSpan.ALIGN_CENTER
	} else {
		ImageSpan.ALIGN_BOTTOM
	}
	return inSpans(ImageSpan(icon, alignment)) { append(' ') }
}
