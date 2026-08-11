package com.bilibili.pure.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bilibili.pure.MainActivity

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
        isIndeterminate: Boolean = false,
        paused: Boolean = false
    ): Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (paused) "暂停下载" else "正在下载")
            .setContentText(title)
            .setProgress(maxProgress, progress, isIndeterminate)
            .setContentIntent(buildContentIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun formatRemaining(seconds: Long): String {
        if (seconds <= 0) return "剩余 1 秒以下"
        return when {
            seconds < 60 -> "剩余 ${seconds} 秒"
            seconds < 3600 -> {
                val m = seconds / 60
                val s = seconds % 60
                if (s > 0) "剩余 ${m} 分 ${s} 秒" else "剩余 ${m} 分"
            }
            else -> {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                if (m > 0) "剩余 ${h} 小时 ${m} 分" else "剩余 ${h} 小时"
            }
        }
    }

    private fun buildContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showCompleted(context: Context, title: String) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(title)
            .setContentIntent(buildContentIntent(context))
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }
}