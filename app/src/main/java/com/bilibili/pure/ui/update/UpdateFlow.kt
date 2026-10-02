package com.bilibili.pure.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.update.UpdateChecker
import com.bilibili.pure.data.update.UpdateDownloaderApi
import com.bilibili.pure.data.update.UpdateInfo
import com.bilibili.pure.data.update.UpdateSource
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Latest : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(
        val downloaded: Long,
        val total: Long,
        val speedBytesPerSec: Long,
        val isPaused: Boolean
    ) : UpdateUiState
    data object InstallPermissionNeeded : UpdateUiState
    data class LocalReady(val file: File, val info: UpdateInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

enum class CheckMode {
    /** Manual trigger (settings row): shows Checking/Latest/Error dialogs. */
    Manual,

    /** Startup trigger: fully silent — only surfaces an unprompted newer version. */
    Auto
}

private class SpeedTicker(
    var lastBytes: Long,
    var lastNanos: Long,
    var lastEmitNanos: Long,
    var speedBytesPerSec: Long
)

private fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes / 1024f / 1024f)

class UpdateFlowState(
    private val scope: CoroutineScope,
    private val downloader: UpdateDownloaderApi,
    private val source: UpdateSource = UpdateChecker(),
    private val promptedTagProvider: () -> String = { AppSettings.updatePromptedTag },
    private val promptedTagWriter: (String) -> Unit = { AppSettings.updatePromptedTag = it }
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var paused = false
    private var downloadJob: Job? = null
    private val ticker = SpeedTicker(0, 0, 0, 0)

    fun startCheck(mode: CheckMode = CheckMode.Manual) {
        if (mode == CheckMode.Manual) {
            _state.value = UpdateUiState.Checking
        }
        scope.launch {
            source.checkLatest().onSuccess { info ->
                val newer = source.isNewerVersion(info.tagName)
                _state.value = when {
                    !newer ->
                        if (mode == CheckMode.Manual) UpdateUiState.Latest else UpdateUiState.Idle
                    mode == CheckMode.Manual -> UpdateUiState.Available(info)
                    info.tagName != promptedTagProvider() -> UpdateUiState.Available(info)
                    else -> UpdateUiState.Idle
                }
            }.onFailure {
                _state.value =
                    if (mode == CheckMode.Manual) UpdateUiState.Error("网络无法连接 GitHub")
                    else UpdateUiState.Idle
            }
        }
    }

    fun retryCheck() = startCheck(CheckMode.Manual)

    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }

    fun dismissAvailable() {
        val current = _state.value as? UpdateUiState.Available
        if (current == null) {
            dismiss()
            return
        }
        promptedTagWriter(current.info.tagName)
        _state.value = UpdateUiState.Idle
    }

    fun installOrAskPermission(file: File) {
        _state.value = UpdateUiState.Idle
        if (downloader.hasInstallPermission()) {
            downloader.install(file)
        } else {
            _state.value = UpdateUiState.InstallPermissionNeeded
        }
    }

    fun startDownload(info: UpdateInfo) {
        downloader.findExisting(info.tagName, info.apkSize)?.let { local ->
            _state.value = UpdateUiState.LocalReady(local, info)
            return
        }
        paused = false
        _state.value = UpdateUiState.Downloading(
            downloaded = 0,
            total = info.apkSize,
            speedBytesPerSec = 0,
            isPaused = false
        )
        ticker.lastBytes = 0
        ticker.lastNanos = 0
        ticker.lastEmitNanos = 0
        ticker.speedBytesPerSec = 0
        downloadJob = scope.launch {
            downloader.download(
                url = info.apkUrl,
                tag = info.tagName,
                isPaused = { paused },
                progress = { d, t ->
                    val now = System.nanoTime()
                    val dtSec = (now - ticker.lastNanos) / 1_000_000_000.0
                    if (dtSec > 0.0) {
                        ticker.speedBytesPerSec =
                            ((d - ticker.lastBytes) / dtSec).toLong().coerceAtLeast(0L)
                    }
                    ticker.lastBytes = d
                    ticker.lastNanos = now
                    if (now - ticker.lastEmitNanos >= 200_000_000L || d >= t) {
                        ticker.lastEmitNanos = now
                        _state.value = UpdateUiState.Downloading(
                            downloaded = d,
                            total = t,
                            speedBytesPerSec = ticker.speedBytesPerSec,
                            isPaused = false
                        )
                    }
                }
            ).onSuccess { file ->
                installOrAskPermission(file)
            }.onFailure { e ->
                if (e is CancellationException) {
                    _state.value = UpdateUiState.Idle
                } else {
                    _state.value = UpdateUiState.Error("下载失败：${e.message}")
                }
            }
        }
    }

    fun togglePause() {
        val current = _state.value as? UpdateUiState.Downloading ?: return
        if (paused) {
            paused = false
            ticker.lastBytes = current.downloaded
            ticker.lastNanos = System.nanoTime()
            ticker.lastEmitNanos = 0
            ticker.speedBytesPerSec = 0
        } else {
            paused = true
        }
        _state.value = current.copy(isPaused = !current.isPaused)
    }

    fun cancelDownload() {
        paused = true
        downloadJob?.cancel()
        _state.value = UpdateUiState.Idle
    }

    fun openInstallPermissionSettings() {
        _state.value = UpdateUiState.Idle
        downloader.openInstallPermissionSettings()
    }
}

