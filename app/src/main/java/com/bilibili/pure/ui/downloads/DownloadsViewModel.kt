package com.bilibili.pure.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bilibili.pure.data.download.DownloadManager
import com.bilibili.pure.data.model.DownloadInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadGroup(
    val bvid: String,
    val title: String,
    val cover: String,
    val downloads: List<DownloadInfo>
)

sealed class DownloadListItem {
    data class Group(val group: DownloadGroup) : DownloadListItem()
    data class Single(val download: DownloadInfo) : DownloadListItem()
}

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = DownloadManager.getInstance(application)

    private val _items = MutableStateFlow<List<DownloadListItem>>(emptyList())
    val items: StateFlow<List<DownloadListItem>> = _items.asStateFlow()

    private val callback: () -> Unit = { load() }

    init {
        downloadManager.onDownloadsChanged = callback
    }

    override fun onCleared() {
        super.onCleared()
        if (downloadManager.onDownloadsChanged === callback) {
            downloadManager.onDownloadsChanged = null
        }
    }

    fun load() {
        val downloads = downloadManager.getDownloads()
        val grouped = downloads.groupBy { it.bvid }
        val items = mutableListOf<DownloadListItem>()

        for ((bvid, group) in grouped) {
            if (group.size > 1) {
                val sorted = group.sortedBy { it.page }
                items.add(
                    DownloadListItem.Group(
                        DownloadGroup(
                            bvid = bvid,
                            title = sorted.first().title,
                            cover = sorted.first().cover,
                            downloads = sorted
                        )
                    )
                )
            } else {
                items.add(DownloadListItem.Single(group.first()))
            }
        }

        _items.value = items
    }

    fun deleteDownload(download: DownloadInfo) {
        downloadManager.deleteDownload(download.id)
        load()
    }

    fun deleteGroup(group: DownloadGroup) {
        group.downloads.forEach { downloadManager.deleteDownload(it.id) }
        load()
    }

    fun pauseDownload(download: DownloadInfo) {
        downloadManager.pauseDownload(download.id)
        load()
    }

    fun resumeDownload(download: DownloadInfo) {
        downloadManager.resumeDownload(download.id)
        load()
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
        load()
    }
}
