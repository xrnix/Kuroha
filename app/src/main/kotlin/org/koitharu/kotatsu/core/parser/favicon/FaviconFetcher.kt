package org.koitharu.kotatsu.core.parser.favicon

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import coil3.ColorImage
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri
import coil3.toBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toOkioPath
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.EmptyMangaRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.parser.external.ExternalMangaRepository
import org.koitharu.kotatsu.core.util.MimeTypes
import org.koitharu.kotatsu.core.util.ext.fetch
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.toMimeTypeOrNull
import org.koitharu.kotatsu.local.data.FaviconCache
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageCache
import org.koitharu.kotatsu.mihon.MihonExtensionLoader
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.mihon.MihonMangaRepository
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.File
import javax.inject.Inject
import coil3.Uri as CoilUri

class FaviconFetcher(
	private val uri: Uri,
	private val options: Options,
	private val imageLoader: ImageLoader,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val localStorageCache: LocalStorageCache,
	private val mihonExtensionManager: MihonExtensionManager,
) : Fetcher {

	override suspend fun fetch(): FetchResult? {
		val ssp = uri.schemeSpecificPart
		if (ssp.startsWith(FAVICON_PACKAGE_PREFIX)) {
			return fetchPackageIcon(ssp.removePrefix(FAVICON_PACKAGE_PREFIX))
		}
		resolveMihonSource(ssp)?.let { return fetchMihonIcon(it) }

		val mangaSource = MangaSource(ssp)

		return when (val repo = mangaRepositoryFactory.create(mangaSource)) {
			is ParserMangaRepository -> fetchParserFavicon(repo)
			is MihonMangaRepository -> fetchMihonIcon(repo)
			is ExternalMangaRepository -> fetchPluginIcon(repo)
			is EmptyMangaRepository -> {
				val resolvedMihon = resolveMihonSource(mangaSource.name)
				if (resolvedMihon != null) {
					fetchMihonIcon(resolvedMihon)
				} else {
					ImageFetchResult(
						image = ColorImage(Color.WHITE),
						isSampled = false,
						dataSource = DataSource.MEMORY,
					)
				}
			}

			is LocalMangaRepository -> imageLoader.fetch(R.drawable.ic_storage, options)

			else -> {
				val resolvedMihon = resolveMihonSource(mangaSource.name)
				if (resolvedMihon != null) {
					fetchMihonIcon(resolvedMihon)
				} else {
					imageLoader.fetch(R.drawable.ic_manga_source, options)
				}
			}
		}
	}

	private suspend fun fetchParserFavicon(repository: ParserMangaRepository): FetchResult {
		val sizePx = maxOf(
			options.size.width.pxOrElse { FALLBACK_SIZE },
			options.size.height.pxOrElse { FALLBACK_SIZE },
		)
		val cacheKey = options.diskCacheKey ?: "${repository.source.name}_$sizePx"
		if (options.diskCachePolicy.readEnabled) {
			localStorageCache[cacheKey]?.let { file ->
				return SourceFetchResult(
					source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
					mimeType = MimeTypes.probeMimeType(file)?.toString(),
					dataSource = DataSource.DISK,
				)
			}
		}
		var favicons = repository.getFavicons()
		var lastError: Exception? = null
		while (favicons.isNotEmpty()) {
			currentCoroutineContext().ensureActive()
			val icon = favicons.find(sizePx) ?: throwNSEE(lastError)
			try {
				val result = imageLoader.fetch(icon.url, options)
				if (result != null) {
					return if (options.diskCachePolicy.writeEnabled) {
						writeToCache(cacheKey, result)
					} else {
						result
					}
				} else {
					favicons -= icon
				}
			} catch (e: CloudFlareProtectedException) {
				throw e
			} catch (e: IOException) {
				lastError = e
				favicons -= icon
			}
		}
		throwNSEE(lastError)
	}

	private suspend fun fetchPluginIcon(repository: ExternalMangaRepository): FetchResult {
		val source = repository.source
		val pm = options.context.packageManager
		val icon = runInterruptible {
			val provider = pm.resolveContentProvider(source.authority, 0)
			provider?.loadIcon(pm) ?: pm.getApplicationIcon(source.packageName)
		}
		return ImageFetchResult(
			image = icon.nonAdaptive().asImage(),
			isSampled = false,
			dataSource = DataSource.DISK,
		)
	}

	private suspend fun fetchMihonIcon(repository: MihonMangaRepository): FetchResult =
		fetchMihonIcon(repository.source)

	private suspend fun fetchMihonIcon(source: MihonMangaSource): FetchResult = fetchPackageIcon(source.pkgName)

	private suspend fun fetchPackageIcon(pkgName: String): FetchResult {
		val icon = runCatchingCancellable {
			runInterruptible {
				options.context.packageManager.getApplicationIcon(pkgName)
			}
		}.getOrNull() ?: runCatchingCancellable {
			runInterruptible {
				val ctx = options.context
				val extFile = File(MihonExtensionLoader.getPrivateExtensionDir(ctx), "$pkgName.ext")
				if (!extFile.isFile) return@runInterruptible null
				val pm = ctx.packageManager
				@Suppress("DEPRECATION")
				val pkgInfo = pm.getPackageArchiveInfo(extFile.absolutePath, PackageManager.GET_META_DATA) ?: return@runInterruptible null
				val appInfo = pkgInfo.applicationInfo ?: return@runInterruptible null
				if (appInfo.sourceDir == null) appInfo.sourceDir = extFile.absolutePath
				if (appInfo.publicSourceDir == null) appInfo.publicSourceDir = extFile.absolutePath
				appInfo.loadIcon(pm)
			}
		}.getOrNull() ?: return requireNotNull(imageLoader.fetch(R.drawable.ic_manga_source, options))

		return ImageFetchResult(
			image = icon.nonAdaptive().asImage(),
			isSampled = false,
			dataSource = DataSource.DISK,
		)
	}

	private suspend fun resolveMihonSource(name: String): MihonMangaSource? {
		if (!name.startsWith("MIHON_")) return null
		mihonExtensionManager.ensureReady()
		val sourceId = name.removePrefix("MIHON_").substringBefore(':').toLongOrNull()
		val existing = sourceId?.let { mihonExtensionManager.getMihonMangaSourceById(it) }
			?: mihonExtensionManager.getMihonMangaSourceByName(name)
		if (existing != null) return existing
		mihonExtensionManager.ensureReady(forceRefresh = true)
		return sourceId?.let { mihonExtensionManager.getMihonMangaSourceById(it) }
			?: mihonExtensionManager.getMihonMangaSourceByName(name)
	}

	private suspend fun writeToCache(key: String, result: FetchResult): FetchResult = runCatchingCancellable {
		when (result) {
			is ImageFetchResult -> {
				if (result.dataSource == DataSource.NETWORK) {
					localStorageCache.set(key, result.image.toBitmap()).asFetchResult()
				} else {
					result
				}
			}

			is SourceFetchResult -> {
				if (result.dataSource == DataSource.NETWORK) {
					result.source.source().use {
						localStorageCache.set(key, it, result.mimeType?.toMimeTypeOrNull()).asFetchResult()
					}
				} else {
					result
				}
			}
		}
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrDefault(result)

	private fun File.asFetchResult() = SourceFetchResult(
		source = ImageSource(toOkioPath(), FileSystem.SYSTEM),
		mimeType = MimeTypes.probeMimeType(this)?.toString(),
		dataSource = DataSource.DISK,
	)

	class Factory @Inject constructor(
		private val mangaRepositoryFactory: MangaRepository.Factory,
		@FaviconCache private val faviconCache: LocalStorageCache,
		private val mihonExtensionManager: MihonExtensionManager,
	) : Fetcher.Factory<CoilUri> {

		override fun create(
			data: CoilUri,
			options: Options,
			imageLoader: ImageLoader
		): Fetcher? = if (data.scheme == URI_SCHEME_FAVICON) {
			FaviconFetcher(
				uri = data.toAndroidUri(),
				options = options,
				imageLoader = imageLoader,
				mangaRepositoryFactory = mangaRepositoryFactory,
				localStorageCache = faviconCache,
				mihonExtensionManager = mihonExtensionManager,
			)
		} else {
			null
		}
	}

	private companion object {

		const val FALLBACK_SIZE = 9999 // largest icon

		private fun throwNSEE(lastError: Exception?): Nothing {
			if (lastError != null) {
				throw lastError
			} else {
				throw NoSuchElementException("No favicons found")
			}
		}

		private fun Drawable.nonAdaptive() =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
				LayerDrawable(arrayOf(background, foreground))
			} else {
				this
			}

	}
}