@Composable
fun UpdateDialogs(state: UpdateFlowState) {
    val s by state.state.collectAsState()

    when (val current = s) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("检查更新") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("正在检查更新…")
                }
            },
            confirmButton = {}
        )

        UpdateUiState.Latest -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            title = { Text("检查更新") },
            text = { Text("已是最新版本 v${UpdateChecker.CURRENT_VERSION}") },
            confirmButton = {
                TextButton(onClick = { state.dismiss() }) { Text("知道了") }
            }
        )

        is UpdateUiState.Available -> {
            val notes = current.info.releaseNotes.trim()
            Dialog(
                onDismissRequest = { state.dismissAvailable() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 600.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = "发现新版本 ${current.info.tagName}",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "当前版本 v${UpdateChecker.CURRENT_VERSION}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        if (notes.isNotBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                            MarkdownText(
                                markdown = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp, top = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { state.dismissAvailable() }) {
                                Text("稍后")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { state.startDownload(current.info) }) {
                                Text("立即更新")
                            }
                        }
                    }
                }
            }
        }

        is UpdateUiState.Downloading -> {
            val fraction = if (current.total > 0) {
                (current.downloaded.toFloat() / current.total.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val percent =
                if (current.total > 0) (current.downloaded * 100 / current.total).toInt() else 0
            val speedMbPerSec = current.speedBytesPerSec / 1024f / 1024f
            AlertDialog(
                onDismissRequest = {},
                title = { Text("下载更新") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (current.total > 0) {
                                    "已下载 ${formatMb(current.downloaded)} / ${formatMb(current.total)}"
                                } else {
                                    "正在下载… ${formatMb(current.downloaded)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (current.total > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                current.isPaused -> "已暂停"
                                current.speedBytesPerSec > 0 ->
                                    String.format("%.2f MB/s", speedMbPerSec)
                                else -> "0.00 MB/s"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { state.togglePause() }) {
                            Text(if (current.isPaused) "继续" else "暂停")
                        }
                        TextButton(onClick = { state.cancelDownload() }) {
                            Text("取消")
                        }
                    }
                }
            )
        }

        is UpdateUiState.LocalReady -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            title = { Text("安装包已下载") },
            text = {
                Text(
                    "${current.info.tagName}（${formatMb(current.file.length())}）已存在于本地，无需重新下载。"
                )
            },
            confirmButton = {
                TextButton(onClick = { state.installOrAskPermission(current.file) }) {
                    Text("直接安装")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { state.dismiss() }) {
                        Text("取消")
                    }
                    TextButton(onClick = {
                        current.file.delete()
                        state.startDownload(current.info)
                    }) { Text("重新下载") }
                }
            }
        )

        UpdateUiState.InstallPermissionNeeded -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            title = { Text("需要安装权限") },
            text = { Text("请允许「安装未知应用」权限后再试，否则无法安装更新。") },
            confirmButton = {
                TextButton(onClick = { state.openInstallPermissionSettings() }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { state.dismiss() }) { Text("取消") }
            }
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            title = { Text("更新失败") },
            text = { Text(current.message) },
            confirmButton = {
                TextButton(onClick = { state.retryCheck() }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { state.dismiss() }) { Text("返回") }
            }
        )
    }
}
