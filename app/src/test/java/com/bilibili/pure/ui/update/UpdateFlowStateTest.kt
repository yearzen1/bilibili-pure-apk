package com.bilibili.pure.ui.update

import com.bilibili.pure.data.update.UpdateDownloaderApi
import com.bilibili.pure.data.update.UpdateInfo
import com.bilibili.pure.data.update.UpdateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateFlowStateTest {

    private class FakeSource(
        var result: Result<UpdateInfo>,
        var newer: Boolean
    ) : UpdateSource {
        var checkCount = 0

        override suspend fun checkLatest(): Result<UpdateInfo> {
            checkCount++
            return result
        }

        override fun isNewerVersion(latestTag: String): Boolean = newer
    }

    private class FakeDownloader : UpdateDownloaderApi {
        var existing: File? = null

        override fun findExisting(tag: String, expectedSize: Long): File? = existing

        override suspend fun download(
            url: String,
            tag: String,
            progress: (downloaded: Long, total: Long) -> Unit,
            isPaused: () -> Boolean
        ): Result<File> = Result.failure(IllegalStateException("not used in test"))

        override fun install(apkFile: File): Boolean = true

        override fun hasInstallPermission(): Boolean = true

        override fun openInstallPermissionSettings() {}
    }

    private val info = UpdateInfo(
        tagName = "v9.9.9",
        apkUrl = "https://example.com/app.apk",
        apkSize = 1000L,
        releaseNotes = "notes"
    )

    private fun state(
        source: FakeSource,
        promptedTag: String = "",
        onPrompted: (String) -> Unit = {}
    ) = UpdateFlowState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        downloader = FakeDownloader(),
        source = source,
        promptedTagProvider = { promptedTag },
        promptedTagWriter = onPrompted
    )

    // --- CheckMode.Auto (startup) ---

    @Test
    fun auto_check_shows_available_for_unprompted_new_version() {
        val s = state(FakeSource(Result.success(info), newer = true))
        s.startCheck(CheckMode.Auto)
        assertEquals(UpdateUiState.Available(info), s.state.value)
    }

    @Test
    fun auto_check_stays_silent_when_tag_already_prompted() {
        val src = FakeSource(Result.success(info), newer = true)
        val s = state(src, promptedTag = "v9.9.9")
        s.startCheck(CheckMode.Auto)
        assertEquals(UpdateUiState.Idle, s.state.value)
        assertEquals(1, src.checkCount)
    }

    @Test
    fun auto_check_stays_silent_when_no_new_version() {
        val src = FakeSource(Result.success(info), newer = false)
        val s = state(src)
        s.startCheck(CheckMode.Auto)
        assertEquals(UpdateUiState.Idle, s.state.value)
        assertEquals(1, src.checkCount)
    }

    @Test
    fun auto_check_stays_silent_on_network_error() {
        val s = state(FakeSource(Result.failure(Exception("timeout")), newer = true))
        s.startCheck(CheckMode.Auto)
        assertEquals(UpdateUiState.Idle, s.state.value)
    }

    // --- CheckMode.Manual (settings screen) ---

    @Test
    fun manual_check_shows_available_for_new_version() {
        val s = state(FakeSource(Result.success(info), newer = true))
        s.startCheck(CheckMode.Manual)
        assertEquals(UpdateUiState.Available(info), s.state.value)
    }

    @Test
    fun manual_check_shows_latest_when_no_new_version() {
        val s = state(FakeSource(Result.success(info), newer = false))
        s.startCheck(CheckMode.Manual)
        assertEquals(UpdateUiState.Latest, s.state.value)
    }

    @Test
    fun manual_check_shows_error_on_failure() {
        val s = state(FakeSource(Result.failure(Exception("HTTP 500")), newer = false))
        s.startCheck(CheckMode.Manual)
        assertTrue(s.state.value is UpdateUiState.Error)
    }

    // --- prompted-tag bookkeeping ---

    @Test
    fun dismiss_available_records_prompted_tag_and_resets() {
        var recorded: String? = null
        val s = state(
            FakeSource(Result.success(info), newer = true),
            onPrompted = { recorded = it }
        )
        s.startCheck(CheckMode.Auto)
        s.dismissAvailable()
        assertEquals("v9.9.9", recorded)
        assertEquals(UpdateUiState.Idle, s.state.value)
    }

    @Test
    fun plain_dismiss_does_not_record_prompted_tag() {
        var recorded: String? = null
        val s = state(
            FakeSource(Result.success(info), newer = true),
            onPrompted = { recorded = it }
        )
        s.startCheck(CheckMode.Auto)
        s.dismiss()
        assertEquals(null, recorded)
        assertEquals(UpdateUiState.Idle, s.state.value)
    }

    // --- download flow entry ---

    @Test
    fun start_download_uses_local_existing_apk() {
        val existing = File("bilibili-pure-v9.9.9.apk")
        val downloader = FakeDownloader().also { it.existing = existing }
        val s = UpdateFlowState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            downloader = downloader,
            source = FakeSource(Result.success(info), newer = true),
            promptedTagProvider = { "" },
            promptedTagWriter = {}
        )
        s.startDownload(info)
        assertEquals(UpdateUiState.LocalReady(existing, info), s.state.value)
    }
}
