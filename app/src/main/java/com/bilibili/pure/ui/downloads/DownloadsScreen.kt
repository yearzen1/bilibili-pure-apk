package com.bilibili.pure.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.model.DownloadInfo

private fun fixPic(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
    else -> url
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (filePath: String) -> Unit,
    viewModel: DownloadsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current
    var confirmResume by remember { mutableStateOf<DownloadInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (downloads.any { it.status == DownloadInfo.STATUS_COMPLETED }) {
                        TextButton(onClick = { viewModel.clearCompleted() }) {
                            Text("清除已完成", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无下载",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在视频详情页点击下载按钮开始下载",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(
                        download = download,
                        onPlay = { onPlay(download.filePath) },
                        onDelete = { viewModel.deleteDownload(download) },
                        onPause = { viewModel.pauseDownload(download) },
                        onResume = {
                            if (AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)) {
                                confirmResume = download
                            } else {
                                viewModel.resumeDownload(download)
                            }
                        }
                    )
                }
            }
        }
    }

    confirmResume?.let { dl ->
        AlertDialog(
            onDismissRequest = { confirmResume = null },
            title = { Text("移动网络下载") },
            text = { Text("当前为移动网络，继续下载「${dl.title}」将消耗流量，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmResume = null
                    viewModel.resumeDownload(dl)
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResume = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DownloadItem(
    download: DownloadInfo,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = fixPic(download.cover),
                    contentDescription = download.title,
                    modifier = Modifier
                        .size(80.dp)
                        .clickable(onClick = onPlay),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    SelectionContainer {
                        Text(
                            text = download.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = download.qualityDesc,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if ((download.status == DownloadInfo.STATUS_DOWNLOADING || download.status == DownloadInfo.STATUS_PAUSED) && download.totalSize > 0) {
                        Text(
                            text = "${formatFileSize(download.fileSize)} / ${formatFileSize(download.totalSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (download.status == DownloadInfo.STATUS_DOWNLOADING && download.speed > 0) {
                            val remaining = remainingText(download)
                            Text(
                                text = if (remaining != null) {
                                    "${formatSpeed(download.speed)} · $remaining"
                                } else {
                                    formatSpeed(download.speed)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (download.status == DownloadInfo.STATUS_COMPLETED) {
                        Text(
                            text = formatFileSize(download.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = if (download.totalSize > 0) formatFileSize(download.totalSize) else "未知大小",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (download.status) {
                        DownloadInfo.STATUS_COMPLETED -> {
                            IconButton(onClick = onPlay) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "播放",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DownloadInfo.STATUS_DOWNLOADING -> {
                            IconButton(onClick = onPause) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "暂停",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DownloadInfo.STATUS_PAUSED, DownloadInfo.STATUS_FAILED -> {
                            IconButton(onClick = onResume) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "继续",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if ((download.status == DownloadInfo.STATUS_DOWNLOADING || download.status == DownloadInfo.STATUS_PAUSED) && download.totalSize > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (download.fileSize.toFloat() / download.totalSize).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (download.status == DownloadInfo.STATUS_FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "下载失败，点击重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除下载") },
            text = { Text("确定要删除「${download.title}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "未知大小"
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "%.1fKB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024))
        else -> "%.2fGB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "${bytesPerSecond}B/s"
        bytesPerSecond < 1024 * 1024 -> "%.1fKB/s".format(bytesPerSecond / 1024.0)
        else -> "%.1fMB/s".format(bytesPerSecond / (1024.0 * 1024))
    }
}

private fun remainingText(download: DownloadInfo): String? {
    if (download.speed <= 0 || download.totalSize <= download.fileSize) return null
    val seconds = (download.totalSize - download.fileSize) / download.speed
    return com.bilibili.pure.data.download.DownloadNotificationHelper.formatRemaining(seconds)
}
