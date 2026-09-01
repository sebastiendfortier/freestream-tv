package com.freestream

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.freestream.resolver.WcoHttpClient
import okhttp3.OkHttpClient

class FreeStreamApp : Application(), ImageLoaderFactory {

    lateinit var wcoHttpClient: WcoHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        wcoHttpClient = WcoHttpClient()
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.url.host.contains("myanimelist.net")) {
                    builder.header("Referer", "https://myanimelist.net/")
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    companion object {
        @Volatile
        private var instance: FreeStreamApp? = null

        fun getInstance(): FreeStreamApp {
            return instance ?: throw IllegalStateException("FreeStreamApp not initialized")
        }
    }
}
