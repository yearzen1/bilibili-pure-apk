package com.bilibili.pure.data.update

import android.util.Log
import com.bilibili.pure.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val apkUrl: String,
    val apkSize: Long,
    val releaseNotes: String
)

class UpdateChecker {

    companion object {
        private const val TAG = "BiliPure"
        private const val RELEASE_API =
            "https://api.github.com/repos/yearzen1/bilibili-pure-apk/releases/latest"
        val CURRENT_VERSION: String = BuildConfig.VERSION_NAME

        private data class ReleaseAsset(
            @SerializedName("name") val name: String = "",
            @SerializedName("browser_download_url") val url: String = "",
            @SerializedName("size") val size: Long = 0L
        )

        private data class Release(
            @SerializedName("tag_name") val tagName: String = "",
            @SerializedName("body") val body: String = "",
            @SerializedName("assets") val assets: List<ReleaseAsset> = emptyList()
        )
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                }
                val release = gson.fromJson(resp.body?.string(), Release::class.java)
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext Result.failure(Exception("No APK asset in release"))
                if (release.tagName.isBlank() || apkAsset.url.isBlank()) {
                    return@withContext Result.failure(Exception("Invalid release data"))
                }
                Result.success(
                    UpdateInfo(release.tagName, apkAsset.url, apkAsset.size, release.body.orEmpty())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "UpdateChecker failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isNewerVersion(latestTag: String): Boolean {
        return compareVersions(parseVersion(latestTag), parseVersion(CURRENT_VERSION)) > 0
    }

    private fun parseVersion(v: String): List<Int> =
        v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}