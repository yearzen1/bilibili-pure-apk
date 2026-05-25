package com.bilibili.pure

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.bilibili.pure.data.api.BilibiliApi

class BilibiliApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "App started")
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .okHttpClient { BilibiliApi.httpClient }
            .crossfade(true)
            .build()
    }

    companion object {
        const val TAG = "BiliPure"
    }
}
