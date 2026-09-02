package com.freestream.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val title: String,
    @SerialName("malId") val tmdbId: Int? = null,
    val imdbId: String = "",
    val titleRomaji: String = "",
    val titleJapanese: String = "",
    val airingStatus: String = "aired",
    val type: String = "TV",
    val year: Int? = null,
    @SerialName("scoreMean") val imdbRating: Double = 0.0,
    val picture: String = "",
    val studios: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val synopsis: String = "",
)

@Serializable
data class QualityOption(
    val label: String,
    val url: String,
)

@Serializable
data class ResolvedStream(
    val streamUrl: String,
    val quality: String,
    val headers: Map<String, String>,
    val sourceUrl: String,
    val backupUrl: String = "",
    val contentType: String = "video/mp4",
)
