package com.freestream.data.repository

import android.content.Context
import com.freestream.data.db.WatchHistoryDatabase
import com.freestream.data.db.WatchHistoryEntity
import com.freestream.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val catalog = CatalogRepository(appContext)
    private val historyDao = WatchHistoryDatabase.getInstance(appContext).watchHistoryDao()
    private val prefs = appContext.getSharedPreferences("freestream_prefs", Context.MODE_PRIVATE)

    suspend fun getMediaByTitle(title: String): MediaItem? = catalog.getMediaByTitle(title)

    fun getPreferredQuality(): String = prefs.getString("pref_quality", "Auto") ?: "Auto"

    fun setPreferredQuality(quality: String) {
        prefs.edit().putString("pref_quality", quality).apply()
    }

    suspend fun getFilteredMedia(
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
    ): List<MediaItem> = catalog.getFilteredMedia(
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

    suspend fun search(query: String, limit: Int = 100): List<MediaItem> =
        catalog.search(query, limit)

    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchHistoryEntity>> =
        historyDao.observeContinueWatching(limit)

    suspend fun getSeriesHistory(seriesTitle: String): List<WatchHistoryEntity> = withContext(Dispatchers.IO) {
        historyDao.getHistoryForSeries(seriesTitle)
    }

    suspend fun deleteWatchProgress(episodeUrl: String) = withContext(Dispatchers.IO) {
        historyDao.delete(episodeUrl)
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
}
