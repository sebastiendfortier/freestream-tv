package com.freestream.resolver

import com.freestream.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.IOException
import java.net.URI

class WcoResolver(private val client: WcoHttpClient) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private data class CacheEntry<T>(val expiresAt: Long, val value: T)

    private val searchCache = object : LinkedHashMap<String, CacheEntry<List<SeriesMatch>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<List<SeriesMatch>>>): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    private val episodesCache = object : LinkedHashMap<String, CacheEntry<SeriesDetails>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<SeriesDetails>>): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    private val cacheMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun <T> cached(key: String, store: LinkedHashMap<String, CacheEntry<T>>, loader: suspend () -> T): T {
        val now = System.currentTimeMillis()
        cacheMutex.lock()
        try {
            val hit = store[key]
            if (hit != null && hit.expiresAt > now) {
                return hit.value
            }
        } finally {
            cacheMutex.unlock()
        }

        val value = loader()
        cacheMutex.lock()
        try {
            store[key] = CacheEntry(now + CACHE_TTL_MS, value)
        } finally {
            cacheMutex.unlock()
        }
        return value
    }

    suspend fun searchSeries(query: String): List<SeriesMatch> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        cached("search:$trimmed", searchCache) { searchSeriesUncached(trimmed) }
    }

    private suspend fun searchSeriesUncached(trimmed: String): List<SeriesMatch> {
        val queriesToTry = mutableListOf<String>()

        fun addCandidate(c: String) {
            val cleaned = c.trim()
            if (cleaned.length >= 2 && !queriesToTry.contains(cleaned)) {
                queriesToTry.add(cleaned)
            }
        }

        // 1. Exact title
        addCandidate(trimmed)

        // 2. Strip parentheses e.g. "(TV)", "(2021)", "(Uncensored)"
        val noParens = trimmed.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        addCandidate(noParens)

        // 3. Colon / hyphen prefixes (e.g. "Sword Art Online: Alicization" -> "Sword Art Online")
        for (sep in listOf(":", " - ", " – ", " — ")) {
            if (trimmed.contains(sep)) {
                val prefix = trimmed.split(sep)[0].trim()
                addCandidate(prefix)
            }
        }

        // 4. Strip season suffixes e.g. "Season 2", "2nd Season", "Part 2", "II"
        val noSeason = trimmed.replace(Regex("""(?i)\s*(Season\s+\d+|\d+(?:st|nd|rd|th)\s+Season|Part\s+\d+|II|III|IV|V)$"""), "").trim()
        addCandidate(noSeason)

        // 5. Shortened word prefixes (first 3-4 significant words)
        // Helps with very long light novel titles like "The World's Finest Assassin Gets Reincarnated in Another World as an Aristocrat"
        val words = trimmed.replace(Regex("""[^\w\s]"""), " ").split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size >= 4) {
            addCandidate(words.take(4).joinToString(" "))
        }
        if (words.size >= 3) {
            addCandidate(words.take(3).joinToString(" "))
        }

        // Search strategy: first try with konuara="series", then fallback to konuara="0"
        val konuaraModes = listOf("series", "0")

        for (konuara in konuaraModes) {
            for (currentQuery in queriesToTry) {
                val html = try {
                    client.postForm("/search", mapOf("catara" to currentQuery, "konuara" to konuara))
                } catch (_: Exception) {
                    continue
                }

                val matches = parseSeriesMatches(html)
                if (matches.isNotEmpty()) {
                    return matches
                }
            }
        }

        return emptyList()
    }

    companion object {
        private const val CACHE_TTL_MS = 300_000L
        private const val MAX_CACHE_ENTRIES = 64
    }

    private fun parseSeriesMatches(html: String): List<SeriesMatch> {
        val matches = mutableListOf<SeriesMatch>()
        val seenUrls = mutableSetOf<String>()

        // 1. Modern WCO layout
        val modernRegex = Regex("""<div class=['"]recent-release-episodes['"]><a href=['"]([^'"]+)['"][^>]*>([^<]+)</a>""")
        for (match in modernRegex.findAll(html)) {
            val rawUrl = match.groupValues[1]
            val rawTitle = match.groupValues[2]
            val url = client.sanitizeUrl(rawUrl)
            val title = WcoDecoder.unescapeHtml(rawTitle)

            if (title.isEmpty() || seenUrls.contains(url) || url.contains("/search") || url.contains("genre")) {
                continue
            }
            seenUrls.add(url)
            val isSub = title.contains("subbed", ignoreCase = true) || url.contains("subbed", ignoreCase = true)
            val isDub = title.contains("dubbed", ignoreCase = true) || url.contains("dubbed", ignoreCase = true)
            val audio = if (isSub && !isDub) "sub" else if (isDub && !isSub) "dub" else "both"

            matches.add(
                SeriesMatch(
                    title = title,
                    url = url,
                    audioHint = audio,
                    isMovie = url.contains("/anime/movies") || title.contains("movie", ignoreCase = true)
                )
            )
        }

        // 2. Also check img blocks
        val imgRegex = Regex("""<div class=['"]img['"]>\s*<a href=['"]([^'"]+)['"]>\s*<img\s+alt=['"]([^'"]+)['"]""")
        for (match in imgRegex.findAll(html)) {
            val rawUrl = match.groupValues[1]
            val rawTitle = match.groupValues[2]
            val url = client.sanitizeUrl(rawUrl)
            val title = WcoDecoder.unescapeHtml(rawTitle)

            if (title.isEmpty() || seenUrls.contains(url) || url.contains("/search") || url.contains("genre")) {
                continue
            }
            seenUrls.add(url)
            val isSub = title.contains("subbed", ignoreCase = true) || url.contains("subbed", ignoreCase = true)
            val isDub = title.contains("dubbed", ignoreCase = true) || url.contains("dubbed", ignoreCase = true)
            val audio = if (isSub && !isDub) "sub" else if (isDub && !isSub) "dub" else "both"

            matches.add(
                SeriesMatch(
                    title = title,
                    url = url,
                    audioHint = audio,
                    isMovie = url.contains("/anime/movies") || title.contains("movie", ignoreCase = true)
                )
            )
        }

        return matches
    }

    suspend fun listEpisodes(seriesUrl: String): SeriesDetails = withContext(Dispatchers.IO) {
        val sanitizedUrl = client.sanitizeUrl(seriesUrl)
        cached("episodes:$sanitizedUrl", episodesCache) { listEpisodesUncached(sanitizedUrl) }
    }

    private suspend fun listEpisodesUncached(sanitizedUrl: String): SeriesDetails {
        val html = client.get(sanitizedUrl)
        return buildSeriesDetails(sanitizedUrl, html)
    }

    private suspend fun buildSeriesDetails(sanitizedUrl: String, html: String): SeriesDetails {
        val titleMatch = Regex("""<h1[^>]*>([^<]+)</h1>""").find(html)
        val seriesTitle = if (titleMatch != null) WcoDecoder.unescapeHtml(titleMatch.groupValues[1]) else ""

        var plot = ""
        val infoIdx = html.indexOf("Info:")
        if (infoIdx != -1) {
            val pMatch = Regex("""</h3>\s*<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(html.substring(infoIdx))
            if (pMatch != null) {
                plot = WcoDecoder.unescapeHtml(pMatch.groupValues[1])
            }
        }

        var thumb = ""
        val ogIdx = html.indexOf("og:image\" content=\"")
        if (ogIdx != -1) {
            val start = ogIdx + 19
            val end = html.indexOf("\"", start)
            if (end != -1) {
                val thumbPath = html.substring(start, end)
                thumb = if (thumbPath.startsWith("http")) thumbPath else URI(client.baseUrl).resolve(thumbPath).toString()
            }
        }

        val isSeriesDub = sanitizedUrl.contains("dubbed", ignoreCase = true) || seriesTitle.contains("dubbed", ignoreCase = true) || sanitizedUrl.contains("/anime/dubbed")
        val isSeriesSub = sanitizedUrl.contains("subbed", ignoreCase = true) || seriesTitle.contains("subbed", ignoreCase = true)

        val episodes = mutableListOf<EpisodeItem>()
        val seenUrls = mutableSetOf<String>()

        val darkRegex = Regex("""<a[^>]+href=['"]([^'"]+)['"][^>]*class=['"][^'"]*dark-episode-item[^'"]*['"][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val darkMatches = darkRegex.findAll(html).toList()

        if (darkMatches.isNotEmpty()) {
            for (m in darkMatches) {
                val epRawUrl = m.groupValues[1]
                val innerHtml = m.groupValues[2]
                val epUrl = client.sanitizeUrl(epRawUrl)
                if (epUrl.isEmpty() || seenUrls.contains(epUrl)) continue
                seenUrls.add(epUrl)

                val spanMatch = Regex("""<span>([^<]+)</span>""").find(innerHtml)
                val rawTitleText = spanMatch?.groupValues?.get(1) ?: innerHtml.replace(Regex("""<[^>]+>"""), "")
                val epTitle = WcoDecoder.unescapeHtml(rawTitleText).trim()

                val isDub = if (epTitle.contains("dubbed", ignoreCase = true) || epTitle.contains("english dubbed", ignoreCase = true)) {
                    true
                } else if (epTitle.contains("subbed", ignoreCase = true) || epTitle.contains("english subbed", ignoreCase = true)) {
                    false
                } else if (isSeriesDub && !isSeriesSub) {
                    true
                } else {
                    false
                }
                val isSub = !isDub

                val parsed = WcoDecoder.parseEpisodeInfo(epTitle)

                episodes.add(
                    EpisodeItem(
                        title = epTitle,
                        rawTitle = epTitle,
                        url = epUrl,
                        episodeNumber = parsed.episodeNumber,
                        seasonNumber = parsed.seasonNumber,
                        multipart = parsed.multipart,
                        episodeTitle = parsed.episodeTitle,
                        cleanLabel = parsed.cleanLabel,
                        isSub = isSub,
                        isDub = isDub
                    )
                )
            }
        } else {
            // Legacy link list
            val legacyRegex = Regex("""<a href="([^"]+)"[^>]*>([^<]+)</a>""")
            for (m in legacyRegex.findAll(html)) {
                val epRawUrl = m.groupValues[1]
                val epRawTitle = m.groupValues[2]
                val epUrl = client.sanitizeUrl(epRawUrl)
                val epTitle = WcoDecoder.unescapeHtml(epRawTitle).trim()

                if (epTitle.isEmpty() || seenUrls.contains(epUrl) || epUrl == sanitizedUrl || epUrl.contains("/category/") || epUrl.contains("/search")) {
                    continue
                }
                seenUrls.add(epUrl)

                val isDub = if (epTitle.contains("dubbed", ignoreCase = true) || epTitle.contains("english dubbed", ignoreCase = true)) {
                    true
                } else if (epTitle.contains("subbed", ignoreCase = true) || epTitle.contains("english subbed", ignoreCase = true)) {
                    false
                } else if (isSeriesDub && !isSeriesSub) {
                    true
                } else {
                    false
                }
                val isSub = !isDub

                val parsed = WcoDecoder.parseEpisodeInfo(epTitle)

                episodes.add(
                    EpisodeItem(
                        title = epTitle,
                        rawTitle = epTitle,
                        url = epUrl,
                        episodeNumber = parsed.episodeNumber,
                        seasonNumber = parsed.seasonNumber,
                        multipart = parsed.multipart,
                        episodeTitle = parsed.episodeTitle,
                        cleanLabel = parsed.cleanLabel,
                        isSub = isSub,
                        isDub = isDub
                    )
                )
            }
        }

        val reversedEpisodes = episodes.reversed()
        val subbed = reversedEpisodes.filter { it.isSub }
        val dubbed = reversedEpisodes.filter { it.isDub }

        return SeriesDetails(
            title = if (seriesTitle.isNotEmpty()) seriesTitle else (reversedEpisodes.firstOrNull()?.title ?: "Unknown Series"),
            url = sanitizedUrl,
            plot = plot,
            thumb = thumb,
            episodes = reversedEpisodes,
            subbedEpisodes = subbed,
            dubbedEpisodes = dubbed
        )
    }

    suspend fun resolveEpisodeStreams(episodeUrl: String, preferredQuality: String? = null): ResolvedStream = withContext(Dispatchers.IO) {
        val sanitizedEpUrl = client.sanitizeUrl(episodeUrl)
        val content = client.get(sanitizedEpUrl)
        if (content.isEmpty()) {
            throw IOException("Empty episode page response")
        }

        var embedUrl = WcoDecoder.decodeIframeSource(content, client.baseUrl)
            ?: throw IOException("Could not decode video iframe URL from episode page.")

        if (embedUrl.contains("inc/embed/index.php")) {
            embedUrl = embedUrl.replace("inc/embed/index.php", "inc/embed/video-js-new.php")
        } else if (embedUrl.contains("vhs.wcostream.com") && !embedUrl.contains("/video-js/")) {
            if (embedUrl.contains("?")) {
                val parts = embedUrl.split("?", limit = 2)
                embedUrl = "https://vhs.wcostream.com/video-js/?${parts[1]}"
            }
        }

        val html = client.get(embedUrl, extraHeaders = mapOf("Referer" to sanitizedEpUrl))
        val sourceUrls = mutableListOf<QualityOption>()
        var backupUrl = ""

        // Path A: VHS HLS Stream
        if (embedUrl.contains("vhs.wcostream.com") || html.contains("index.m3u8")) {
            val hlsRegex = Regex("""(?:getRedirectedUrl\(['"]|source\s+src=['"]|['"])(https?://[^'"]+/index\.m3u8|https?://[^'"]+/getvid/[^'"]+/index\.m3u8)['"]""")
            val hlsMatch = hlsRegex.find(html)
            if (hlsMatch != null) {
                sourceUrls.add(QualityOption(label = "1080 (FHD Multi)", url = hlsMatch.groupValues[1]))
            }
        }

        // Path B: GetVidLink JSON Tokens
        if (sourceUrls.isEmpty() && (html.contains("getvid?evid") || html.contains("/inc/embed/getvidlink"))) {
            val match = Regex("""['"](/inc/embed/getvidlink[^'"]+)""").find(html)
            if (match != null) {
                val sourceEndpoint = match.groupValues[1]
                val tokenHeaders = mapOf(
                    "User-Agent" to client.userAgent,
                    "Accept" to "*/*",
                    "Referer" to embedUrl,
                    "X-Requested-With" to "XMLHttpRequest"
                )
                val fullTokenUrl = URI("https://embed.wcostream.com").resolve(sourceEndpoint).toString()
                val tokenResp = client.get(fullTokenUrl, extraHeaders = tokenHeaders)
                val jsonObj = jsonParser.parseToJsonElement(tokenResp).jsonObject

                val sdToken = jsonObj["enc"]?.jsonPrimitive?.content ?: ""
                val hdToken = jsonObj["hd"]?.jsonPrimitive?.content ?: ""
                val fhdToken = jsonObj["fhd"]?.jsonPrimitive?.content ?: ""
                val serverBase = jsonObj["server"]?.jsonPrimitive?.content ?: ""
                val cdnBase = jsonObj["cdn"]?.jsonPrimitive?.content ?: ""

                if (serverBase.isNotEmpty()) {
                    val streamBase = serverBase.trimEnd('/') + "/getvid?evid="
                    if (sdToken.isNotEmpty()) sourceUrls.add(QualityOption(label = "480 (SD)", url = "$streamBase$sdToken"))
                    if (hdToken.isNotEmpty()) sourceUrls.add(QualityOption(label = "720 (HD)", url = "$streamBase$hdToken"))
                    if (fhdToken.isNotEmpty()) sourceUrls.add(QualityOption(label = "1080 (FHD)", url = "$streamBase$fhdToken"))
                }

                if (cdnBase.isNotEmpty()) {
                    val token = if (fhdToken.isNotEmpty()) fhdToken else if (hdToken.isNotEmpty()) hdToken else sdToken
                    if (token.isNotEmpty()) {
                        backupUrl = "${cdnBase.trimEnd('/')}/getvid?evid=$token"
                    }
                }
            }
        }

        // Path C: JWPlayer fallback
        if (sourceUrls.isEmpty()) {
            val streamPattern = Regex("""\{\s*file:\s*["']([^"']+)["'](?:,\s*label:\s*["']([^"']+)["'])?""")
            for (sm in streamPattern.findAll(html)) {
                val fileUrl = sm.groupValues[1]
                val label = if (sm.groupValues.size > 2 && sm.groupValues[2].isNotEmpty()) sm.groupValues[2] else "default"
                sourceUrls.add(QualityOption(label = label, url = fileUrl))
            }
        }

        if (sourceUrls.isEmpty()) {
            throw IOException("No playable stream URLs found in embed player.")
        }

        val selectedOption = if (!preferredQuality.isNullOrBlank() && preferredQuality.lowercase() != "auto") {
            val pref = preferredQuality.lowercase()
            sourceUrls.firstOrNull { it.label.lowercase().contains(pref) }
                ?: sourceUrls.lastOrNull { it.label.lowercase().contains("720") }
                ?: sourceUrls.last()
        } else {
            sourceUrls.last()
        }

        val mediaReferer = if (embedUrl.contains("vhs.wcostream.com")) "https://vhs.wcostream.com/" else "https://embed.wcostream.com/"
        val playbackHeaders = mapOf(
            "User-Agent" to client.userAgent,
            "Accept" to "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5",
            "Referer" to mediaReferer
        )

        val finalStreamUrl = solveMediaRedirect(selectedOption.url, playbackHeaders)

        ResolvedStream(
            streamUrl = finalStreamUrl,
            quality = selectedOption.label,
            headers = playbackHeaders,
            sourceUrl = sanitizedEpUrl,
            backupUrl = backupUrl,
            contentType = if (finalStreamUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"
        )
    }

    private fun solveMediaRedirect(initialUrl: String, headers: Map<String, String>): String {
        var currentUrl = initialUrl
        val noRedirectClient = client.okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        for (i in 0 until 8) {
            try {
                val reqBuilder = Request.Builder().url(currentUrl).head()
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                val resp = noRedirectClient.newCall(reqBuilder.build()).execute()
                if (resp.isRedirect) {
                    val location = resp.header("Location")
                    if (!location.isNullOrEmpty()) {
                        currentUrl = URI(currentUrl).resolve(location).toString()
                        continue
                    }
                }
                if (resp.code < 400) {
                    return currentUrl
                }
            } catch (_: Exception) {
                return currentUrl
            }
        }
        return currentUrl
    }
}
