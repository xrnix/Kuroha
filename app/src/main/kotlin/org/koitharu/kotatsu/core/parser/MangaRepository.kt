package org.koitharu.kotatsu.core.parser

import android.content.Context
import androidx.annotation.AnyThread
import androidx.collection.ArrayMap
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Headers
import okhttp3.Response
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.MangaSource as ResolveMangaSource
import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.core.model.MissingMangaSource
import org.koitharu.kotatsu.core.model.TestMangaSource
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.parser.bundle.ActiveParsers
import org.koitharu.kotatsu.core.parser.bundle.LiveMangaParser
import org.koitharu.kotatsu.core.parser.bundle.ParsersUpdater
import org.koitharu.kotatsu.core.parser.external.ExternalMangaRepository
import org.koitharu.kotatsu.core.parser.external.ExternalMangaSource
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.mihon.LazyMihonMangaRepository
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.mihon.MihonMangaRepository
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface FreshMangaDetailsRepository {
	suspend fun getFreshDetails(manga: Manga): Manga
}

interface MangaRepository {

	val source: MangaSource

	val sortOrders: Set<SortOrder>

	var defaultSortOrder: SortOrder

	val filterCapabilities: MangaListFilterCapabilities

	suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga>

	suspend fun getDetails(manga: Manga): Manga

	suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	/**
	 * [manga] is the one [chapter] belongs to. Like Mihon, a chapter url is only unique within its
	 * manga (AllAnime uses the bare chapter number), so per-chapter state is keyed by both.
	 */
	suspend fun getPages(manga: Manga, chapter: MangaChapter): List<MangaPage> = getPages(chapter)

	suspend fun getPageUrl(page: MangaPage): String

	/** Public web URL for a single [chapter] of [manga], or null for sources without a browsable chapter page. */
	suspend fun getChapterUrl(manga: Manga, chapter: MangaChapter): String? = null

	/**
	 * Returns [chapter]'s content as html for text sources (web novels), or null for image sources.
	 * Consumed by the native epub reader, which renders the html into a Spanned.
	 */
	suspend fun getChapterHtml(chapter: MangaChapter): String? = null

	/** Returns extension-specific HTTP headers for the image at [imageUrl], or null for non-extension sources. */
	suspend fun getImageRequestHeaders(imageUrl: String, page: MangaPage): Headers? = null

	/**
	 * Returns the full image [Response] for [pageUrl], or null to fall back to the default fetch path.
	 * Extensions may override [HttpSource.getImage] to decrypt or transform scrambled images;
	 * this hook ensures those transforms are applied before the bytes hit the disk cache.
	 */
	suspend fun getImageStream(pageUrl: String, page: MangaPage): Response? = null

	/**
	 * Fetches a non-page image (e.g. a cover/thumbnail) through the source's own client + headers,
	 * or null to fall back to the default fetch path.
	 */
	suspend fun getCoverStream(url: String): Response? = null

	suspend fun getFilterOptions(): MangaListFilterOptions

	suspend fun getRelated(seed: Manga): List<Manga>

	suspend fun find(manga: Manga): Manga? {
		val list = getList(0, SortOrder.RELEVANCE, MangaListFilter(query = manga.title))
		return list.find { x -> x.id == manga.id }
	}

	@Singleton
	class Factory @Inject constructor(
		@ApplicationContext private val context: Context,
		private val localMangaRepository: LocalMangaRepository,
		private val loaderContext: MangaLoaderContext,
		private val contentCache: MemoryContentCache,
		private val mirrorSwitcher: MirrorSwitcher,
		private val parsersUpdater: ParsersUpdater,
		private val mihonExtensionManager: MihonExtensionManager,
	) {

		private val cache = ArrayMap<MangaSource, WeakReference<MangaRepository>>()

		@AnyThread
		fun create(source: MangaSource): MangaRepository {
			val unwrapped = resolveFreshSource(unwrap(source))
			val isExternalMissing = unwrapped is MissingMangaSource && unwrapped.name.startsWith("MIHON_")
			when (unwrapped) {
				LocalMangaSource -> return localMangaRepository
				UnknownMangaSource -> return EmptyMangaRepository(unwrapped)
				is MihonMangaSource -> mihonExtensionManager.initialize()
			}
			if (isExternalMissing) {
				cache[unwrapped]?.get()?.let { cached ->
					if (cached !is EmptyMangaRepository) return cached
				}
				return synchronized(cache) {
					cache[unwrapped]?.get()?.let { cached ->
						if (cached !is EmptyMangaRepository) return cached
					}
					val lazyRepo = LazyMihonMangaRepository(
						source = unwrapped,
						extensionManager = mihonExtensionManager,
						cache = contentCache,
						context = context,
					)
					cache[unwrapped] = WeakReference(lazyRepo)
					lazyRepo
				}
			}
			cache[unwrapped]?.get()?.let { return it }
			return synchronized(cache) {
				cache[unwrapped]?.get()?.let { return it }
				val repository = createRepository(unwrapped)
				if (repository != null) {
					cache[unwrapped] = WeakReference(repository)
					repository
				} else {
					EmptyMangaRepository(unwrapped).also {
						cache[unwrapped] = WeakReference(it)
					}
				}
			}
		}

		private fun unwrap(source: MangaSource): MangaSource = when (source) {
			is MangaSourceInfo -> source.mangaSource
			else -> source
		}

		private fun resolveFreshSource(source: MangaSource): MangaSource {
			if (source is MissingMangaSource && source.name.startsWith("MIHON_")) {
				mihonExtensionManager.initialize()
				return ResolveMangaSource(source.name)
			}
			return source
		}

		private fun createRepository(source: MangaSource): MangaRepository? = when (source) {
			is ParserMangaSource -> if (ActiveParsers.findSource(source.name) != null) {
				ParserMangaRepository(
					parser = LiveMangaParser(source.name, loaderContext, parsersUpdater),
					cache = contentCache,
					mirrorSwitcher = mirrorSwitcher,
				)
			} else {
				null
			}

			is MihonMangaSource -> MihonMangaRepository(
				source = source,
				cache = contentCache,
				context = context,
			)

			TestMangaSource -> TestMangaRepository(
				loaderContext = loaderContext,
				cache = contentCache,
			)

			is ExternalMangaSource -> if (source.isAvailable(context)) {
				ExternalMangaRepository(
					contentResolver = context.contentResolver,
					source = source,
					cache = contentCache,
				)
			} else {
				EmptyMangaRepository(source)
			}

			else -> null
		}
	}
}
