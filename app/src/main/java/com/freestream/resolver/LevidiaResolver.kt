package com.freestream.resolver

import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class LevidiaResolver(
    private val client: OkHttpClient,
) {
    private val base = "https://www.levidia.ch"
    private val ua = KODI_UA
    private val sessionCookies = ConcurrentHashMap<String, String>()
    private val lock = Any()

    data class HosterLink(val provider: String, val url: String)

    private var cachedSeriesKey: String? = null
    private var cachedSeriesUrl: String? = null

    fun scrape(
        title: String,
        year: Int?,
        mediaType: String,
        season: Int?,
        episode: Int?,
        episodeUrl: String? = null,
    ): List<HosterLink> = synchronized(lock) {
        sessionCookies.clear()
        get("$base/")

        // Prefer direct episode URL from the list — avoid a second search flake.
        if (!episodeUrl.isNullOrBlank()) {
            val referer = abs(episodeUrl)
            val page = get(referer, referer = base)
            return collectHosters(page, referer)
        }

        val matchUrl = seriesUrl(title, year) ?: return emptyList()
        var pageUrl = matchUrl
        if (mediaType.equals("tv", ignoreCase = true) && season != null) {
            pageUrl = "$matchUrl&s=$season"
        }

        val referer: String
        val page: String
        if (mediaType.equals("tv", ignoreCase = true) && season != null && episode != null) {
            val seasonPage = get(pageUrl, referer = base)
            val epHref = findEpisodeHref(seasonPage, season, episode) ?: return emptyList()
            referer = abs(epHref)
            page = get(referer, referer = pageUrl)
        } else {
            referer = pageUrl
            page = get(pageUrl, referer = base)
        }

        return collectHosters(page, referer)
    }

    fun listEpisodes(title: String, year: Int?, season: Int): List<TvEpisodeInfo> = synchronized(lock) {
        sessionCookies.clear()
        get("$base/")
        val matchUrl = seriesUrl(title, year) ?: return emptyList()
        val page = get("$matchUrl&s=$season", referer = base)
        val prefix = "s${season}e"
        val seen = mutableSetOf<Int>()
        val out = mutableListOf<TvEpisodeInfo>()
        for ((label, href) in parseAllLinks(page)) {
            val hrefL = href.lowercase()
            if (!hrefL.contains(prefix)) continue
            val m = Regex("""s${season}e(\d+)""", RegexOption.IGNORE_CASE).find(hrefL) ?: continue
            val ep = m.groupValues[1].toIntOrNull() ?: continue
            if (!seen.add(ep)) continue
            out += TvEpisodeInfo(
                season = season,
                episode = ep,
                title = stripTags(label).ifBlank { "Episode $ep" },
                episodeUrl = abs(href),
            )
        }
        return out.sortedBy { it.episode }
    }

    private fun seriesUrl(title: String, year: Int?): String? {
        val key = "${cleanTitle(title)}|${year ?: ""}"
        if (cachedSeriesKey == key && !cachedSeriesUrl.isNullOrBlank()) {
            return cachedSeriesUrl
        }
        val found = findSeriesUrl(title, year)
        cachedSeriesKey = key
        cachedSeriesUrl = found
        return found
    }

    private fun findSeriesUrl(title: String, year: Int?): String? {
        val titleKey = cleanTitle(title)
        val queries = buildList {
            add(title)
            if (year != null) add("$title $year")
        }
        var exactIgnoreYear: String? = null
        for (query in queries) {
            val searchHtml = post("$base/search.php?q=${enc(query)}")
            if (searchHtml.contains("about 0 results", ignoreCase = true)) continue
            val block = extractMainlinkBlock(searchHtml) ?: continue
            val links = parseAllLinks(block)
            for ((label, href) in links) {
                val years = Regex("""\((\d{4})\)""").findAll(label).map { it.groupValues[1] }.toList()
                if (years.isEmpty()) continue
                val name = label.replace(Regex("""\(\d{4}\)"""), "").trim()
                val cleaned = cleanTitle(name)
                if (cleaned != titleKey && !cleaned.contains(titleKey) && !titleKey.contains(cleaned)) {
                    continue
                }
                val url = abs(href)
                if (year == null || years.any { it == year.toString() }) {
                    return url
                }
                if (cleaned == titleKey && exactIgnoreYear == null) {
                    exactIgnoreYear = url
                }
            }
        }
        // Catalog year can drift from Levidia's listed year (returning series).
        return exactIgnoreYear
    }

    private fun findEpisodeHref(page: String, season: Int, episode: Int): String? {
        val epPattern = Regex("""s${season}e0*$episode(?!\d)""", RegexOption.IGNORE_CASE)
        return parseAllLinks(page).firstOrNull { epPattern.containsMatchIn(it.second) }?.second
    }

    private fun collectHosters(page: String, referer: String): List<HosterLink> {
        var hosts = parseSpansContaining(page, listOf("xxx1", "xx12"))
        if (hosts.isEmpty()) hosts = parseSpansContaining(page, listOf("xxx1"))
        var links = parseLinks(page, "xxx xflv")
        if (links.isEmpty()) {
            links = parseBlankTargetLinks(page).filter { it.contains("go.php", ignoreCase = true) }
        }
        val cookieHeader = cookieHeader(page)
        val out = mutableListOf<HosterLink>()
        for ((index, link) in links.withIndex()) {
            val host = hosts.getOrNull(index) ?: "levidia"
            val absLink = abs(link)
            val final = resolveGo(absLink, referer, cookieHeader) ?: continue
            out += HosterLink(host, final)
        }
        return out
    }

    private fun cookieHeader(pageHtml: String): String {
        val cookies = LinkedHashMap(sessionCookies)
        Regex("""_3chk\(['"](.+?)['"],['"](.+?)['"]\)""")
            .find(pageHtml)
            ?.let { cookies[it.groupValues[1]] = it.groupValues[2] }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun resolveGo(goUrl: String, referer: String, cookieHeader: String): String? {
        if (!goUrl.contains("go.php")) return goUrl
        val req = Request.Builder()
            .url(goUrl)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", referer)
            .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
            .get()
            .build()
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
            .newCall(req)
            .execute()
            .use { resp ->
                captureCookies(resp)
                if (resp.code in REDIRECTS) {
                    val loc = resp.header("Location") ?: return null
                    return WootlyResolver.normalizeUrl(if (loc.startsWith("//")) "https:$loc" else loc)
                }
                val final = resp.request.url.toString()
                if (final.contains("go.php")) return null
                return WootlyResolver.normalizeUrl(final)
            }
    }

    private fun get(url: String, referer: String = base): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", referer)
            .apply {
                val cookie = sessionCookieHeader()
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            captureCookies(resp)
            return resp.body?.string().orEmpty()
        }
    }

    private fun post(url: String): String {
        val body = ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", base)
            .apply {
                val cookie = sessionCookieHeader()
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            captureCookies(resp)
            return resp.body?.string().orEmpty()
        }
    }

    private fun sessionCookieHeader(): String =
        sessionCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun captureCookies(resp: Response) {
        fun ingest(raw: String) {
            val pair = raw.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', missingDelimiterValue = "").trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                sessionCookies[name] = value
            }
        }
        for (raw in resp.headers("Set-Cookie")) ingest(raw)
        var prior = resp.priorResponse
        while (prior != null) {
            for (raw in prior.headers("Set-Cookie")) ingest(raw)
            prior = prior.priorResponse
        }
    }

    /** Test/debug helper. */
    internal fun debugSessionCookieNames(): Set<String> = sessionCookies.keys.toSet()

    private fun abs(href: String): String =
        if (href.startsWith("http")) href else "$base/${href.trimStart('/')}"

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private val REDIRECTS = 300..399

        fun newClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
