package com.bilibili.pure

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.download.DownloadManager
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.local.WifiMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class BilibiliApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppSettings.init(this)
        val prefs = getSharedPreferences("bili_prefs", MODE_PRIVATE)
        if (!prefs.contains("buvid3")) {
            prefs.edit().putString("buvid3", UUID.randomUUID().toString()).apply()
        }
        BilibiliApi.buvid3 = prefs.getString("buvid3", "")!!
        BilibiliApi.activateBuvidIfNeeded(appScope)
        val sessdata = prefs.getString("sessdata", null)
        val biliJct = prefs.getString("bili_jct", null)
        val dedeUserId = prefs.getString("dede_userid", null)
        if (!sessdata.isNullOrEmpty() && !biliJct.isNullOrEmpty() && !dedeUserId.isNullOrEmpty()) {
            BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
            Log.d(TAG, "Restored login session for UID=$dedeUserId")
        }
        Log.d(TAG, "App started")
        WifiMonitor.start(this)
        appScope.launch {
            WifiMonitor.wifiLost.collect {
                DownloadManager.getInstance(this@BilibiliApp).pauseForWifi()
            }
        }
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
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
