package com.freestream.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history WHERE isFinished = 0 ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE isFinished = 0 ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    suspend fun getContinueWatching(limit: Int = 20): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int = 50): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 50): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE episodeUrl = :episodeUrl LIMIT 1")
    suspend fun getProgressForEpisode(episodeUrl: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE seriesTitle = :seriesTitle ORDER BY lastWatchedTimestamp DESC")
    suspend fun getHistoryForSeries(seriesTitle: String): List<WatchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE episodeUrl = :episodeUrl")
    suspend fun delete(episodeUrl: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
