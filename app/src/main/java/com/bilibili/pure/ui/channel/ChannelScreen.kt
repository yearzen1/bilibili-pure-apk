package com.bilibili.pure.ui.channel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bilibili.pure.data.model.UserVideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    mid: Long,
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    onChannelSearch: (mid: Long, keyword: String) -> Unit,
    viewModel: ChannelViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(mid) {
        viewModel.load(mid)
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            searchQuery = ""
            delay(200)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            keyboardController?.hide()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSearching) "取消" else "返回"
                        )
                    }
                },
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("搜索UP主视频") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        isSearching = false
                                        keyboardController?.hide()
                                        onChannelSearch(mid, searchQuery.trim())
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("UP主视频")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
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
            else -> {
                ChannelContent(
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
internal fun ChannelContent(
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
        if (videos.isNotEmpty()) {
            item(key = "header") {
                Text(
                    text = "共 ${videos.size} 个视频",
                    style = MaterialTheme.typography.titleMedium
                )
            }
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
                    videos.isNotEmpty() -> Text(
                        text = "已全部加载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
