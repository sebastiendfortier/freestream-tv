package com.freestream.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackEpisode(
    val url: String,
    val cleanLabel: String,
    val seasonNumber: String = "",
    val episodeNumber: String = "",
)

@Serializable
data class PlaybackSession(
    val episodes: List<PlaybackEpisode>,
    val startIndex: Int,
    val source: String,
    val seriesTitle: String,
    val posterUrl: String,
    val preferredQuality: String,
    val malId: Int? = null,
)
