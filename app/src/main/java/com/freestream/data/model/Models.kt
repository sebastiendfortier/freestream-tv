package com.freestream.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AnimeItem(
    val title: String,
    val malId: Int? = null,
    val titleRomaji: String = "",
    val titleJapanese: String = "",
    val airingStatus: String = "aired",
    val type: String = "TV",
    val year: Int? = null,
    val scoreMean: Double = 0.0,
    val picture: String = "",
    val studios: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val synopsis: String = ""
)

@Serializable
data class SeriesMatch(
    val title: String,
    val url: String,
    val thumb: String = "",
    val isMovie: Boolean = false,
    val audioHint: String = "both",
    val source: String = "wco"
)

@Serializable
data class EpisodeItem(
    val title: String,
    val rawTitle: String,
    val url: String,
    val episodeNumber: String? = null,
    val seasonNumber: String? = null,
    val multipart: String? = null,
    val episodeTitle: String = "",
    val cleanLabel: String = "",
    val isSub: Boolean = true,
    val isDub: Boolean = false
)

@Serializable
data class SeriesDetails(
    val title: String,
    val url: String,
    val plot: String = "",
    val thumb: String = "",
    val episodes: List<EpisodeItem> = emptyList(),
    val subbedEpisodes: List<EpisodeItem> = emptyList(),
    val dubbedEpisodes: List<EpisodeItem> = emptyList()
)

@Serializable
data class QualityOption(
    val label: String,
    val url: String
)

@Serializable
data class ResolvedStream(
    val streamUrl: String,
    val quality: String,
    val headers: Map<String, String>,
    val sourceUrl: String,
    val backupUrl: String = "",
    val contentType: String = "video/mp4"
)
