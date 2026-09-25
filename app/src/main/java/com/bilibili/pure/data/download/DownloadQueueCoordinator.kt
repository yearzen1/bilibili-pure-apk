package com.bilibili.pure.data.download

import com.bilibili.pure.data.model.DownloadInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class DownloadListStore(
    private val readAll: () -> List<DownloadInfo>,
    private val writeAll: (List<DownloadInfo>) -> Unit
) {
    private val lock = Any()

    fun all(): List<DownloadInfo> = synchronized(lock) { readAll() }

    fun enqueuePending(requests: List<DownloadInfo>): List<DownloadInfo> = synchronized(lock) {
        if (requests.isEmpty()) {
            return@synchronized emptyList()
        }
        val current = readAll().associateBy { it.id }.toMutableMap()
        val queued = mutableListOf<DownloadInfo>()
        var changed = false
        for (request in requests) {
            val existing = current[request.id]
            if (existing != null &&
                existing.status != DownloadInfo.STATUS_PENDING &&
                existing.status != DownloadInfo.STATUS_PAUSED &&
                existing.status != DownloadInfo.STATUS_FAILED
            ) {
                continue
            }
            current[request.id] = request
            changed = true
            queued.add(request)
        }
        if (changed) {
            writeAll(current.values.toList())
        }
        queued
    }

    fun update(id: String, transform: (DownloadInfo) -> DownloadInfo): DownloadInfo? =
        synchronized(lock) {
            val downloads = readAll().toMutableList()
            val index = downloads.indexOfFirst { it.id == id }
            if (index < 0) {
                return@synchronized null
            }
            val updated = transform(downloads[index])
            downloads[index] = updated
            writeAll(downloads)
            updated
        }

    fun remove(id: String): DownloadInfo? = synchronized(lock) {
        val downloads = readAll().toMutableList()
        val removed = downloads.firstOrNull { it.id == id } ?: return@synchronized null
        downloads.removeAll { it.id == id }
        writeAll(downloads)
        removed
    }

    fun removeCompleted(): List<DownloadInfo> = synchronized(lock) {
        val downloads = readAll()
        val completed = downloads.filter { it.status == DownloadInfo.STATUS_COMPLETED }
        if (completed.isNotEmpty()) {
            writeAll(downloads.filter { it.status != DownloadInfo.STATUS_COMPLETED })
        }
        completed
    }
}

internal class DownloadTransferGate(private val maxConcurrent: Int) {
    private val semaphore = Semaphore(maxConcurrent)

    suspend fun <T> run(block: suspend () -> T): T = semaphore.withPermit { block() }
}

internal class DownloadQueueCoordinator(
    private val scope: CoroutineScope,
    private val persistPending: (List<DownloadInfo>) -> List<DownloadInfo>,
    private val resolveUrl: suspend (DownloadInfo) -> String?,
    private val startTransfer: (DownloadInfo, String) -> Unit,
    private val markFailed: (DownloadInfo) -> Unit
) {
    fun enqueue(requests: List<DownloadInfo>): Int {
        val queued = persistPending(requests)
        if (queued.isEmpty()) {
            return 0
        }
        scope.launch {
            for (download in queued) {
                try {
                    val url = resolveUrl(download)
                    if (url.isNullOrEmpty()) {
                        markFailed(download)
                    } else {
                        startTransfer(download, url)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markFailed(download)
                }
            }
        }
        return queued.size
    }
}
