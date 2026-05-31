package com.bilibili.pure.ui.channel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilibili.pure.data.model.UserVideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSearchScreen(
    mid: Long,
    keyword: String,
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    viewModel: ChannelSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(mid, keyword) {
        viewModel.search(mid, keyword)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索：$keyword") },
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
                    Text("搜索失败：${uiState.error}")
                }
            }
            uiState.videos.isEmpty() && !uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未找到相关视频", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                ChannelSearchContent(
                    videos = uiState.videos,
                    loadingMore = uiState.loadingMore,
                    hasMore = uiState.hasMore,
                    onLoadMore = { viewModel.loadMore() },
                    onVideoClick = onVideoClick,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ChannelSearchContent(
    videos: List<UserVideoItem>,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val nearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(nearBottom, hasMore, loadingMore) {
        if (nearBottom && hasMore && !loadingMore) {
            delay(500)
            if (hasMore && !loadingMore) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Text(
                text = "找到 ${videos.size} 个视频",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(videos, key = { it.bvid }) { video ->
            ChannelVideoCard(video = video, onClick = { onVideoClick(video.bvid) })
        }

        item(key = "bottom") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                    hasMore -> TextButton(onClick = onLoadMore) {
                        Text("加载更多", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Text(
                        text = "已全部加载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
