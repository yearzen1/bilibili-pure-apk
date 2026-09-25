package com.bilibili.pure.data.download

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.DownloadInfo
import com.bilibili.pure.data.repository.BilibiliRepository
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
        private const val MAX_CONCURRENT_DOWNLOADS = 3

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
    private val store = DownloadListStore(
        readAll = { readDownloads() },
        writeAll = { downloads ->
            prefs.edit().putString(KEY_DOWNLOADS, gson.toJson(downloads)).apply()
        }
    )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedBytes = ConcurrentHashMap<String, Long>()
    private val resolvingIds = ConcurrentHashMap.newKeySet<String>()
    private val transferGate = DownloadTransferGate(MAX_CONCURRENT_DOWNLOADS)

    var onProgressChanged: ((String, Long, Long, Long) -> Unit)? = null
    var onStatusChanged: ((String, Int) -> Unit)? = null
    var onDownloadsChanged: (() -> Unit)? = null

    private val queueCoordinator = DownloadQueueCoordinator(
        scope = scope,
        persistPending = ::enqueuePendingDownloads,
        resolveUrl = ::resolveDownloadUrl,
        startTransfer = ::startResolvedDownload,
        markFailed = ::markResolutionFailed
    )

    private fun readDownloads(): List<DownloadInfo> {
        val json = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getDownloads parse error", e)
            emptyList()
        }
    }

    fun getDownloads(): List<DownloadInfo> = store.all()

    fun getDownload(id: String): DownloadInfo? = store.all().find { it.id == id }

    private fun updateDownload(id: String, update: (DownloadInfo) -> DownloadInfo) {
        val updated = store.update(id, update) ?: return
        onStatusChanged?.invoke(id, updated.status)
        onDownloadsChanged?.invoke()
    }

    private fun enqueuePendingDownloads(requests: List<DownloadInfo>): List<DownloadInfo> {
        val queued = store.enqueuePending(requests).filter { resolvingIds.add(it.id) }
        if (queued.isNotEmpty()) {
            onDownloadsChanged?.invoke()
        }
        return queued
    }

    fun startDownload(
        bvid: String,
        cid: Long,
        title: String,
        cover: String,
        quality: Int,
        qualityDesc: String,
        url: String,
        overrideWifiOnly: Boolean = false,
        page: Int = 1,
        part: String = "",
        aid: Long = 0
    ) {
        if (!overrideWifiOnly && AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)) {
            Log.d(TAG, "Download blocked: WiFi-only mode and not on WiFi")
            return
        }
        val info = buildDownloadInfo(
            bvid = bvid,
            cid = cid,
            title = title,
            cover = cover,
            quality = quality,
            qualityDesc = qualityDesc,
            page = page,
            part = part,
            aid = aid
        )
        val queued = enqueuePendingDownloads(listOf(info))
        if (queued.isEmpty()) {
            Log.d(TAG, "Download skipped: ${info.id}")
            return
        }
        onStatusChanged?.invoke(info.id, DownloadInfo.STATUS_PENDING)
        startResolvedDownload(queued.first(), url)
    }

    private fun buildDownloadInfo(
        bvid: String,
        cid: Long,
        title: String,
        cover: String,
        quality: Int,
        qualityDesc: String,
        page: Int = 1,
        part: String = "",
        aid: Long = 0
    ): DownloadInfo {
        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val safeTitle = title.replace(Regex("[^\\w\\u4e00-\\u9fff\\-]"), "_").take(50)
        val pagePrefix = if (part.isNotEmpty()) {
            "P${page} ${part.replace(Regex("[^\\w\\u4e00-\\u9fff\\-]"), "_").take(30)}_"
        } else {
            ""
        }
        val file = File(downloadsDir, "${pagePrefix}${safeTitle}_${qualityDesc}.mp4")
        return DownloadInfo(
            id = "${bvid}_${cid}",
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
            createTime = System.currentTimeMillis(),
            page = page,
            part = part,
            aid = aid
        )
    }

    data class BatchPageInfo(
        val cid: Long,
        val page: Int,
        val part: String,
        val aid: Long = 0
    )

    fun startBatchDownload(
        bvid: String,
        videoTitle: String,
        cover: String,
        pages: List<BatchPageInfo>,
        quality: Int,
        qualityDesc: String
    ): Int {
        if (pages.isEmpty()) return 0
        val requests = pages.map { page ->
            buildDownloadInfo(
                bvid = bvid,
                cid = page.cid,
                title = videoTitle,
                cover = cover,
                quality = quality,
                qualityDesc = qualityDesc,
                page = page.page,
                part = page.part,
                aid = page.aid
            )
        }
        val queued = queueCoordinator.enqueue(requests)
        if (queued > 0) {
            startForegroundService()
            Log.d(TAG, "Batch download queued: $queued/${pages.size} pages for $bvid")
        }
        return queued
    }

    private suspend fun resolveDownloadUrl(download: DownloadInfo): String? {
        val api = BilibiliApi.create()
        val response = withContext(Dispatchers.IO) {
            api.getPlayUrl(bvid = download.bvid, cid = download.cid, qn = download.quality)
        }
        if (response.code != 0) {
            Log.w(TAG, "Batch download API error for P${download.page}: ${response.message}")
            return null
        }
        val url = response.data?.durl?.firstOrNull()?.url
        if (url.isNullOrEmpty()) {
            Log.w(TAG, "Batch download: no URL for P${download.page} ${download.part}")
        }
        return url
    }

    private fun startResolvedDownload(info: DownloadInfo, url: String) {
        resolvingIds.remove(info.id)
        val current = getDownload(info.id)
        if (current == null || current.status != DownloadInfo.STATUS_PENDING) {
            Log.d(TAG, "Resolved download no longer pending: ${info.id}")
            return
        }
        if (activeJobs[info.id]?.isActive == true) {
            return
        }
        startForegroundService()
        activeJobs[info.id] = scope.launch {
            transferGate.run {
                downloadFile(current, url)
            }
        }
    }

    private fun markResolutionFailed(info: DownloadInfo) {
        resolvingIds.remove(info.id)
        val current = getDownload(info.id) ?: return
        if (current.status != DownloadInfo.STATUS_PENDING) return
        updateDownload(info.id) { it.copy(status = DownloadInfo.STATUS_FAILED, speed = 0) }
        Log.w(TAG, "Batch download failed to resolve: ${info.id}")
    }

    private fun startForegroundService() {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
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

            downloadSubtitles(info, file)

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
            val isWifiOnlyBlocked = AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)
            updateDownload(info.id) {
                it.copy(
                    status = if (isWifiOnlyBlocked) DownloadInfo.STATUS_PAUSED else DownloadInfo.STATUS_FAILED,
                    fileSize = currentSize
                )
            }
            Log.e(TAG, "Download failed: ${info.id}", e)
        }
    }

    private suspend fun downloadSubtitles(info: DownloadInfo, videoFile: File) {
        if (info.aid == 0L) {
            Log.d(TAG, "Subtitle skip: aid=0 for ${info.id}")
            return
        }
        try {
            val repository = BilibiliRepository()
            val tracks = repository.getSubtitleTracks(info.aid, info.cid).getOrNull()
            if (tracks.isNullOrEmpty()) {
                Log.d(TAG, "Subtitle skip: no tracks for ${info.id}")
                return
            }
            val baseName = videoFile.nameWithoutExtension
            val dir = videoFile.parentFile ?: return
            for (track in tracks) {
                try {
                    val body = repository.getSubtitleContent(track.subtitleUrl).getOrNull() ?: continue
                    val subFile = File(dir, "${baseName}_${track.lan}.json")
                    withContext(Dispatchers.IO) {
                        subFile.writeText(Gson().toJson(body))
                    }
                    Log.d(TAG, "Subtitle saved: ${subFile.name} (${body.body.size} cues)")
                } catch (e: Exception) {
                    Log.w(TAG, "Subtitle download failed for ${track.lan}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Subtitle tracks fetch failed: ${e.message}")
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

    fun pauseForWifi() {
        val active = getDownloads().filter {
            it.status == DownloadInfo.STATUS_PENDING || it.status == DownloadInfo.STATUS_DOWNLOADING
        }
        if (active.isEmpty()) return
        Log.d(TAG, "Pausing ${active.size} downloads for wifi loss")
        active.forEach { d ->
            pauseDownload(d.id)
        }
    }

    fun resumeDownload(id: String) {
        val download = getDownload(id) ?: return
        if (download.status != DownloadInfo.STATUS_PAUSED && download.status != DownloadInfo.STATUS_FAILED) return

        val url = getDownloadUrl(download) ?: return
        startForegroundService()
        val job = scope.launch {
            transferGate.run {
                downloadFile(download, url)
            }
        }
        activeJobs[id] = job
    }

    private fun getDownloadUrl(download: DownloadInfo): String? {
        return try {
            runBlocking { resolveDownloadUrl(download) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download URL for ${download.bvid}", e)
            null
        }
    }

    fun cancelDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        pausedBytes.remove(id)
        resolvingIds.remove(id)
        val download = store.remove(id)
        if (download != null) {
            File(download.filePath).delete()
            deleteSubtitleFiles(download.filePath)
        }
        onDownloadsChanged?.invoke()
    }

    fun deleteDownload(id: String) {
        cancelDownload(id)
    }

    private fun checkAllDone() {
        // No foreground service to stop
    }

    fun clearCompleted() {
        store.removeCompleted().forEach { download ->
            File(download.filePath).delete()
            deleteSubtitleFiles(download.filePath)
        }
        onDownloadsChanged?.invoke()
    }

    private fun deleteSubtitleFiles(videoFilePath: String) {
        val file = File(videoFilePath)
        val dir = file.parentFile ?: return
        val baseName = file.nameWithoutExtension
        dir.listFiles()?.filter {
            it.name.startsWith("${baseName}_") && it.name.endsWith(".json")
        }?.forEach { it.delete() }
    }
}
