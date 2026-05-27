package com.bilibili.pure.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun fixPic(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
    else -> url
}

private fun formatTimestamp(unixSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixSeconds
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 604800 -> "${diff / 86400}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(unixSeconds * 1000))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    bvid: String,
    onBack: () -> Unit,
    onPlay: (bvid: String) -> Unit,
    onUploaderClick: (mid: Long) -> Unit = {},
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
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
                    onUploaderClick = onUploaderClick,
                    modifier = Modifier.padding(padding),
                    replyThreads = uiState.replyThreads,
                    expandedReplies = uiState.expandedReplies,
                    onToggleReplies = { rpid -> viewModel.toggleReplies(uiState.videoInfo!!.aid, rpid) },
                    loadingMore = uiState.loadingMore,
                    hasMoreComments = uiState.hasMoreComments,
                    onLoadMoreComments = { viewModel.loadMoreComments(uiState.videoInfo!!.aid) }
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
    onUploaderClick: (mid: Long) -> Unit = {},
    modifier: Modifier = Modifier,
    replyThreads: Map<Long, ReplyThread> = emptyMap(),
    expandedReplies: Set<Long> = emptySet(),
    onToggleReplies: (rpid: Long) -> Unit = {},
    loadingMore: Boolean = false,
    hasMoreComments: Boolean = true,
    onLoadMoreComments: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    val nearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(nearBottom, hasMoreComments, loadingMore, comments) {
        if (nearBottom && hasMoreComments && !loadingMore && comments != null) {
            delay(500)
            if (hasMoreComments && !loadingMore) {
                onLoadMoreComments()
            }
        }
    }

    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 6 }
    }
    val showScrollToComments by remember {
        derivedStateOf { listState.firstVisibleItemIndex < 4 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AsyncImage(
                    model = fixPic(videoInfo.pic),
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
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onUploaderClick(videoInfo.owner.mid) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = fixPic(videoInfo.owner.face),
                            contentDescription = videoInfo.owner.name,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = videoInfo.owner.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
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
                val dateStr = if (videoInfo.pubdate > 0L) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(videoInfo.pubdate * 1000))
                } else ""
                Text(
                    text = "发布日期：$dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                var isTruncated by remember { mutableStateOf(false) }

                Column {
                    Text(
                        text = videoInfo.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) isTruncated = it.hasVisualOverflow }
                    )
                    if (isTruncated) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起" else "展开全部")
                        }
                    }
                }
            }

            item(key = "comments_header") {
                Text(
                    text = "评论 (${videoInfo.stat.reply})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (comments != null) {
                comments.forEach { comment ->
                    val rpid = comment.rpid
                    val thread = replyThreads[rpid]
                    val isExpanded = rpid in expandedReplies

                    item(key = rpid) {
                        CommentCard(
                            comment = comment,
                            thread = thread,
                            isExpanded = isExpanded,
                            onToggle = { onToggleReplies(rpid) }
                        )
                    }

                    if (isExpanded && thread != null) {
                        thread.items.forEach { reply ->
                            item(key = "r_$rpid:${reply.rpid}") {
                                ReplyRow(reply = reply)
                            }
                        }
                        item(key = "collapse_$rpid") {
                            TextButton(
                                onClick = { onToggleReplies(rpid) },
                                modifier = Modifier.padding(start = 32.dp)
                            ) {
                                Text("收起回复")
                            }
                        }
                    }
                }
            }

            item(key = "bottom") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                        hasMoreComments -> TextButton(onClick = onLoadMoreComments) {
                            Text("加载更多评论", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (showScrollToTop) {
            FloatingActionButton(
                onClick = { scope.launch { listState.scrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        }

        if (showScrollToComments) {
            FloatingActionButton(
                onClick = { scope.launch { listState.scrollToItem(6) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "跳至评论区")
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: com.bilibili.pure.data.model.CommentItem,
    thread: ReplyThread?,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (comment.rcount > 0) Modifier.clickable { onToggle() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                AsyncImage(
                    model = fixPic(comment.member.avatar),
                    contentDescription = comment.member.uname,
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.member.uname,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTimestamp(comment.ctime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = comment.content.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (comment.rcount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                thread?.isLoading == true -> "加载中..."
                                thread?.hasMore == false && isExpanded -> "${comment.rcount} 条回复"
                                isExpanded -> "收起回复"
                                else -> "${comment.rcount} 条回复"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(reply: com.bilibili.pure.data.model.CommentItem) {
    Row(modifier = Modifier.padding(start = 32.dp)) {
        AsyncImage(
            model = fixPic(reply.member.avatar),
            contentDescription = reply.member.uname,
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.member.uname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatTimestamp(reply.ctime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = reply.content.message,
                style = MaterialTheme.typography.bodySmall
            )
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
