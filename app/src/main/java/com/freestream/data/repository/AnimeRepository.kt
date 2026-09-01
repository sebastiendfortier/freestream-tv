package com.freestream.data.repository

import android.content.Context
import com.freestream.FreeStreamApp
import com.freestream.data.db.WatchHistoryDatabase
import com.freestream.data.db.WatchHistoryEntity
import com.freestream.data.model.AnimeItem
import com.freestream.data.model.SeriesDetails
import com.freestream.data.model.SeriesMatch
import com.freestream.resolver.MatchRanker
import com.freestream.resolver.OtakuResolver
import com.freestream.resolver.WcoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimeRepository(
    context: Context,
    wcoResolver: WcoResolver? = null,
    val otakuResolver: OtakuResolver = OtakuResolver(),
) {
    private val appContext = context.applicationContext
    private val catalog = CatalogRepository(appContext)
    private val historyDatabase = WatchHistoryDatabase.getInstance(appContext)
    private val historyDao = historyDatabase.watchHistoryDao()
    val wcoResolver: WcoResolver = wcoResolver ?: WcoResolver(FreeStreamApp.getInstance().wcoHttpClient)
    private val prefs = appContext.getSharedPreferences("freestream_prefs", Context.MODE_PRIVATE)

    /** @deprecated Use [wcoResolver] directly. */
    val resolver: WcoResolver get() = wcoResolver

    fun getPreferredQuality(): String = prefs.getString("pref_quality", "Auto") ?: "Auto"

    fun setPreferredQuality(quality: String) {
        prefs.edit().putString("pref_quality", quality).apply()
    }

    suspend fun searchStreamSeries(anime: AnimeItem): List<SeriesMatch> = withContext(Dispatchers.IO) {
        val candidates = MatchRanker.titleCandidates(
            displayTitle = anime.title,
            titleRomaji = anime.titleRomaji.takeIf { it.isNotBlank() },
            titleJapanese = anime.titleJapanese.takeIf { it.isNotBlank() },
        )

        val pool = mutableListOf<SeriesMatch>()
        val seenUrls = mutableSetOf<String>()
        for (query in candidates) {
            try {
                val hits = wcoResolver.searchSeries(query)
                for (hit in hits) {
                    if (hit.url.isNotBlank() && hit.url !in seenUrls) {
                        seenUrls.add(hit.url)
                        pool.add(hit.copy(source = "wco"))
                    }
                }
            } catch (_: Exception) {
            }
        }

        val ranked = MatchRanker.rankSeriesMatches(pool, anime.title)
        if (ranked.isNotEmpty()) {
            return@withContext ranked
        }

        val malId = anime.malId
        if (malId != null) {
            try {
                return@withContext otakuResolver.searchByMalId(malId)
            } catch (_: Exception) {
            }
        }

        emptyList()
    }

    suspend fun listEpisodesForMatch(match: SeriesMatch): SeriesDetails = withContext(Dispatchers.IO) {
        if (match.source == "otaku") {
            otakuResolver.listEpisodes(match.url)
        } else {
            wcoResolver.listEpisodes(match.url)
        }
    }

    suspend fun getPopularAnime(limit: Int = 100, offset: Int = 0): List<AnimeItem> =
        catalog.getFilteredAnime(limit = limit, offset = offset)

    suspend fun getAnimeByType(type: String, limit: Int = 100): List<AnimeItem> =
        catalog.getFilteredAnime(type = type, limit = limit)

    suspend fun getAnimeByTag(tag: String, limit: Int = 50): List<AnimeItem> =
        catalog.getFilteredAnime(includeTags = listOf(tag), limit = limit)

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
    ): List<AnimeItem> = catalog.getFilteredAnime(
        type = type,
        minYear = minYear,
        minScore = minScore,
        airingStatus = airingStatus,
        includeTags = includeTags,
        excludeTags = excludeTags,
        query = query,
        sortBy = sortBy,
        limit = limit,
        offset = offset,
    )

    suspend fun getFilteredCount(
        type: String = "ALL",
        minYear: Int = 0,
        minScore: Float = 0f,
        airingStatus: String = "ALL",
        includeTags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
        query: String = "",
    ): Int = catalog.getFilteredCount(
        type = type,
        minYear = minYear,
        minScore = minScore,
        airingStatus = airingStatus,
        includeTags = includeTags,
        excludeTags = excludeTags,
        query = query,
    )

    suspend fun getAnimeByTitle(title: String): AnimeItem? = catalog.getAnimeByTitle(title)

    suspend fun search(query: String, limit: Int = 100): List<AnimeItem> = withContext(Dispatchers.IO) {
        val localResults = catalog.search(query, limit)
        if (localResults.isNotEmpty()) {
            return@withContext localResults
        }
        try {
            val wcoMatches = wcoResolver.searchSeries(query)
            wcoMatches.map { match ->
                AnimeItem(
                    title = match.title,
                    type = if (match.isMovie) "MOVIE" else "TV",
                    picture = match.thumb,
                    synopsis = "Available to stream",
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeContinueWatching(limit: Int = 20): kotlinx.coroutines.flow.Flow<List<WatchHistoryEntity>> {
        return historyDao.observeContinueWatching(limit)
    }

    suspend fun getContinueWatching(limit: Int = 20): List<WatchHistoryEntity> = withContext(Dispatchers.IO) {
        historyDao.getContinueWatching(limit)
    }

    suspend fun getEpisodeProgress(episodeUrl: String): WatchHistoryEntity? = withContext(Dispatchers.IO) {
        historyDao.getProgressForEpisode(episodeUrl)
    }

    suspend fun getSeriesHistory(seriesTitle: String): List<WatchHistoryEntity> = withContext(Dispatchers.IO) {
        historyDao.getHistoryForSeries(seriesTitle)
    }

    suspend fun saveWatchProgress(
        episodeUrl: String,
        seriesTitle: String,
        episodeTitle: String,
        seasonNumber: String,
        episodeNumber: String,
        posterUrl: String,
        positionMs: Long,
        durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        val isFinished = durationMs > 0 && positionMs >= (durationMs * 0.90)
        val entity = WatchHistoryEntity(
            episodeUrl = episodeUrl,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            posterUrl = posterUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis(),
            isFinished = isFinished,
        )
        historyDao.upsert(entity)
    }

    suspend fun deleteWatchProgress(episodeUrl: String) = withContext(Dispatchers.IO) {
        historyDao.delete(episodeUrl)
    }
}
