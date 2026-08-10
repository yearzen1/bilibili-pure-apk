package com.bilibili.pure.ui.following

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.FollowingItem
import kotlinx.coroutines.delay

private fun fixPic(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
    else -> url
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingListScreen(
    onBack: () -> Unit = {},
    onUploaderClick: (mid: Long) -> Unit = {},
    viewModel: FollowingListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的关注") },
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
            uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有关注任何UP主", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                FollowingList(
                    items = uiState.items,
                    loadingMore = uiState.loadingMore,
                    hasMore = uiState.items.size < uiState.total,
                    onLoadMore = { viewModel.loadMore() },
                    onUploaderClick = onUploaderClick,
                    onUnfollow = { mid -> viewModel.unfollow(mid) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun FollowingList(
    items: List<FollowingItem>,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onUploaderClick: (mid: Long) -> Unit,
    onUnfollow: (mid: Long) -> Unit,
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.mid }) { item ->
            var showUnfollowConfirm by remember { mutableStateOf(false) }

            FollowingCard(
                item = item,
                onClick = { onUploaderClick(item.mid) },
                onUnfollow = { showUnfollowConfirm = true }
            )

            if (showUnfollowConfirm) {
                AlertDialog(
                    onDismissRequest = { showUnfollowConfirm = false },
                    title = { Text("取消关注") },
                    text = { Text("确定取消关注「${item.uname}」？") },
                    confirmButton = {
                        TextButton(onClick = {
                            onUnfollow(item.mid)
                            showUnfollowConfirm = false
                        }) {
                            Text("确定", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUnfollowConfirm = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }

        item(key = "bottom") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                    hasMore -> TextButton(onClick = onLoadMore) {
                        Text("加载更多", style = MaterialTheme.typography.bodySmall)
                    }
                    items.isNotEmpty() -> Text(
                        text = "共 ${items.size} 个关注",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowingCard(
    item: FollowingItem,
    onClick: () -> Unit,
    onUnfollow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = fixPic(item.face),
                contentDescription = item.uname,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    Text(
                        text = item.uname,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.sign.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SelectionContainer {
                        Text(
                            text = item.sign,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onUnfollow,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("已关注", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
