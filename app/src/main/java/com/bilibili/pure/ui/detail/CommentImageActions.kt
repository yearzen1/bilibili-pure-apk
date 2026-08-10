package com.bilibili.pure.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

object CommentImageActions {

    private const val CLIP_SUBDIR = "comment_images"
    private const val FILENAME_PREFIX = "comment_"

    private fun fixUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
        else -> url
    }

    private suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(fixUrl(url)).build()
        BilibiliApi.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.bytes() ?: throw Exception("Empty response body")
        }
    }

    suspend fun saveToGallery(context: Context, url: String) {
        try {
            val bytes = downloadBytes(url)
            val saved = withContext(Dispatchers.IO) { insertIntoMediaStore(context, bytes) }
            val msg = if (saved) "已保存到相册" else "保存失败"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertIntoMediaStore(context: Context, bytes: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$FILENAME_PREFIX${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Bilibili")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val uri = MediaStore.Images.Media.insertImage(
                    context.contentResolver,
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size),
                    "$FILENAME_PREFIX${System.currentTimeMillis()}",
                    "Bilibili comment image"
                )
                uri != null
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun copyToClipboard(context: Context, url: String) {
        try {
            val bytes = downloadBytes(url)
            val uri = withContext(Dispatchers.IO) { writeClipFile(context, bytes) }
                ?: throw Exception("无法写入缓存文件")
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, "comment_image", uri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "图片已复制", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeClipFile(context: Context, bytes: ByteArray): Uri? {
        return try {
            val dir = File(context.cacheDir, CLIP_SUBDIR).apply { mkdirs() }
            val file = File(dir, "$FILENAME_PREFIX${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}