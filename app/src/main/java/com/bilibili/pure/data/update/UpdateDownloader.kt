package com.bilibili.pure.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
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
        private const val FILE_PREFIX = "bilibili-pure-"

        /** Builds the versioned APK file name for a release tag, sanitizing unsafe characters. */
        fun apkFileName(tag: String): String {
            val safe = tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return "$FILE_PREFIX$safe.apk"
        }

        /** A local APK is reusable only when it exists, is non-empty and matches the release size. */
        fun isValidApk(file: File, expectedSize: Long): Boolean {
            if (expectedSize <= 0) return false
            return file.exists() && file.isFile && file.length() == expectedSize && file.length() > 0
        }

        /**
         * Identity check for a locally parsed APK archive: it must belong to [expectedPackage]
         * and its internal versionName must equal the release [tag] (with or without `v` prefix).
         * Null metadata (corrupt/unparseable archive) never matches.
         */
        fun matchesRelease(
            pkg: String?,
            versionName: String?,
            expectedPackage: String,
            tag: String
        ): Boolean {
            if (pkg == null || versionName == null) return false
            return pkg == expectedPackage && versionName == tag.removePrefix("v")
        }
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
        tag: String,
        progress: (downloaded: Long, total: Long) -> Unit,
        isPaused: () -> Boolean
    ): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val dir = updatesDir()
                dir.mkdirs()
                dir.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".apk") && f.name != apkFileName(tag)) f.delete()
                }
                val target = File(dir, apkFileName(tag))
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
                deleteTarget(tag)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "UpdateDownloader failed: ${e.message}", e)
                deleteTarget(tag)
                Result.failure(e)
            }
        }

    /**
     * Returns a previously downloaded APK for [tag] only when it passes three checks:
     * file size matches the GitHub release asset, the archive parses, and its packageName +
     * versionName identify exactly this app and release. A file failing any check is deleted
     * so the caller falls back to a fresh download.
     */
    fun findExisting(tag: String, expectedSize: Long): File? {
        val file = targetFile(tag)
        if (!isValidApk(file, expectedSize)) return null
        val info = readArchive(file)
        val ok = info != null && matchesRelease(
            info.packageName, info.versionName, context.packageName, tag
        )
        return if (ok) file else {
            file.delete()
            null
        }
    }

    private fun readArchive(file: File): PackageInfo? = try {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
    } catch (e: Exception) {
        Log.e(TAG, "readArchive failed: ${e.message}", e)
        null
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

    private fun updatesDir(): File = File(context.getExternalFilesDir(null), "updates")

    private fun targetFile(tag: String): File = File(updatesDir(), apkFileName(tag))

    private fun deleteTarget(tag: String) {
        val f = targetFile(tag)
        if (f.exists()) f.delete()
    }
}