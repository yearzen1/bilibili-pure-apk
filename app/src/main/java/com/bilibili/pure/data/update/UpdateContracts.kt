package com.bilibili.pure.data.update

import java.io.File

/** Abstraction over [UpdateChecker] so update flow logic can be unit tested with fakes. */
interface UpdateSource {
    suspend fun checkLatest(): Result<UpdateInfo>

    fun isNewerVersion(latestTag: String): Boolean
}

/** Abstraction over [UpdateDownloader] so update flow logic can be unit tested with fakes. */
interface UpdateDownloaderApi {
    fun findExisting(tag: String, expectedSize: Long): File?

    suspend fun download(
        url: String,
        tag: String,
        progress: (downloaded: Long, total: Long) -> Unit,
        isPaused: () -> Boolean
    ): Result<File>

    fun install(apkFile: File): Boolean

    fun hasInstallPermission(): Boolean

    fun openInstallPermissionSettings()
}
