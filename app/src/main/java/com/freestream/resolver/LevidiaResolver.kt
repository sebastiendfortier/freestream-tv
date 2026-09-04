package com.freestream.resolver

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal class LevidiaResolver(
    private val client: OkHttpClient,
) {
    private val base = "https://www.levidia.ch"
    private val ua = KODI_UA

    data class HosterLink(val provider: String, val url: String)

    fun scrape(
        title: String,
        year: Int?,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): List<HosterLink> {
        get("$base/")
        val searchHtml = post("$base/search.php?q=${enc(title)}")
        val block = extractMainlinkBlock(searchHtml) ?: return emptyList()
        val links = parseAllLinks(block)
        val titleKey = cleanTitle(title)
        var matchUrl: String? = null
        for ((label, href) in links) {
            val years = Regex("""\((\d{4})\)""").findAll(label).map { it.groupValues[1] }.toList()
            if (years.isEmpty()) continue
            val name = label.replace(Regex("""\(\d{4}\)"""), "").trim()
            if (cleanTitle(name) == titleKey || cleanTitle(name).contains(titleKey)) {
                if (year != null && years.first() != year.toString()) continue
                matchUrl = abs(href)
                break
            }
        }
        if (matchUrl == null && links.isNotEmpty()) {
            matchUrl = abs(links.first().second)
        }
        matchUrl ?: return emptyList()

        var pageUrl = matchUrl
        if (mediaType.equals("tv", ignoreCase = true) && season != null) {
            pageUrl = "$matchUrl&s=$season"
        }
        var page = get(pageUrl, referer = base)
        var referer = pageUrl
        if (mediaType.equals("tv", ignoreCase = true) && season != null && episode != null) {
            val epPattern = Regex("""s${season}e$episode(?!\d)""", RegexOption.IGNORE_CASE)
            val epHref = parseAllLinks(page).firstOrNull { epPattern.containsMatchIn(it.second) }?.second
                ?: return emptyList()
            referer = abs(epHref)
            page = get(referer, referer = pageUrl)
        }
        return collectHosters(page, referer)
    }

    fun listEpisodes(title: String, year: Int?, season: Int): List<TvEpisodeInfo> {
        get("$base/")
        val searchHtml = post("$base/search.php?q=${enc(title)}")
        val block = extractMainlinkBlock(searchHtml) ?: return emptyList()
        val links = parseAllLinks(block)
        val titleKey = cleanTitle(title)
        var matchUrl: String? = null
        for ((label, href) in links) {
            val years = Regex("""\((\d{4})\)""").findAll(label).map { it.groupValues[1] }.toList()
            if (years.isEmpty()) continue
            val name = label.replace(Regex("""\(\d{4}\)"""), "").trim()
            if (cleanTitle(name).contains(titleKey)) {
                if (year != null && years.first() != year.toString()) continue
                matchUrl = abs(href)
                break
            }
        }
        matchUrl ?: return emptyList()
        val page = get("$matchUrl&s=$season", referer = base)
        val prefix = "s${season}e"
        val seen = mutableSetOf<Int>()
        val out = mutableListOf<TvEpisodeInfo>()
        for ((label, href) in parseAllLinks(page)) {
            val hrefL = href.lowercase()
            if (!hrefL.contains(prefix)) continue
            val m = Regex("""s${season}e(\d+)""").find(hrefL) ?: continue
            val ep = m.groupValues[1].toIntOrNull() ?: continue
            if (!seen.add(ep)) continue
            out += TvEpisodeInfo(season, ep, stripTags(label).ifBlank { "Episode $ep" })
        }
        return out.sortedBy { it.episode }
    }

    private fun collectHosters(page: String, referer: String): List<HosterLink> {
        var hosts = parseSpansContaining(page, listOf("xxx1", "xx12"))
        if (hosts.isEmpty()) hosts = parseSpansContaining(page, listOf("xxx1"))
        var links = parseLinks(page, "xxx xflv")
        if (links.isEmpty()) {
            links = parseBlankTargetLinks(page)
        }
        val cookieHeader = cookieHeader(page, referer)
        val out = mutableListOf<HosterLink>()
        for ((index, link) in links.withIndex()) {
            val host = hosts.getOrNull(index) ?: "levidia"
            val absLink = abs(link)
            val final = resolveGo(absLink, referer, cookieHeader) ?: continue
            out += HosterLink(host, final)
        }
        return out
    }

    private fun cookieHeader(pageHtml: String, referer: String): String {
        val cookies = linkedMapOf<String, String>()
        base.toHttpUrlOrNull()?.let { client.cookieJar.loadForRequest(it) }
            .orEmpty()
            .forEach { cookies[it.name] = it.value }
        referer.toHttpUrlOrNull()?.let { client.cookieJar.loadForRequest(it) }
            .orEmpty()
            .forEach { cookies[it.name] = it.value }
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
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Referer", referer).get().build()
        client.newCall(req).execute().use { return it.body?.string().orEmpty() }
    }

    private fun post(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Referer", base)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        client.newCall(req).execute().use { return it.body?.string().orEmpty() }
    }

    private fun abs(href: String): String =
        if (href.startsWith("http")) href else "$base/${href.trimStart('/')}"

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private val REDIRECTS = 300..399

        fun newClient(): OkHttpClient {
            val store = mutableMapOf<String, List<Cookie>>()
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val merged = store.getOrDefault(url.host, emptyList())
                        .associateBy { it.name }
                        .toMutableMap()
                    cookies.forEach { merged[it.name] = it }
                    store[url.host] = merged.values.toList()
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
            }
            return OkHttpClient.Builder()
                .cookieJar(jar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
