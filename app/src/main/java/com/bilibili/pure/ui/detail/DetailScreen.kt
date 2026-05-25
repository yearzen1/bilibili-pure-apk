package com.bilibili.pure.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.ui.search.formatCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    bvid: String,
    onBack: () -> Unit,
    onPlay: (bvid: String) -> Unit,
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bvid) {
        viewModel.load(bvid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载失败：${uiState.error}")
                }
            }
            uiState.videoInfo != null -> {
                DetailContent(
                    videoInfo = uiState.videoInfo!!,
                    comments = uiState.comments,
                    onPlay = { onPlay(bvid) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    videoInfo: VideoInfo,
    comments: List<com.bilibili.pure.data.model.CommentItem>?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AsyncImage(
                model = videoInfo.pic,
                contentDescription = videoInfo.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
        }

        item {
            Text(
                text = videoInfo.title,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = videoInfo.owner.face,
                    contentDescription = videoInfo.owner.name,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = videoInfo.owner.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("播放")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("播放", formatCount(videoInfo.stat.view))
                StatChip("点赞", formatCount(videoInfo.stat.like))
                StatChip("弹幕", formatCount(videoInfo.stat.danmaku))
                StatChip("评论", formatCount(videoInfo.stat.reply))
            }
        }

        item {
            Text(
                text = videoInfo.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }

        item {
            Text(
                text = "评论 (${videoInfo.stat.reply})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (comments != null) {
            items(comments) { comment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = comment.member.avatar,
                            contentDescription = comment.member.uname,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = comment.member.uname,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comment.content.message,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (comment.rcount > 0) {
                                Text(
                                    text = "${comment.rcount} 条回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
