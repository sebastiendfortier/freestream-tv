package com.freestream.resolver

import com.freestream.data.model.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@Serializable
data class StreamApiResponse(
    val streams: List<StreamPayload> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class StreamPayload(
    @SerialName("streamUrl") val streamUrl: String,
    val quality: String = "SD",
    val headers: Map<String, String> = emptyMap(),
    val provider: String = "",
    @SerialName("sourceUrl") val sourceUrl: String = "",
    @SerialName("contentType") val contentType: String = "video/mp4",
)

/** On-device Levidia/Wootly resolver with optional remote API fallback. */
class StreamResolver(
    private val remoteBaseUrl: String? = null,
    private val client: OkHttpClient = LevidiaResolver.newClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
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
        val localError = runCatching { resolveLocal(title, mediaType, year, season, episode) }
        if (localError.isSuccess) {
            return@withContext localError.getOrThrow()
        }
        val remote = remoteBaseUrl?.trim().orEmpty()
        if (remote.isNotBlank()) {
            return@withContext resolveRemote(remote, imdbId, title, mediaType, year, season, episode)
        }
        throw localError.exceptionOrNull()
            ?: IllegalStateException("No playable stream found for $title")
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
        for (hoster in hosters) {
            if (hoster.url.contains("wootly", ignoreCase = true)) {
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
        }
        throw IllegalStateException("No playable stream found (try another episode)")
    }

    private fun resolveRemote(
        baseUrl: String,
        imdbId: String,
        title: String,
        mediaType: String,
        year: Int?,
        season: Int?,
        episode: Int?,
    ): List<StreamPayload> {
        val params = buildList {
            add("imdb_id=${enc(imdbId)}")
            add("title=${enc(title)}")
            add("media_type=${enc(mediaType.lowercase())}")
            year?.let { add("year=$it") }
            season?.let { add("season=$it") }
            episode?.let { add("episode=$it") }
        }.joinToString("&")
        val url = "${baseUrl.trimEnd('/')}/api/stream/resolve?$params"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Remote stream API HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return json.decodeFromString<StreamApiResponse>(body).streams
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
