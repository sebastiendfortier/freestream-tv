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
import java.util.concurrent.TimeUnit

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

class StreamResolver(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun resolve(
        imdbId: String,
        title: String,
        mediaType: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
    ): List<StreamPayload> = withContext(Dispatchers.IO) {
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
                throw IllegalStateException("Stream API HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            json.decodeFromString<StreamApiResponse>(body).streams
        }
    }

    fun toResolvedStream(payload: StreamPayload): ResolvedStream =
        ResolvedStream(
            streamUrl = payload.streamUrl,
            quality = payload.quality,
            headers = payload.headers,
            sourceUrl = payload.sourceUrl.ifBlank { payload.streamUrl },
            contentType = payload.contentType,
        )

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
