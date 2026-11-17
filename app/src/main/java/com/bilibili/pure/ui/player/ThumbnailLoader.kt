package com.bilibili.pure.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 进度条缩略图雪碧图的下载、降采样解码与内存缓存。
 *
 * 整张雪碧图可能高达 4800x2700（约 52MB），这里按最大宽度 [MAX_WIDTH_PX]
 * 做 2 的幂降采样并使用 RGB_565，单张约 6.5MB；LRU 只保留最近使用的 [CACHE_SIZE] 张。
 */
object ThumbnailLoader {
    private const val MAX_WIDTH_PX = 2400
    private const val CACHE_SIZE = 2
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 命中缓存直接返回，否则下载并解码；失败返回 null。 */
    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = fetch(url) ?: return@withContext null
                val bitmap = decodeSampled(bytes) ?: return@withContext null
                cache.put(url, bitmap)
                if (BuildConfig.DEBUG) {
                    Log.d(BilibiliApp.TAG, "ThumbnailLoader: decoded ${bitmap.width}x${bitmap.height}")
                }
                bitmap
            }.onFailure { e ->
                if (BuildConfig.DEBUG) Log.w(BilibiliApp.TAG, "ThumbnailLoader: failed ${e.message}")
            }.getOrNull()
        }
    }

    private fun fetch(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        BilibiliApi.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_WIDTH_PX) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
