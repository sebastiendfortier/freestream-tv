package com.freestream.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class WcoHttpClient(
    val baseUrl: String = "https://www.wcoflix.tv",
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36",
    private val minIntervalMs: Long = 600L
) {
    private val mutex = Mutex()
    private var lastRequestTime = 0L

    private val oldDomains = listOf(
        "www.thewatchcartoononline.tv",
        "thewatchcartoononline.tv",
        "www.wcostream.com",
        "m.wcostream.com",
        "www.wcostream.tv",
        "wcostream.tv",
        "wcostream.com",
        "www.watchcartoononline.io",
        "m.watchcartoononline.io",
        "watchcartoononline.io",
        "www.wcofun.com",
        "www.wcofun.net",
        "www.wcofun.tv",
        "wcofun.net",
        "wcofun.com",
        "wcofun.tv",
        "www.wcoforever.net",
        "www.wcoforever.org",
        "wcoforever.net",
        "wcoforever.org"
    )

    private val cookieStore = mutableMapOf<String, String>()

    val okHttpClient: OkHttpClient = createUnsafeOkHttpClient()

    private fun createUnsafeOkHttpClient(): OkHttpClient {
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookies.forEach { cookieStore[it.name] = it.value }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore.map { (k, v) ->
                        Cookie.Builder().name(k).value(v).domain(url.host).build()
                    }
                }
            })
            .build()
    }

    fun sanitizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isEmpty()) return baseUrl

        try {
            val uri = URI(url)
            val host = uri.host ?: ""
            val subdomains = listOf("embed.", "vhs.", "images.", "neptun.", "saturn.", "s01.", "s02.", "s03.", "s04.", "s05.", "s06.", "s07.", "e11.", "cizgifilmlerizle")
            if (subdomains.any { host.contains(it) }) {
                return url
            }
        } catch (_: Exception) {}

        val targetHost = try { URI(baseUrl).host } catch (_: Exception) { "www.wcoflix.tv" }
        for (old in oldDomains) {
            if (url.contains(old)) {
                url = url.replace(old, targetHost)
                break
            }
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        if (url.startsWith("/")) {
            return "$baseUrl$url"
        }

        return "$baseUrl/$url"
    }

    private suspend fun throttle() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < minIntervalMs) {
                delay(minIntervalMs - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        throttle()
        val sanitized = sanitizeUrl(url)
        val requestBuilder = Request.Builder()
            .url(sanitized)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")

        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 302 && response.code != 301) {
            throw IOException("HTTP error code: ${response.code} for $sanitized")
        }
        response.body?.string() ?: ""
    }

    suspend fun postForm(url: String, formParams: Map<String, String>, extraHeaders: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        throttle()
        val sanitized = sanitizeUrl(url)
        val formBuilder = FormBody.Builder()
        formParams.forEach { (k, v) -> formBuilder.add(k, v) }

        val requestBuilder = Request.Builder()
            .url(sanitized)
            .post(formBuilder.build())
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")

        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP error code: ${response.code} for $sanitized")
        }
        response.body?.string() ?: ""
    }
}
