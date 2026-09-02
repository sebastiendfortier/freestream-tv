package com.freestream

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient

class FreeStreamApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder().build()
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

        fun getInstance(): FreeStreamApp = instance
            ?: throw IllegalStateException("FreeStreamApp not initialized")
    }
}
