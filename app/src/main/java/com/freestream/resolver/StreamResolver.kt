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

    suspend fun resolve(
        imdbId: String,
        title: String,
        mediaType: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
    ): List<StreamPayload> = withContext(Dispatchers.IO) {
        resolveLocal(title, mediaType, year, season, episode)
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
    ): List<StreamPayload> {
        val hosters = levidia.scrape(title, year, mediaType, season, episode)
        if (hosters.isEmpty()) {
            throw IllegalStateException("No streams found for $title")
        }
        for (hoster in hosters) {
            if (!hoster.url.contains("wootly", ignoreCase = true)) continue
            val resolved = wootly.resolve(hoster.url) ?: continue
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
        throw IllegalStateException("No playable stream found (try another episode)")
    }
}
