package com.freestream.data.repository

import android.content.Context
import com.freestream.data.model.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Loads the MAL catalog from the gzip JSON export of anime_view.parquet.
 * Parquet remains the desktop source of truth; JSON.gz is the Android runtime format.
 */
class CatalogRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    @Volatile
    private var catalog: List<AnimeItem>? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun ensureLoaded(): List<AnimeItem> = withContext(Dispatchers.IO) {
        catalog?.let { return@withContext it }
        loadMutex.withLock {
            catalog?.let { return@withLock it }
            val items = loadCatalogLocked()
            catalog = items
            items
        }
    }

    private fun loadCatalogLocked(): List<AnimeItem> {
        val catalogFile = File(context.filesDir, CATALOG_FILE_NAME)
        val assetVersion = prefs.getInt(PREF_ASSET_VERSION, 0)

        if (!catalogFile.exists() || assetVersion < CATALOG_ASSET_VERSION) {
            val (assetName, isGzip) = resolveCatalogAsset()
            context.assets.open(assetName).use { input ->
                catalogFile.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.edit()
                .putInt(PREF_ASSET_VERSION, CATALOG_ASSET_VERSION)
                .putBoolean(PREF_ASSET_GZIP, isGzip)
                .apply()
        }

        val isGzip = prefs.getBoolean(PREF_ASSET_GZIP, isGzipFile(catalogFile))
        val text = readCatalogText(catalogFile, isGzip)
        return json.decodeFromString<List<AnimeItem>>(text)
    }

    private fun resolveCatalogAsset(): Pair<String, Boolean> {
        if (assetExists(ASSET_GZ)) return ASSET_GZ to true
        if (assetExists(ASSET_JSON)) return ASSET_JSON to false
        throw java.io.FileNotFoundException("$ASSET_GZ or $ASSET_JSON not found in assets")
    }

    private fun assetExists(name: String): Boolean = try {
        context.assets.open(name).close()
        true
    } catch (_: java.io.FileNotFoundException) {
        false
    }

    private fun isGzipFile(file: File): Boolean {
        file.inputStream().use { input ->
            return input.read() == 0x1f && input.read() == 0x8b
        }
    }

    private fun readCatalogText(file: File, isGzip: Boolean): String {
        val input = file.inputStream()
        return if (isGzip) {
            GZIPInputStream(input).bufferedReader().use { it.readText() }
        } else {
            input.bufferedReader().use { it.readText() }
        }
    }

    suspend fun getFilteredAnime(
        type: String = "ALL",
        minYear: Int = 0,
        minScore: Float = 0f,
        airingStatus: String = "ALL",
        includeTags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
        query: String = "",
        sortBy: String = "score",
        limit: Int = 120,
        offset: Int = 0,
    ): List<AnimeItem> = withContext(Dispatchers.Default) {
        val filtered = ensureLoaded().asSequence().filter { item ->
            matchesFilters(item, type, minYear, minScore, airingStatus, includeTags, excludeTags, query)
        }

        val sorted = when (sortBy) {
            "year" -> filtered.sortedWith(compareByDescending<AnimeItem> { it.year }.thenByDescending { it.scoreMean })
            "title" -> filtered.sortedBy { it.title.lowercase() }
            else -> filtered.sortedByDescending { it.scoreMean }
        }

        sorted.drop(offset).take(limit).toList()
    }

    suspend fun getFilteredCount(
        type: String = "ALL",
        minYear: Int = 0,
        minScore: Float = 0f,
        airingStatus: String = "ALL",
        includeTags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
        query: String = "",
    ): Int = withContext(Dispatchers.Default) {
        ensureLoaded().count { item ->
            matchesFilters(item, type, minYear, minScore, airingStatus, includeTags, excludeTags, query)
        }
    }

    suspend fun search(query: String, limit: Int = 100): List<AnimeItem> =
        getFilteredAnime(query = query, limit = limit)

    suspend fun getAnimeByTitle(title: String): AnimeItem? = withContext(Dispatchers.Default) {
        val needle = title.trim().lowercase()
        ensureLoaded().firstOrNull { it.title.equals(needle, ignoreCase = true) }
    }

    private fun matchesFilters(
        item: AnimeItem,
        type: String,
        minYear: Int,
        minScore: Float,
        airingStatus: String,
        includeTags: List<String>,
        excludeTags: List<String>,
        query: String,
    ): Boolean {
        if (type.isNotBlank() && type.uppercase() != "ALL" && item.type.uppercase() != type.uppercase()) {
            return false
        }
        if (minYear > 0 && (item.year == null || item.year < minYear)) return false
        if (minScore > 0f && item.scoreMean < minScore) return false

        val statusFilter = airingStatus.trim().lowercase()
        if (statusFilter.isNotEmpty() && statusFilter != "all") {
            if (statusFilter != "upcoming" && item.airingStatus.equals("upcoming", ignoreCase = true)) {
                return false
            }
            if (!item.airingStatus.equals(statusFilter, ignoreCase = true)) return false
        }

        val tagsJoined = item.tags.joinToString(",").lowercase()
        for (tag in includeTags) {
            val t = tag.trim().lowercase()
            if (t.isNotEmpty() && !tagsJoined.contains(t)) return false
        }
        for (tag in excludeTags) {
            val t = tag.trim().lowercase()
            if (t.isNotEmpty() && tagsJoined.contains(t)) return false
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val haystack = listOf(
                item.title,
                item.titleRomaji,
                item.titleJapanese,
                item.synopsis,
                item.tags.joinToString(" "),
            ).joinToString(" ").lowercase()
            if (!haystack.contains(q)) return false
        }
        return true
    }

    companion object {
        private const val PREFS_NAME = "freestream_catalog"
        private const val PREF_ASSET_VERSION = "catalog_asset_version"
        private const val PREF_ASSET_GZIP = "catalog_asset_gzip"
        private const val CATALOG_ASSET_VERSION = 1
        private const val ASSET_GZ = "titles_catalog.json.gz"
        private const val ASSET_JSON = "titles_catalog.json"
        private const val CATALOG_FILE_NAME = "titles_catalog.cache"
    }
}
