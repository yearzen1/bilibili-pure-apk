package com.bilibili.pure.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.update.UpdateChecker
import com.bilibili.pure.data.update.UpdateDownloader
import com.bilibili.pure.data.update.UpdateInfo
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    object Latest : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(
        val downloaded: Long,
        val total: Long,
        val speedBytesPerSec: Long,
        val isPaused: Boolean
    ) : UpdateUiState
    object InstallPermissionNeeded : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

private class SpeedTicker(
    var lastBytes: Long,
    var lastNanos: Long,
    var lastEmitNanos: Long,
    var speedBytesPerSec: Long
)

private val SectionShape = RoundedCornerShape(16.dp)

private fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes / 1024f / 1024f)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    var wifiOnlyPlayback by remember { mutableStateOf(AppSettings.wifiOnlyPlayback) }
    var wifiOnlyDownload by remember { mutableStateOf(AppSettings.wifiOnlyDownload) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { UpdateChecker() }
    val downloader = remember { UpdateDownloader(context.applicationContext) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var paused by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val ticker = remember { SpeedTicker(0, 0, 0, 0) }

    fun startCheck() {
        updateState = UpdateUiState.Checking
        scope.launch {
            checker.checkLatest().onSuccess { info ->
                updateState = if (checker.isNewerVersion(info.tagName)) {
                    UpdateUiState.Available(info)
                } else {
                    UpdateUiState.Latest
                }
            }.onFailure {
                updateState = UpdateUiState.Error("网络无法连接 GitHub")
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        paused = false
        updateState = UpdateUiState.Downloading(
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
                        updateState = UpdateUiState.Downloading(
                            downloaded = d,
                            total = t,
                            speedBytesPerSec = ticker.speedBytesPerSec,
                            isPaused = false
                        )
                    }
                }
            ).onSuccess { file ->
                updateState = UpdateUiState.Idle
                if (downloader.hasInstallPermission()) {
                    downloader.install(file)
                } else {
                    updateState = UpdateUiState.InstallPermissionNeeded
                }
            }.onFailure { e ->
                if (e is CancellationException) {
                    updateState = UpdateUiState.Idle
                } else {
                    updateState = UpdateUiState.Error("下载失败：${e.message}")
                }
            }
        }
    }

    fun togglePause() {
        val current = updateState as? UpdateUiState.Downloading ?: return
        if (paused) {
            paused = false
            ticker.lastBytes = current.downloaded
            ticker.lastNanos = System.nanoTime()
            ticker.lastEmitNanos = 0
            ticker.speedBytesPerSec = 0
        } else {
            paused = true
        }
        updateState = current.copy(isPaused = !current.isPaused)
    }

    fun cancelDownload() {
        paused = true
        downloadJob?.cancel()
        updateState = UpdateUiState.Idle
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionTitle("网络设置")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SettingsRow(
                    title = "仅WiFi播放视频",
                    subtitle = "开启后，移动网络下不会播放视频",
                    leadingIcon = Icons.Outlined.Wifi,
                    trailing = {
                        Switch(
                            checked = wifiOnlyPlayback,
                            onCheckedChange = {
                                wifiOnlyPlayback = it
                                AppSettings.wifiOnlyPlayback = it
                            }
                        )
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp)
                )
                SettingsRow(
                    title = "仅WiFi下载视频",
                    subtitle = "开启后，移动网络下不会下载视频",
                    leadingIcon = Icons.Outlined.FileDownload,
                    trailing = {
                        Switch(
                            checked = wifiOnlyDownload,
                            onCheckedChange = {
                                wifiOnlyDownload = it
                                AppSettings.wifiOnlyDownload = it
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("关于")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SettingsRow(
                    title = "检查更新",
                    subtitle = "当前版本 v${UpdateChecker.CURRENT_VERSION}",
                    onClick = { startCheck() },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }

    when (val s = updateState) {
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
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("检查更新") },
            text = { Text("已是最新版本 v${UpdateChecker.CURRENT_VERSION}") },
            confirmButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("知道了") }
            }
        )

        is UpdateUiState.Available -> {
            val notes = s.info.releaseNotes.trim()
            Dialog(
                onDismissRequest = { updateState = UpdateUiState.Idle },
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
                            text = "发现新版本 ${s.info.tagName}",
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
                            TextButton(onClick = { updateState = UpdateUiState.Idle }) {
                                Text("稍后")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { startDownload(s.info) }) {
                                Text("立即更新")
                            }
                        }
                    }
                }
            }
        }

        is UpdateUiState.Downloading -> {
            val fraction = if (s.total > 0) {
                (s.downloaded.toFloat() / s.total.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val percent = if (s.total > 0) (s.downloaded * 100 / s.total).toInt() else 0
            val speedMbPerSec = s.speedBytesPerSec / 1024f / 1024f
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
                                text = if (s.total > 0) {
                                    "已下载 ${formatMb(s.downloaded)} / ${formatMb(s.total)}"
                                } else {
                                    "正在下载… ${formatMb(s.downloaded)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (s.total > 0) {
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
                                s.isPaused -> "已暂停"
                                s.speedBytesPerSec > 0 -> String.format("%.2f MB/s", speedMbPerSec)
                                else -> "0.00 MB/s"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { togglePause() }) {
                            Text(if (s.isPaused) "继续" else "暂停")
                        }
                        TextButton(onClick = { cancelDownload() }) {
                            Text("取消")
                        }
                    }
                }
            )
        }

        UpdateUiState.InstallPermissionNeeded -> AlertDialog(
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("需要安装权限") },
            text = { Text("请允许「安装未知应用」权限后再试，否则无法安装更新。") },
            confirmButton = {
                TextButton(onClick = {
                    updateState = UpdateUiState.Idle
                    downloader.openInstallPermissionSettings()
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("取消") }
            }
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("更新失败") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { startCheck() }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("返回") }
            }
        )
    }
}