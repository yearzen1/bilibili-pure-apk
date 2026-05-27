package com.bilibili.pure

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bilibili.pure.data.api.BilibiliApi
import java.util.UUID

class BilibiliApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        val prefs = getSharedPreferences("bili_prefs", MODE_PRIVATE)
        if (!prefs.contains("buvid3")) {
            prefs.edit().putString("buvid3", UUID.randomUUID().toString()).apply()
        }
        BilibiliApi.buvid3 = prefs.getString("buvid3", "")!!
        val sessdata = prefs.getString("sessdata", null)
        val biliJct = prefs.getString("bili_jct", null)
        val dedeUserId = prefs.getString("dede_userid", null)
        if (!sessdata.isNullOrEmpty() && !biliJct.isNullOrEmpty() && !dedeUserId.isNullOrEmpty()) {
            BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
            Log.d(TAG, "Restored login session for UID=$dedeUserId")
        }
        Log.d(TAG, "App started")
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache(
                MemoryCache.Builder(this)
                    .maxSizeBytes(16 * 1024 * 1024)
                    .build()
            )
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
        lateinit var instance: BilibiliApp
            private set
    }
}
