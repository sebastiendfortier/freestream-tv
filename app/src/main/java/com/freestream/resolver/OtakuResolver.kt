package com.freestream.resolver

import com.freestream.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OtakuResolver(
    private val timeoutSeconds: Long = 12,
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client: OkHttpClient = createUnsafeClient()

    private fun createUnsafeClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun searchByMalId(malId: Int): List<SeriesMatch> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$MALSYNC_API_BASE/mal/anime/$malId")
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()

        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) return@withContext emptyList()

        val data = jsonParser.parseToJsonElement(body).jsonObject
        val title = data["title"]?.jsonPrimitive?.content ?: "Anime (MAL: $malId)"
        val image = data["image"]?.jsonPrimitive?.content.orEmpty()
        val sites = data["Sites"]?.jsonObject ?: return@withContext emptyList()

        val skipSites = setOf("Crunchyroll", "Netflix", "Hulu", "VRV")
        val matches = mutableListOf<SeriesMatch>()

        for ((siteName, siteEntries) in sites) {
            if (siteName in skipSites) continue
            val entriesObj = siteEntries.jsonObject
            for ((_, entryVal) in entriesObj) {
                val entry = entryVal.jsonObject
                val targetUrl = entry["url"]?.jsonPrimitive?.content.orEmpty()
                if (targetUrl.isEmpty()) continue
                matches.add(
                    SeriesMatch(
                        title = "$title [$siteName]",
                        url = targetUrl,
                        thumb = image,
                        audioHint = "sub",
                        isMovie = false,
                        source = "otaku"
                    )
                )
            }
        }
        matches
    }

    suspend fun listEpisodes(seriesUrl: String): SeriesDetails = withContext(Dispatchers.IO) {
        val cleanUrl = seriesUrl.trim()
        val request = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to fetch episodes page: HTTP ${response.code}")
        }
        val html = response.body?.string().orEmpty()

        val titleMatch = Regex("""<h1[^>]*>([^<]+)</h1>""").find(html)
        val title = titleMatch?.groupValues?.get(1)?.trim().orEmpty().ifEmpty { "Anime Series" }

        val episodes = mutableListOf<EpisodeItem>()
        val netloc = try {
            URI(cleanUrl).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }

        if ("animepahe" in netloc) {
            val epMatches = Regex("""<a href="([^"]+/play/[^"]+)"[^>]*>([^<]+)</a>""").findAll(html)
            for (match in epMatches) {
                val rawLink = match.groupValues[1]
                val epLabel = match.groupValues[2].trim()
                val epUrl = URI(cleanUrl).resolve(rawLink).toString()
                val epDigits = epLabel.filter { it.isDigit() }
                episodes.add(
                    EpisodeItem(
                        title = epLabel,
                        rawTitle = epLabel,
                        url = epUrl,
                        episodeNumber = epDigits.ifEmpty { null },
                        seasonNumber = "1",
                        cleanLabel = if (epDigits.isNotEmpty()) "E${epDigits.toInt().toString().padStart(2, '0')}" else epLabel,
                        isSub = true,
                        isDub = false
                    )
                )
            }
        }

        if (episodes.isEmpty()) {
            val fallback = Regex("""<a[^>]+href="([^"]+)"[^>]*>([^<]*Episode\s*(\d+)[^<]*)</a>""", RegexOption.IGNORE_CASE)
            for (match in fallback.findAll(html)) {
                val rawLink = match.groupValues[1]
                val epText = match.groupValues[2].trim()
                val epNum = match.groupValues[3]
                val epUrl = URI(cleanUrl).resolve(rawLink).toString()
                episodes.add(
                    EpisodeItem(
                        title = epText,
                        rawTitle = epText,
                        url = epUrl,
                        episodeNumber = epNum,
                        seasonNumber = "1",
                        cleanLabel = "E${epNum.toInt().toString().padStart(2, '0')}",
                        isSub = !epText.contains("dub", ignoreCase = true),
                        isDub = epText.contains("dub", ignoreCase = true)
                    )
                )
            }
        }

        val subbed = episodes.filter { it.isSub }
        val dubbed = episodes.filter { it.isDub }

        SeriesDetails(
            title = title,
            url = cleanUrl,
            plot = "",
            thumb = "",
            episodes = episodes,
            subbedEpisodes = subbed,
            dubbedEpisodes = dubbed
        )
    }

    suspend fun resolveEpisodeStreams(episodeUrl: String, preferredQuality: String? = null): ResolvedStream =
        withContext(Dispatchers.IO) {
            val cleanUrl = episodeUrl.trim()
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch Otaku episode: HTTP ${response.code}")
            }
            val html = response.body?.string().orEmpty()

            var streamUrl: String? = null

            val mp4uploadMatch = Regex("""src="([^"]*mp4upload\.com/[^"]+)"""").find(html)
            if (mp4uploadMatch != null) {
                streamUrl = extractMp4Upload(mp4uploadMatch.groupValues[1])
            }

            if (streamUrl == null) {
                val videoMatch = Regex("""<source[^>]+src=["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").find(html)
                if (videoMatch != null) {
                    streamUrl = videoMatch.groupValues[1]
                }
            }

            if (streamUrl.isNullOrEmpty()) {
                throw IOException("Could not extract playable stream from Otaku source")
            }

            val headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to cleanUrl
            )

            ResolvedStream(
                streamUrl = streamUrl,
                quality = preferredQuality ?: "Auto",
                headers = headers,
                sourceUrl = cleanUrl,
                contentType = if (streamUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"
            )
        }

    private fun extractMp4Upload(embedUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", userAgent)
                .header("Referer", embedUrl)
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string().orEmpty()
            val srcMatch = Regex("""src:\s*["'](https?://[^"']+\.mp4[^"']*)["']""").find(html)
                ?: Regex("""player\.src\(["'](https?://[^"']+\.mp4[^"']*)["']\)""").find(html)
            srcMatch?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MALSYNC_API_BASE = "https://api.malsync.moe"
    }
}
