package com.bilibili.pure.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bilibili.pure.R

object DownloadNotificationHelper {
    private const val CHANNEL_ID = "bilibili_download"
    private const val CHANNEL_NAME = "视频下载"
    const val NOTIFICATION_ID_PROGRESS = 1001
    const val NOTIFICATION_ID_COMPLETE = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频下载进度"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        progress: Int,
        maxProgress: Int = 100,
        isIndeterminate: Boolean = false
    ): android.app.Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载")
            .setContentText(title)
            .setProgress(maxProgress, progress, isIndeterminate)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showCompleted(context: Context, title: String) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(title)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }
}
