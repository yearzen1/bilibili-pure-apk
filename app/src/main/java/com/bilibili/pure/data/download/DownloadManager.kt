package com.bilibili.pure.data.download

import android.content.Context
import android.util.Log
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.DownloadInfo
import com.bilibili.pure.data.local.AppSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(private val context: Context) {

    companion object {
        private const val TAG = BilibiliApp.TAG
        private const val PREFS_NAME = "bili_downloads"
        private const val KEY_DOWNLOADS = "downloads_list"

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedBytes = ConcurrentHashMap<String, Long>()

    var onProgressChanged: ((String, Long, Long, Long) -> Unit)? = null
    var onStatusChanged: ((String, Int) -> Unit)? = null
    var onDownloadsChanged: (() -> Unit)? = null

    fun getDownloads(): List<DownloadInfo> {
        val json = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getDownloads parse error", e)
            emptyList()
        }
    }

    fun getDownload(id: String): DownloadInfo? {
        return getDownloads().find { it.id == id }
    }

    private fun saveDownloads(downloads: List<DownloadInfo>) {
        prefs.edit().putString(KEY_DOWNLOADS, gson.toJson(downloads)).apply()
    }

    private fun updateDownload(id: String, update: (DownloadInfo) -> DownloadInfo) {
        val downloads = getDownloads().toMutableList()
        val index = downloads.indexOfFirst { it.id == id }
        if (index >= 0) {
            downloads[index] = update(downloads[index])
            saveDownloads(downloads)
            onStatusChanged?.invoke(id, downloads[index].status)
            onDownloadsChanged?.invoke()
        }
    }

    fun startDownload(
        bvid: String,
        cid: Long,
        title: String,
        cover: String,
        quality: Int,
        qualityDesc: String,
        url: String
    ) {
        if (AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)) {
            Log.d(TAG, "Download blocked: WiFi-only mode and not on WiFi")
            return
        }
        val id = "${bvid}_${cid}"
        val existing = getDownload(id)
        if (existing != null && existing.status == DownloadInfo.STATUS_COMPLETED) {
            Log.d(TAG, "Download already completed: $id")
            return
        }
        if (existing != null && existing.status == DownloadInfo.STATUS_DOWNLOADING) {
            Log.d(TAG, "Download already in progress: $id")
            return
        }

        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val ext = "mp4"
        val safeTitle = title.replace(Regex("[^\\w\\u4e00-\\u9fff\\-]"), "_").take(50)
        val fileName = "${safeTitle}_${qualityDesc}.${ext}"
        val file = File(downloadsDir, fileName)

        val info = DownloadInfo(
            id = id,
            bvid = bvid,
            cid = cid,
            title = title,
            cover = cover,
            quality = quality,
            qualityDesc = qualityDesc,
            filePath = file.absolutePath,
            fileSize = file.length(),
            totalSize = -1,
            status = DownloadInfo.STATUS_PENDING,
            createTime = System.currentTimeMillis()
        )

        val downloads = getDownloads().toMutableList()
        val existIdx = downloads.indexOfFirst { it.id == id }
        if (existIdx >= 0) {
            downloads[existIdx] = info
        } else {
            downloads.add(info)
        }
        saveDownloads(downloads)
        onStatusChanged?.invoke(id, DownloadInfo.STATUS_PENDING)

        val job = scope.launch {
            downloadFile(info, url)
        }
        activeJobs[id] = job
    }

    private suspend fun downloadFile(info: DownloadInfo, url: String) {
        val file = File(info.filePath)
        val startBytes = pausedBytes.remove(info.id) ?: file.length()

        updateDownload(info.id) { it.copy(status = DownloadInfo.STATUS_DOWNLOADING) }

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .header("Cookie", buildString {
                    append("buvid3=${BilibiliApi.buvid3}")
                    if (BilibiliApi.loginCookies.isNotEmpty()) {
                        append("; ${BilibiliApi.loginCookies}")
                    }
                })

            if (startBytes > 0) {
                requestBuilder.header("Range", "bytes=$startBytes-")
            }

            val request = requestBuilder.build()
            val client = BilibiliApi.httpClient

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}")
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val totalSize = if (startBytes > 0 && contentLength > 0) {
                        startBytes + contentLength
                    } else if (contentLength > 0) {
                        contentLength.toLong()
                    } else {
                        -1L
                    }

                    if (totalSize > 0) {
                        updateDownload(info.id) { it.copy(totalSize = totalSize) }
                    }

                    body.byteStream().use { input ->
                        val outputStream = if (startBytes > 0) {
                            FileOutputStream(file, true)
                        } else {
                            FileOutputStream(file)
                        }

                        outputStream.use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = startBytes
                            var lastSpeedTime = System.currentTimeMillis()
                            var lastSpeedBytes = startBytes

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                val currentSize = file.length()
                                val now = System.currentTimeMillis()
                                val elapsed = now - lastSpeedTime
                                if (elapsed >= 1000) {
                                    val speed = ((currentSize - lastSpeedBytes) * 1000) / elapsed
                                    lastSpeedTime = now
                                    lastSpeedBytes = currentSize
                                    onProgressChanged?.invoke(info.id, currentSize, totalSize, speed)
                                    updateDownload(info.id) { it.copy(fileSize = currentSize, speed = speed) }
                                } else {
                                    onProgressChanged?.invoke(info.id, currentSize, totalSize, 0)
                                }
                            }

                            updateDownload(info.id) { it.copy(fileSize = file.length(), speed = 0) }
                        }
                    }
                }
            }

            updateDownload(info.id) {
                it.copy(
                    status = DownloadInfo.STATUS_COMPLETED,
                    fileSize = file.length()
                )
            }
            Log.d(TAG, "Download completed: ${info.id} size=${file.length()}")
            DownloadNotificationHelper.showCompleted(context, info.title)

            checkAllDone()

        } catch (e: CancellationException) {
            val currentSize = file.length()
            pausedBytes[info.id] = currentSize
            updateDownload(info.id) {
                it.copy(
                    status = DownloadInfo.STATUS_PAUSED,
                    fileSize = currentSize
                )
            }
            Log.d(TAG, "Download paused: ${info.id} at $currentSize bytes")
        } catch (e: Exception) {
            val currentSize = file.length()
            updateDownload(info.id) {
                it.copy(
                    status = DownloadInfo.STATUS_FAILED,
                    fileSize = currentSize
                )
            }
            Log.e(TAG, "Download failed: ${info.id}", e)
        }
    }

    fun pauseDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        val currentSize = File(getDownload(id)?.filePath ?: "").length()
        if (currentSize > 0) {
            pausedBytes[id] = currentSize
        }
        updateDownload(id) { it.copy(status = DownloadInfo.STATUS_PAUSED, fileSize = currentSize) }
    }

    fun resumeDownload(id: String) {
        val download = getDownload(id) ?: return
        if (download.status != DownloadInfo.STATUS_PAUSED && download.status != DownloadInfo.STATUS_FAILED) return

        val url = getDownloadUrl(download) ?: return
        val job = scope.launch {
            downloadFile(download, url)
        }
        activeJobs[id] = job
    }

    private fun getDownloadUrl(download: DownloadInfo): String? {
        val bvid = download.bvid
        val cid = download.cid
        return try {
            val api = BilibiliApi.create()
            val response = runBlocking {
                api.getPlayUrl(bvid = bvid, cid = cid, qn = download.quality)
            }
            if (response.code == 0) {
                response.data?.durl?.firstOrNull()?.url
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download URL for $bvid", e)
            null
        }
    }

    fun cancelDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        pausedBytes.remove(id)
        val download = getDownload(id)
        if (download != null) {
            File(download.filePath).delete()
        }
        val downloads = getDownloads().toMutableList()
        downloads.removeAll { it.id == id }
        saveDownloads(downloads)
    }

    fun deleteDownload(id: String) {
        cancelDownload(id)
    }

    private fun checkAllDone() {
        // No foreground service to stop
    }

    fun clearCompleted() {
        val downloads = getDownloads()
        downloads.filter { it.status == DownloadInfo.STATUS_COMPLETED }.forEach { d ->
            File(d.filePath).delete()
        }
        saveDownloads(downloads.filter { it.status != DownloadInfo.STATUS_COMPLETED })
    }
}
