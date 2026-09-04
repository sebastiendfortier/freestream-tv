package com.freestream.resolver

import com.freestream.data.model.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

@Serializable
data class StreamPayload(
    @SerialName("streamUrl") val streamUrl: String,
    val quality: String = "SD",
    val headers: Map<String, String> = emptyMap(),
    val provider: String = "",
    @SerialName("sourceUrl") val sourceUrl: String = "",
    @SerialName("contentType") val contentType: String = "video/mp4",
)

/** On-device Levidia/Wootly resolver. */
class StreamResolver(
    private val client: OkHttpClient = LevidiaResolver.newClient(),
) {
    private val levidia = LevidiaResolver(client)
    private val wootly = WootlyResolver(client)

    suspend fun listEpisodes(
        title: String,
        year: Int?,
        season: Int,
    ): List<TvEpisodeInfo> = withContext(Dispatchers.IO) {
        levidia.listEpisodes(title, year, season)
    }

    /** Probe seasons 1..max until an empty season is found (or max reached). */
    suspend fun listAvailableSeasons(
        title: String,
        year: Int?,
        maxSeason: Int = 12,
    ): List<Int> = withContext(Dispatchers.IO) {
        val seasons = mutableListOf<Int>()
        for (s in 1..maxSeason) {
            val eps = levidia.listEpisodes(title, year, s)
            if (eps.isEmpty()) break
            seasons += s
        }
        seasons
    }

    suspend fun resolve(
        imdbId: String,
        title: String,
        mediaType: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        episodeUrl: String? = null,
    ): List<StreamPayload> = withContext(Dispatchers.IO) {
        resolveLocal(title, mediaType, year, season, episode, episodeUrl)
    }

    fun toResolvedStream(payload: StreamPayload): ResolvedStream =
        ResolvedStream(
            streamUrl = payload.streamUrl,
            quality = payload.quality,
            headers = payload.headers,
            sourceUrl = payload.sourceUrl.ifBlank { payload.streamUrl },
            contentType = payload.contentType,
        )

    private fun resolveLocal(
        title: String,
        mediaType: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        episodeUrl: String? = null,
    ): List<StreamPayload> {
        val hosters = levidia.scrape(title, year, mediaType, season, episode, episodeUrl)
        if (hosters.isEmpty()) {
            throw IllegalStateException(
                if (mediaType.equals("tv", ignoreCase = true)) {
                    "No episode page / hosters for $title S${season}E$episode"
                } else {
                    "No streams found for $title"
                },
            )
        }
        val wootlyHosters = hosters.filter { it.url.contains("wootly", ignoreCase = true) }
        if (wootlyHosters.isEmpty()) {
            throw IllegalStateException("No Wootly hosters for $title (found ${hosters.size} other)")
        }
        for (hoster in wootlyHosters) {
            repeat(2) {
                val resolved = runCatching { wootly.resolve(hoster.url) }.getOrNull()
                if (resolved != null) {
                    return listOf(
                        StreamPayload(
                            streamUrl = resolved.streamUrl,
                            quality = resolved.quality,
                            headers = resolved.headers,
                            provider = hoster.provider,
                            sourceUrl = resolved.sourceUrl,
                            contentType = resolved.contentType,
                        ),
                    )
                }
            }
        }
        throw IllegalStateException("Wootly resolve failed for $title (tried ${wootlyHosters.size})")
    }
}
