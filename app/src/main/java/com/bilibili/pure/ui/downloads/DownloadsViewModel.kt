package com.bilibili.pure.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bilibili.pure.data.download.DownloadManager
import com.bilibili.pure.data.model.DownloadInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = DownloadManager.getInstance(application)

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

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
        _downloads.value = downloadManager.getDownloads()
    }

    fun deleteDownload(download: DownloadInfo) {
        downloadManager.deleteDownload(download.id)
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
