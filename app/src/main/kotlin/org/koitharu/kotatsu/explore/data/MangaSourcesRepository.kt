package org.koitharu.kotatsu.explore.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.LocalizedAppContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.dao.MangaSourcesDao
import org.koitharu.kotatsu.core.db.entity.MangaSourceEntity
import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.parser.bundle.ActiveParsers
import org.koitharu.kotatsu.core.parser.external.ExternalMangaSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.ui.util.ReversibleHandle
import org.koitharu.kotatsu.core.util.ext.flattenLatest
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import android.content.SharedPreferences
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.core.model.unwrap
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.sourcescore.domain.SourceRanker
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedSource(
	val source: MangaSource,
	val languageSubtitle: String?,
)

@Singleton
class MangaSourcesRepository @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val mihonExtensionManagerProvider: javax.inject.Provider<MihonExtensionManager>,
	/**
	 * Nullable with a default so [org.koitharu.kotatsu.backups.domain.AppBackupAgent] can keep
	 * constructing this by hand: a backup agent runs in its own process with no Hilt graph, and it
	 * has no use for score ordering. Dagger ignores the default and injects the real instance.
	 */
	private val sourceRanker: SourceRanker? = null,
) {

	private val mihonExtensionManager: MihonExtensionManager?
		get() = runCatching { mihonExtensionManagerProvider.get() }.getOrNull()

	constructor(
		context: Context,
		db: MangaDatabase,
		settings: AppSettings,
	) : this(
		context = context,
		db = db,
		settings = settings,
		mihonExtensionManagerProvider = javax.inject.Provider { MihonExtensionManager.getInstance() },
		sourceRanker = null,
	)

	/** The [ActiveParsers.generation] whose sources the table has been brought up to. */
	private val assimilatedGeneration = AtomicInteger(-1)
	private val dao: MangaSourcesDao
		get() = db.getSourcesDao()

	/** The sources of the parsers in use. Not a list to keep: newer parsers can come with others, at any time. */
	val allMangaSources: Set<ParserMangaSource>
		get() = ActiveParsers.workingSources

	fun setDefaultBrowseSortOrder(order: org.koitharu.kotatsu.parsers.model.SortOrder) {
		settings.defaultBrowseSortOrder = order
		val manager = mihonExtensionManager ?: return
		for (source in manager.getMihonMangaSources()) {
			org.koitharu.kotatsu.core.prefs.SourceSettings(context, source).defaultSortOrder = order
		}
	}

	suspend fun getEnabledSources(): List<MangaSource> {
		assimilateNewSources()
		val order = settings.sourcesSortOrder
		val enabledNative = dao.findAll(!settings.isAllSourcesEnabled, order).toSources(settings.isNsfwContentDisabled, order)
		val external = getExternalSources()
		val mihon = getMihonSources()
		val list = ArrayList<MangaSource>(enabledNative.size + external.size + mihon.size)
		external.forEach { list.add(it) }
		list.addAll(mihon)
		enabledNative.forEach { list.add(it.mangaSource) }
		return list
	}

	suspend fun getPinnedSources(): Set<MangaSource> {
		assimilateNewSources()
		val skipNsfw = settings.isNsfwContentDisabled
		val nativePinned = dao.findAllPinned().mapNotNullToSet {
			it.source.toMangaSourceOrNull()?.takeUnless { x -> skipNsfw && x.isNsfw() }
		}
		val mihonPinned = getMihonSources().filter { sourceKeyOf(it) in getPinnedSourceKeys() }
		return nativePinned + mihonPinned
	}

	suspend fun getTopSources(limit: Int): List<MangaSource> {
		assimilateNewSources()
		val nativeTop = dao.findLastUsed(limit).toSources(settings.isNsfwContentDisabled, null).map { it.mangaSource }
		val mihonTop = getMihonSources().filter { usagePrefs.getLong(sourceKeyOf(it), 0L) > 0L }
			.sortedByDescending { usagePrefs.getLong(sourceKeyOf(it), 0L) }
		return (mihonTop + nativeTop).take(limit)
	}

	suspend fun getDisabledSources(): Set<MangaSource> {
		assimilateNewSources()
		if (settings.isAllSourcesEnabled) {
			return emptySet()
		}
		val result = LinkedHashSet<MangaSource>(allMangaSources)
		val enabled = dao.findAllEnabledNames()
		for (name in enabled) {
			val source = name.toMangaSourceOrNull() ?: continue
			result.remove(source)
		}
		return result
	}

	suspend fun queryParserSources(
		isDisabledOnly: Boolean,
		isNewOnly: Boolean,
		excludeBroken: Boolean,
		types: Set<ContentType>,
		query: String?,
		locale: String?,
		sortOrder: SourcesSortOrder?,
	): List<ParserMangaSource> {
		assimilateNewSources()
		val entities = dao.findAll().toMutableList()
		if (isDisabledOnly && !settings.isAllSourcesEnabled) {
			entities.removeAll { it.isEnabled }
		}
		if (isNewOnly) {
			entities.retainAll { it.addedIn == BuildConfig.VERSION_CODE }
		}
		val sources = entities.toSources(
			skipNsfwSources = settings.isNsfwContentDisabled,
			sortOrder = sortOrder,
		).run {
			mapNotNullTo(ArrayList(size)) { it.mangaSource as? ParserMangaSource }
		}
		if (locale != null) {
			sources.retainAll { it.locale == locale }
		}
		if (excludeBroken) {
			sources.removeAll { it.isBroken }
		}
		if (types.isNotEmpty()) {
			sources.retainAll { it.contentType in types }
		}
		if (!query.isNullOrEmpty()) {
			sources.retainAll {
				it.getTitle(context).contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
			}
		}
		return sources
	}

	fun observeIsEnabled(source: MangaSource): Flow<Boolean> {
		return dao.observeIsEnabled(source.name).onStart { assimilateNewSources() }
	}

	fun observeEnabledSourcesCount(): Flow<Int> {
		return combine(
			observeIsNsfwDisabled(),
			observeAllEnabled().flatMapLatest { isAllSourcesEnabled ->
				dao.observeAll(!isAllSourcesEnabled, SourcesSortOrder.MANUAL)
			},
			observeParsers(),
		) { skipNsfw, sources, _ ->
			sources.count {
				it.source.toMangaSourceOrNull()?.let { s -> !skipNsfw || !s.isNsfw() } == true
			}
		}.distinctUntilChanged().onStart { assimilateNewSources() }
	}

	fun observeAvailableSourcesCount(): Flow<Int> {
		return combine(
			observeIsNsfwDisabled(),
			observeAllEnabled().flatMapLatest { isAllSourcesEnabled ->
				dao.observeAll(!isAllSourcesEnabled, SourcesSortOrder.MANUAL)
			},
			observeParsers(),
		) { skipNsfw, enabledSources, _ ->
			val enabled = enabledSources.mapToSet { it.source }
			allMangaSources.count { x ->
				x.name !in enabled && (!skipNsfw || !x.isNsfw())
			}
		}.distinctUntilChanged().onStart { assimilateNewSources() }
	}

	fun observeEnabledSources(): Flow<List<MangaSourceInfo>> = combine(
		observeIsNsfwDisabled(),
		observeAllEnabled(),
		observeSortOrder(),
		observeParsers(),
	) { skipNsfw, allEnabled, order, _ ->
		// Re-emits when a score download lands, so the order and the flames update without waiting
		// for the sources table to change. Local stats are left out on purpose: they change on every
		// search, and the list reshuffling under the user's finger would be worse than slightly stale.
		combine(dao.observeAll(!allEnabled, order), observeScoreUpdates()) { entities, _ -> entities }.map {
			it.toSources(skipNsfw, order).sortedByScoreIfNeeded(order)
		}
	}.flattenLatest()
		.onStart { assimilateNewSources() }
		.combine(observeExternalSources()) { enabled, external ->
			enabled to external
		}.combine(observeMihonSources()) { (enabled, external), mihonSources ->
			val mihonInfos = buildMihonSourceInfoList(mihonSources)
			val list = ArrayList<MangaSourceInfo>(enabled.size + external.size + mihonInfos.size)
			external.mapTo(list) { MangaSourceInfo(it, isEnabled = true, isPinned = true) }
			list.addAll(mihonInfos)
			list.addAll(enabled)
			list
		}

	fun observeAll(): Flow<List<Pair<MangaSource, Boolean>>> = combine(
		dao.observeAll(),
		observeParsers(),
		observeMihonSources(),
	) { entities, _, mihonSources ->
		val result = ArrayList<Pair<MangaSource, Boolean>>(entities.size + mihonSources.size)
		for (source in mihonSources) {
			result.add(source to true)
		}
		for (entity in entities) {
			val source = entity.source.toMangaSourceOrNull() ?: continue
			if (source in allMangaSources) {
				result.add(source to entity.isEnabled)
			}
		}
		result
	}.onStart { assimilateNewSources() }

	suspend fun setSourcesEnabled(sources: Collection<MangaSource>, isEnabled: Boolean): ReversibleHandle {
		setSourcesEnabledImpl(sources, isEnabled)
		return ReversibleHandle {
			setSourcesEnabledImpl(sources, !isEnabled)
		}
	}

	suspend fun setSourcesEnabledExclusive(sources: Set<MangaSource>) {
		db.withTransaction {
			assimilateNewSources()
			for (s in allMangaSources) {
				dao.setEnabled(s.name, s in sources)
			}
		}
	}

	suspend fun disableAllSources() {
		db.withTransaction {
			assimilateNewSources()
			dao.disableAllSources()
		}
	}

	suspend fun setPositions(sources: List<MangaSource>) {
		db.withTransaction {
			for ((index, item) in sources.withIndex()) {
				dao.setSortKey(item.name, index)
			}
		}
	}

	fun observeHasNewSources(): Flow<Boolean> = combine(observeIsNsfwDisabled(), observeParsers()) { skipNsfw, _ ->
		val sources = dao.findAllFromVersion(BuildConfig.VERSION_CODE).toSources(skipNsfw, null)
		sources.isNotEmpty() && sources.size != allMangaSources.size
	}.onStart { assimilateNewSources() }

	fun observeHasNewSourcesForBadge(): Flow<Boolean> = combine(
		settings.observeAsFlow(AppSettings.KEY_SOURCES_VERSION) { sourcesVersion },
		observeIsNsfwDisabled(),
		settings.observeAsFlow(AppSettings.KEY_SOURCES_NEW_DOWNLOADED) { hasNewDownloadedSources },
		observeParsers(),
	) { version, skipNsfw, hasDownloaded, _ ->
		if (version < BuildConfig.VERSION_CODE) {
			val sources = dao.findAllFromVersion(version).toSources(skipNsfw, null)
			sources.isNotEmpty()
		} else {
			hasDownloaded
		}
	}.onStart { assimilateNewSources() }

	fun clearNewSourcesBadge() {
		settings.sourcesVersion = BuildConfig.VERSION_CODE
		settings.hasNewDownloadedSources = false
	}

	/**
	 * The parsers in use, once the sources they came with are in the table: something to put next to whatever lists
	 * sources, so that it is listed again when newer parsers are downloaded, with what they add and without what they
	 * drop.
	 */
	private fun observeParsers(): Flow<Int> = ActiveParsers.observe().onEach { assimilateNewSources() }

	/**
	 * Gives a row of the table to every source that has none yet. Once for each set of parsers that comes into use:
	 * the one the app starts with, and any it downloads while running.
	 */
	private suspend fun assimilateNewSources(): Boolean {
		val generation = ActiveParsers.generation
		if (assimilatedGeneration.get() == generation) {
			return false
		}
		// Nothing is held while this is done, some callers being inside a transaction of the database: two of them
		// at once do the same thing twice, the rows going in only where there are none
		val isChanged = insertNewSources()
		// Only now: a failure above, or the caller going away, leaves it to be done by whoever asks next
		assimilatedGeneration.set(generation)
		return isChanged
	}

	private suspend fun insertNewSources(): Boolean {
		val new = getNewSources()
		if (new.isEmpty()) {
			return false
		}
		if (!ActiveParsers.isBuiltIn && settings.sourcesVersion >= BuildConfig.VERSION_CODE) {
			// Sources that came with no update of the app, after the ones of this version were looked at
			settings.hasNewDownloadedSources = true
		}
		var maxSortKey = dao.getMaxSortKey()
		val isAllEnabled = settings.isAllSourcesEnabled
		val entities = new.map { x ->
			MangaSourceEntity(
				source = x.name,
				isEnabled = isAllEnabled,
				sortKey = ++maxSortKey,
				addedIn = BuildConfig.VERSION_CODE,
				lastUsedAt = 0,
				isPinned = false,
				cfState = CloudFlareHelper.PROTECTION_NOT_DETECTED,
			)
		}
		dao.insertIfAbsent(entities)
		return true
	}

	suspend fun isSetupRequired(): Boolean {
		return settings.sourcesVersion == 0 && dao.findAllEnabledNames().isEmpty()
	}

	suspend fun trackUsage(source: MangaSource) {
		val key = sourceKeyOf(source)
		usagePrefs.edit().putLong(key, System.currentTimeMillis()).apply()
		if (!settings.isIncognitoModeEnabled(source.isNsfw())) {
			dao.setLastUsed(source.name, System.currentTimeMillis())
		}
	}

	private suspend fun setSourcesEnabledImpl(sources: Collection<MangaSource>, isEnabled: Boolean) {
		if (sources.size == 1) { // fast path
			dao.setEnabled(sources.first().name, isEnabled)
			return
		}
		db.withTransaction {
			for (source in sources) {
				dao.setEnabled(source.name, isEnabled)
			}
		}
	}

	private suspend fun getNewSources(): MutableSet<out MangaSource> {
		val entities = dao.findAll()
		val result = LinkedHashSet<MangaSource>(allMangaSources)
		for (e in entities) {
			result.remove(e.source.toMangaSourceOrNull() ?: continue)
		}
		return result
	}

	private suspend fun setSourcesPinnedImpl(sources: Collection<MangaSource>, isPinned: Boolean) {
		if (sources.size == 1) { // fast path
			dao.setPinned(sources.first().name, isPinned)
			return
		}
		db.withTransaction {
			for (source in sources) {
				dao.setPinned(source.name, isPinned)
			}
		}
	}

	private fun observeExternalSources(): Flow<List<ExternalMangaSource>> {
		return callbackFlow {
			val receiver = object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					trySendBlocking(intent)
				}
			}
			ContextCompat.registerReceiver(
				context,
				receiver,
				IntentFilter().apply {
					addAction(Intent.ACTION_PACKAGE_ADDED)
					addAction(Intent.ACTION_PACKAGE_VERIFIED)
					addAction(Intent.ACTION_PACKAGE_REPLACED)
					addAction(Intent.ACTION_PACKAGE_REMOVED)
					addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
					addDataScheme("package")
				},
				ContextCompat.RECEIVER_EXPORTED,
			)
			awaitClose { context.unregisterReceiver(receiver) }
		}.onStart {
			emit(null)
		}.map {
			getExternalSources()
		}.distinctUntilChanged()
			.conflate()
	}

	fun getExternalSources(): List<ExternalMangaSource> = context.packageManager.queryIntentContentProviders(
		Intent("app.kotatsu.parser.PROVIDE_MANGA"), 0,
	).map { resolveInfo ->
		ExternalMangaSource(
			packageName = resolveInfo.providerInfo.packageName,
			authority = resolveInfo.providerInfo.authority,
		)
	}

	private fun List<MangaSourceEntity>.toSources(
		skipNsfwSources: Boolean,
		sortOrder: SourcesSortOrder?,
	): MutableList<MangaSourceInfo> {
		val isAllEnabled = settings.isAllSourcesEnabled
		val result = ArrayList<MangaSourceInfo>(size)
		for (entity in this) {
			val source = entity.source.toMangaSourceOrNull() ?: continue
			if (skipNsfwSources && source.isNsfw()) {
				continue
			}
			if (source in allMangaSources) {
				result.add(
					MangaSourceInfo(
						mangaSource = source,
						isEnabled = entity.isEnabled || isAllEnabled,
						isPinned = entity.isPinned,
					),
				)
			}
		}
		if (sortOrder == SourcesSortOrder.ALPHABETIC) {
			result.sortWith(compareBy<MangaSourceInfo> { !it.isPinned }.thenBy { it.getTitle(context) })
		}
		return result
	}

	/**
	 * Orders a list that did not come from the sources table - a preset's sources - the way the
	 * enabled list is ordered for the user's chosen [SourcesSortOrder]. Without this a preset showed
	 * sources in registry order whatever the setting said, flames and all but never popular-first.
	 * Manual and last-used orders have no meaning outside the table, so those keep the given order.
	 */
	suspend fun sortForDisplay(sources: List<MangaSourceInfo>, order: SourcesSortOrder): List<MangaSourceInfo> =
		when (order) {
			SourcesSortOrder.ALPHABETIC -> sources.sortedWith(
				compareBy<MangaSourceInfo> { !it.isPinned }.thenBy { it.getTitle(context) },
			)

			else -> sources.sortedByScoreIfNeeded(order)
		}

	/** Emits the current sort order, then again whenever the user changes it. */
	fun observeSourcesSortOrder(): Flow<SourcesSortOrder> = observeSortOrder()

	/**
	 * Applies [SourcesSortOrder.SCORE], which cannot be expressed as an ORDER BY because the community
	 * scores live in a separate Room database (kept apart so upstream Kotatsu's migrations never
	 * collide with this fork's).
	 *
	 * Pinned sources stay on top regardless: pinning is an explicit user choice and outranks anything
	 * a score has to say. The really popular (flame-marked) sources come straight after them.
	 */
	private suspend fun List<MangaSourceInfo>.sortedByScoreIfNeeded(
		order: SourcesSortOrder?,
	): List<MangaSourceInfo> {
		if (order?.isExternallyRanked != true || isEmpty()) return this
		val ranker = sourceRanker ?: return this
		val snapshot = runCatchingCancellable { ranker.snapshot() }.getOrNull() ?: return this
		val ranks = associate { it.name to snapshot.rank(it.mangaSource) }
		return sortedWith(
			compareBy<MangaSourceInfo> { !it.isPinned }
				.thenByDescending { ranks.getValue(it.name).isHot }
				.thenByDescending { ranks.getValue(it.name).composite },
		)
	}

	/** Emits once at start and again whenever downloaded community scores change. */
	fun observeScoreUpdates(): Flow<Unit> =
		sourceRanker?.observeScores()?.map { } ?: flowOf(Unit)

	private fun observeIsNsfwDisabled() = settings.observeAsFlow(AppSettings.KEY_DISABLE_NSFW) {
		isNsfwContentDisabled
	}

	private fun observeSortOrder() = settings.observeAsFlow(AppSettings.KEY_SOURCES_ORDER) {
		sourcesSortOrder
	}

	private fun observeAllEnabled() = settings.observeAsFlow(AppSettings.KEY_SOURCES_ENABLED_ALL) {
		isAllSourcesEnabled
	}

	private val usageRefresh = MutableStateFlow(0)
	private val pinnedRefresh = MutableStateFlow(0)

	private val usageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> usageRefresh.value++ }
	private val pinnedListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> pinnedRefresh.value++ }

	private val usagePrefs: SharedPreferences by lazy {
		context.getSharedPreferences("source_usage", Context.MODE_PRIVATE).also {
			it.registerOnSharedPreferenceChangeListener(usageListener)
		}
	}

	private val sourceStatePrefs: SharedPreferences by lazy {
		context.getSharedPreferences("source_state", Context.MODE_PRIVATE).also {
			it.registerOnSharedPreferenceChangeListener(pinnedListener)
		}
	}

	private fun sourceKeyOf(source: MangaSource): String = when (source) {
		is MangaSourceInfo -> sourceKeyOf(source.mangaSource)
		is MihonMangaSource -> "mihon:${source.pkgName}:${source.catalogueSource.name}"
		else -> source.name
	}

	private fun getPinnedSourceKeys(): List<String> {
		val raw = sourceStatePrefs.getString("pinned_sources_order", null).orEmpty()
		if (raw.isEmpty()) return emptyList()
		return raw.split(";").filter { it.isNotBlank() }
	}

	private fun setPinnedSourceKeys(keys: List<String>) {
		sourceStatePrefs.edit().putString("pinned_sources_order", keys.joinToString(";")).apply()
	}

	fun setIsPinned(sources: Collection<MangaSource>, isPinned: Boolean): ReversibleHandle {
		val before = getPinnedSourceKeys()
		val updated = before.toMutableList()
		for (source in sources) {
			val key = sourceKeyOf(source)
			if (isPinned) {
				updated.remove(key)
				updated.add(0, key)
			} else {
				updated.remove(key)
			}
		}
		setPinnedSourceKeys(updated)
		return ReversibleHandle {
			setPinnedSourceKeys(before)
		}
	}

	private fun buildMihonSourceInfoList(sources: List<MangaSource>): List<MangaSourceInfo> {
		if (sources.isEmpty()) return emptyList()
		val pinnedOrder = getPinnedSourceKeys()
		val pinnedIndex = HashMap<String, Int>(pinnedOrder.size)
		for ((index, key) in pinnedOrder.withIndex()) {
			pinnedIndex[key] = index
		}

		val pinned = ArrayList<MangaSourceInfo>()
		val unpinned = ArrayList<MangaSourceInfo>()
		for (source in sources) {
			val isPinned = sourceKeyOf(source) in pinnedIndex
			val item = MangaSourceInfo(source, isEnabled = true, isPinned = isPinned)
			if (isPinned) pinned += item else unpinned += item
		}

		pinned.sortBy { pinnedIndex[sourceKeyOf(it.mangaSource)] ?: Int.MAX_VALUE }
		when (settings.sourcesSortOrder) {
			SourcesSortOrder.ALPHABETIC -> unpinned.sortWith(compareBy { it.getTitle(context) })
			SourcesSortOrder.LAST_USED -> unpinned.sortWith(compareByDescending { usagePrefs.getLong(sourceKeyOf(it.mangaSource), 0L) })
			else -> Unit
		}
		return pinned + unpinned
	}

	private val appLanguage: String
		get() = ConfigurationCompat.getLocales(context.resources.configuration)[0]
			?.language
			?.takeIf { it.isNotEmpty() }
			?: "en"

	suspend fun reloadMihonSources() {
		mihonExtensionManager?.ensureReady(forceRefresh = true)
	}

	fun getAllMihonSources(): List<MihonMangaSource> {
		val manager = mihonExtensionManager ?: return emptyList()
		manager.initialize()
		val contentFilter = settings.contentFilter
		return manager.getMihonMangaSources().filter { it.contentWarning <= contentFilter }
	}

	fun observeAllMihonSources(): Flow<List<MihonMangaSource>> {
		val manager = mihonExtensionManager ?: return kotlinx.coroutines.flow.flowOf(emptyList())
		manager.initialize()
		return combine(
			manager.installedExtensions,
			manager.isLoading,
			settings.observeAsFlow(AppSettings.KEY_CONTENT_FILTER) { contentFilter },
		) { _: Any?, _: Any?, _: Any? ->
			getAllMihonSources()
		}.distinctUntilChanged()
	}

	fun getMihonSources(): List<MihonMangaSource> {
		val hiddenLangs = settings.hiddenSourceLanguages
		return getActiveMihonSources().filterNot { it.language in hiddenLangs }
	}

	fun getActiveMihonSources(): List<MihonMangaSource> {
		val manager = mihonExtensionManager ?: return emptyList()
		manager.initialize()
		val contentFilter = settings.contentFilter
		val hiddenPackages = settings.mihonHiddenPackages
		val appLang = appLanguage
		return manager.getMihonMangaSources()
			.filter { it.contentWarning <= contentFilter }
			.filterNot { it.pkgName in hiddenPackages }
			.groupBy { it.pkgName to it.catalogueSource.name }
			.mapNotNull { (key, group) ->
				if (group.size == 1) {
					group.first()
				} else {
					val (pkgName, sourceName) = key
					val langs = group.map { it.language }
					val stored = settings.getMihonActiveLang(pkgName, sourceName)
					val activeLang = org.koitharu.kotatsu.mihon.resolveActiveMihonLanguage(langs, stored, appLang)
					group.firstOrNull { it.language == activeLang } ?: group.first()
				}
			}
	}

	fun observeMihonSources(): Flow<List<MihonMangaSource>> {
		val manager = mihonExtensionManager ?: return kotlinx.coroutines.flow.flowOf(emptyList())
		manager.initialize()
		return combine(
			manager.installedExtensions,
			manager.isLoading,
			settings.observeAsFlow(AppSettings.KEY_MIHON_PER_EXT_ACTIVE_LANG) { mihonPerExtActiveLangs },
			settings.observeAsFlow(AppSettings.KEY_CONTENT_FILTER) { contentFilter },
			settings.observeAsFlow(AppSettings.KEY_MIHON_HIDDEN_PACKAGES) { mihonHiddenPackages },
			settings.observeAsFlow(AppSettings.KEY_HIDDEN_SOURCE_LANGUAGES) { hiddenSourceLanguages },
		) { _: Array<Any?> ->
			getMihonSources()
		}.distinctUntilChanged()
	}



	fun resolveActiveSource(source: MangaSource): ResolvedSource {
		val mihon = (source.unwrap() as? MihonMangaSource) ?: return ResolvedSource(source, null)
		val manager = mihonExtensionManager ?: return ResolvedSource(source, null)
		manager.initialize()
		val siblings = manager.getMihonMangaSources()
			.filter { it.pkgName == mihon.pkgName && it.catalogueSource.name == mihon.catalogueSource.name }
		if (siblings.size <= 1) return ResolvedSource(source, null)
		val stored = settings.getMihonActiveLang(mihon.pkgName, mihon.catalogueSource.name)
		val activeLang = org.koitharu.kotatsu.mihon.resolveActiveMihonLanguage(siblings.map { it.language }, stored, appLanguage)
		val active = siblings.firstOrNull { it.language == activeLang } ?: mihon
		return ResolvedSource(active, active.languageDisplayName)
	}

	private fun String.toMangaSourceOrNull(): ParserMangaSource? = ActiveParsers.findSource(this)
}
