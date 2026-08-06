package com.bilibili.pure.data.download

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bilibili.pure.BilibiliApp

class DownloadService : Service() {

    private val manager by lazy { DownloadManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        DownloadNotificationHelper.createChannel(this)

        manager.onProgressChanged = { id, current, total, speed ->
            val downloads = manager.getDownloads()
            val downloading = downloads.find { it.id == id }
            if (downloading != null) {
                val title = downloading.title
                val progress = if (total > 0) (current * 100 / total).toInt() else 0

                val notification = DownloadNotificationHelper.buildProgressNotification(
                    context = this,
                    title = title,
                    progress = progress,
                    isIndeterminate = total <= 0
                )
                startForeground(DownloadNotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
            }
        }

        manager.onStatusChanged = { id, status ->
            if (status == com.bilibili.pure.data.model.DownloadInfo.STATUS_COMPLETED ||
                status == com.bilibili.pure.data.model.DownloadInfo.STATUS_FAILED) {
                val downloads = manager.getDownloads()
                val activeCount = downloads.count {
                    it.status == com.bilibili.pure.data.model.DownloadInfo.STATUS_DOWNLOADING ||
                    it.status == com.bilibili.pure.data.model.DownloadInfo.STATUS_PENDING
                }
                if (activeCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloads = manager.getDownloads()
        val activeCount = downloads.count {
            it.status == com.bilibili.pure.data.model.DownloadInfo.STATUS_DOWNLOADING ||
            it.status == com.bilibili.pure.data.model.DownloadInfo.STATUS_PENDING
        }

        if (activeCount > 0) {
            val notification = DownloadNotificationHelper.buildProgressNotification(
                context = this,
                title = "准备下载...",
                progress = 0,
                isIndeterminate = true
            )
            startForeground(DownloadNotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        manager.onProgressChanged = null
        manager.onStatusChanged = null
    }
}
