package com.freestream.resolver

import com.freestream.data.model.ResolvedStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal class WootlyResolver(
    private val client: OkHttpClient,
) {
    private val ua = KODI_UA

    fun resolve(url: String): ResolvedStream? {
        val webUrl = normalizeUrl(url)
        val pageUrl = webUrl.toHttpUrlOrNull() ?: return null
        val pageReq = Request.Builder().url(webUrl).header("User-Agent", ua).get().build()
        client.newCall(pageReq).execute().use { pageResp ->
            val pageBody = pageResp.body?.string().orEmpty()
            val iframe = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(pageBody)?.groupValues?.get(1) ?: return null
            val embedUrl = if (iframe.startsWith("//")) "https:$iframe" else iframe
            val pageOrigin = pageUrl.host
            val cookieHeader = cookieHeader(pageUrl, embedUrl.toHttpUrlOrNull())
            val postReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", ua)
                .header("Referer", webUrl)
                .header("Origin", "https://$pageOrigin")
                .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
                .post(FormBody.Builder().add("qdfx", "1").build())
                .build()
            val postBody = client.newCall(postReq).execute().use { it.body?.string().orEmpty() }
            val tk = Regex("""tk\s*=\s*["']([^"']+)""").find(postBody)?.groupValues?.get(1) ?: return null
            val vd = Regex("""vd\s*=\s*["']([^"']+)""").find(postBody)?.groupValues?.get(1) ?: return null
            val grabBase = embedUrl.substringBeforeLast("/") + "/grabm"
            val grabUrl = "$grabBase?t=${enc(tk)}&id=${enc(vd)}"
            val grabBody = client.newCall(
                Request.Builder()
                    .url(grabUrl)
                    .header("User-Agent", ua)
                    .header("Referer", webUrl)
                    .get()
                    .build(),
            ).execute().use { it.body?.string().orEmpty().trim() }
            if (!grabBody.startsWith("http")) return null
            val streamUrl = followToMedia(grabBody) ?: return null
            return ResolvedStream(
                streamUrl = streamUrl,
                quality = "HD",
                headers = mapOf(
                    "User-Agent" to ua,
                    "Referer" to "https://web.wootly.ch/",
                    "Origin" to "https://web.wootly.ch",
                ),
                sourceUrl = webUrl,
                contentType = if (streamUrl.contains(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp4",
            )
        }
    }

    private fun cookieHeader(vararg urls: okhttp3.HttpUrl?): String {
        val cookies = linkedMapOf<String, String>()
        for (url in urls) {
            url ?: continue
            client.cookieJar.loadForRequest(url).forEach { cookies[it.name] = it.value }
        }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun followToMedia(startUrl: String): String? {
        var current = startUrl.trim()
        repeat(10) {
            if (current.contains(".mp4", ignoreCase = true) || current.contains(".m3u8", ignoreCase = true)) {
                return current
            }
            val req = Request.Builder()
                .url(current)
                .header("User-Agent", ua)
                .header("Referer", "https://www.wootly.ch/")
                .get()
                .build()
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                .newCall(req)
                .execute()
                .use { resp ->
                    if (resp.code in 300..399) {
                        val loc = resp.header("Location") ?: return@use
                        current = normalizeRedirect(current, loc)
                        return@repeat
                    }
                    val body = resp.body?.string().orEmpty().trim()
                    if (body.startsWith("http")) {
                        current = body.lineSequence().first()
                        return@repeat
                    }
                    if (body.contains(".mp4") || body.contains(".m3u8")) {
                        return body.lineSequence().first { line ->
                            line.contains(".mp4") || line.contains(".m3u8")
                        }
                    }
                }
        }
        return if (current.contains(".mp4", ignoreCase = true) || current.contains(".m3u8", ignoreCase = true)) {
            current
        } else {
            null
        }
    }

    private fun normalizeRedirect(current: String, loc: String): String =
        when {
            loc.startsWith("//") -> "https:$loc"
            loc.startsWith("/") -> {
                val scheme = current.substringBefore("://")
                val host = current.substringAfter("://").substringBefore("/")
                "$scheme://$host$loc"
            }
            else -> loc
        }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        fun normalizeUrl(url: String): String {
            var out = url.replace("web.wootly.ch", "www.wootly.ch")
            val m = Regex("""^https?://web\.wootly\.ch/e/.*/([^/]+)$""").find(out)
            if (m != null) out = "https://www.wootly.ch/?v=${m.groupValues[1]}"
            return out
        }
    }
}
