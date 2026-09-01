package com.freestream.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["seriesTitle"]),
        Index(value = ["lastWatchedTimestamp"]),
        Index(value = ["isFinished", "lastWatchedTimestamp"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey
    val episodeUrl: String,
    val seriesTitle: String,
    val episodeTitle: String,
    val seasonNumber: String,
    val episodeNumber: String,
    val posterUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long,
    val isFinished: Boolean
)
