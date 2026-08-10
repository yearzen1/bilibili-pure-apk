package com.bilibili.pure.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateDownloader(private val context: Context) {

    companion object {
        private const val TAG = "BiliPure"
        private const val FILE_NAME = "bilibili-pure-update.apk"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads the update APK to a fixed app-private file. While [isPaused] evaluates to
     * true the download is suspended in place (connection stays open). Early EOF before the
     * full length is reached is treated as a failure so a truncated APK is never kept.
     */
    suspend fun download(
        url: String,
        progress: (downloaded: Long, total: Long) -> Unit,
        isPaused: () -> Boolean
    ): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                val target = File(dir, FILE_NAME)
                if (target.exists()) target.delete()

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    }
                    val body = resp.body
                        ?: return@withContext Result.failure(Exception("Empty response body"))
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            var read: Int
                            while (true) {
                                while (isPaused()) {
                                    delay(200)
                                }
                                read = input.read(buffer)
                                if (read == -1) {
                                    if (downloaded < total) {
                                        throw IOException(
                                            "连接中断，已下载 ${downloaded}/$total 字节"
                                        )
                                    }
                                    break
                                }
                                output.write(buffer, 0, read)
                                downloaded += read
                                progress(downloaded, total)
                            }
                            output.flush()
                        }
                    }
                    Result.success(target)
                }
            } catch (e: CancellationException) {
                if (targetSafe(context).exists()) targetSafe(context).delete()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "UpdateDownloader failed: ${e.message}", e)
                if (targetSafe(context).exists()) targetSafe(context).delete()
                Result.failure(e)
            }
        }

    fun install(apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "UpdateDownloader install failed: ${e.message}", e)
            false
        }
    }

    /** Whether this app is allowed to install packages from unknown sources. */
    fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Opens the per-app "Install unknown apps" settings page for this app. */
    fun openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UpdateDownloader openInstallPermissionSettings failed: ${e.message}", e)
        }
    }

    private fun targetSafe(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "updates")
        return File(dir, FILE_NAME)
    }
}