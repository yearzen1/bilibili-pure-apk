package com.bilibili.pure.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.ui.search.formatCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.download.DownloadManager
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.model.QualityOption
import com.bilibili.pure.data.model.PlayUrlInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onUserClick: (mid: Long) -> Unit = {},
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var isLoadingQualities by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiState.videoInfo == null) {
            viewModel.load(bvid)
        }
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
        DetailContent(
            videoInfo = uiState.videoInfo,
            isLoading = uiState.isLoading,
            error = uiState.error,
            comments = uiState.comments,
            onPlay = {
                if (AppSettings.wifiOnlyPlayback && !AppSettings.isWifiConnected(context)) {
                    Toast.makeText(context, "当前为移动网络，仅WiFi下可播放", Toast.LENGTH_SHORT).show()
                } else {
                    onPlay(bvid)
                }
            },
            onUploaderClick = onUploaderClick,
            onUserClick = onUserClick,
            modifier = Modifier.padding(padding),
            replyThreads = uiState.replyThreads,
            expandedReplies = uiState.expandedReplies,
            onToggleReplies = { rpid -> uiState.videoInfo?.let { viewModel.toggleReplies(it.aid, rpid) } },
            loadingMore = uiState.loadingMore,
            hasMoreComments = uiState.hasMoreComments,
            onLoadMoreComments = { uiState.videoInfo?.let { viewModel.loadMoreComments(it.aid) } },
            isFavorited = uiState.isFavorited,
            favoriteCount = uiState.favoriteCount,
            isTogglingFavorite = uiState.isTogglingFavorite,
            onToggleFavorite = { uiState.videoInfo?.let { viewModel.toggleFavorite(it.aid) } },
            isLoggedIn = uiState.isLoggedIn,
            isFollowed = uiState.isFollowed,
            isTogglingFollow = uiState.isTogglingFollow,
            onFollowUploader = { mid -> viewModel.toggleFollowUploader(mid) },
            onDownload = {
                if (AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)) {
                    Toast.makeText(context, "当前为移动网络，仅WiFi下可下载", Toast.LENGTH_SHORT).show()
                    return@DetailContent
                }
                scope.launch {
                    isLoadingQualities = true
                    try {
                        val api = BilibiliApi.create()
                        val response = withContext(Dispatchers.IO) {
                            api.getPlayUrlDash(bvid = bvid, cid = uiState.videoInfo?.cid ?: 0)
                        }
                        if (response.code == 0) {
                            val info = response.data
                            val qualities = info?.accept_quality?.zip(info.accept_description ?: emptyList()) { q, desc ->
                                QualityOption(quality = q, description = desc)
                            } ?: emptyList()
                            availableQualities = qualities
                            showQualityDialog = qualities.isNotEmpty()
                        } else {
                            Toast.makeText(context, "获取画质信息失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "获取画质信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoadingQualities = false
                    }
                }
            }
        )
    }

    if (showQualityDialog && uiState.videoInfo != null) {
        val videoInfo = uiState.videoInfo!!
        QualitySelectionDialog(
            qualities = availableQualities,
            onSelect = { quality ->
                showQualityDialog = false
                scope.launch {
                    try {
                        val api = BilibiliApi.create()
                        val response = withContext(Dispatchers.IO) {
                            api.getPlayUrl(bvid = bvid, cid = videoInfo.cid, qn = quality.quality)
                        }
                        if (response.code == 0) {
                            val url = response.data?.durl?.firstOrNull()?.url
                            if (url != null) {
                                DownloadManager.getInstance(context).startDownload(
                                    bvid = bvid,
                                    cid = videoInfo.cid,
                                    title = videoInfo.title,
                                    cover = videoInfo.pic,
                                    quality = quality.quality,
                                    qualityDesc = quality.description,
                                    url = url
                                )
                                Toast.makeText(context, "开始下载: ${videoInfo.title}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "获取下载链接失败", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "获取下载链接失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}

@Composable
private fun QualitySelectionDialog(
    qualities: List<QualityOption>,
    onSelect: (QualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择画质") },
        text = {
            Column {
                qualities.forEach { quality ->
                    TextButton(
                        onClick = { onSelect(quality) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(quality.description, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DetailContent(
    videoInfo: VideoInfo?,
    isLoading: Boolean = false,
    error: String? = null,
    comments: List<com.bilibili.pure.data.model.CommentItem>?,
    onPlay: () -> Unit,
    onUploaderClick: (mid: Long) -> Unit = {},
    onUserClick: (mid: Long) -> Unit = {},
    modifier: Modifier = Modifier,
    replyThreads: Map<Long, ReplyThread> = emptyMap(),
    expandedReplies: Set<Long> = emptySet(),
    onToggleReplies: (rpid: Long) -> Unit = {},
    loadingMore: Boolean = false,
    hasMoreComments: Boolean = true,
    onLoadMoreComments: () -> Unit = {},
    isFavorited: Boolean = false,
    favoriteCount: Long = 0,
    isTogglingFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    isLoggedIn: Boolean = false,
    isFollowed: Boolean = false,
    isTogglingFollow: Boolean = false,
    onFollowUploader: (mid: Long) -> Unit = {},
    onDownload: () -> Unit = {}
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
            if (videoInfo != null) {
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
                        if (isLoggedIn) {
                            TextButton(
                                onClick = { onFollowUploader(videoInfo.owner.mid) },
                                enabled = !isTogglingFollow,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isFollowed) "已关注" else "关注",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Button(onClick = onPlay) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("播放")
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
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

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            enabled = !isTogglingFavorite
                        ) {
                            Icon(
                                imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isFavorited) "取消收藏" else "收藏",
                                tint = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatCount(favoriteCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "下载",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                onToggle = { onToggleReplies(rpid) },
                                onUserClick = onUserClick
                            )
                        }

                        if (isExpanded && thread != null) {
                            thread.items.forEach { reply ->
                                item(key = "r_$rpid:${reply.rpid}") {
                                    ReplyRow(reply = reply, onUserClick = onUserClick)
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
        }

        if (videoInfo == null) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载失败：$error")
                    }
                }
            }
        } else {
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
}

@Composable
private fun CommentCard(
    comment: com.bilibili.pure.data.model.CommentItem,
    thread: ReplyThread?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUserClick: (mid: Long) -> Unit = {}
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
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onUserClick(comment.member.mid) }
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
                    comment.content.pictures?.let { pictures ->
                        CommentPicturesRow(pictures = pictures)
                    }
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
private fun ReplyRow(reply: com.bilibili.pure.data.model.CommentItem, onUserClick: (mid: Long) -> Unit = {}) {
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onUserClick(reply.member.mid) }
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
            reply.content.pictures?.let { pictures ->
                CommentPicturesRow(pictures = pictures)
            }
        }
    }
}

@Composable
private fun CommentPicturesRow(pictures: List<com.bilibili.pure.data.model.CommentPicture>) {
    var showViewer by remember { mutableStateOf(false) }
    var initialIndex by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        pictures.forEachIndexed { index, picture ->
            val ratio = if (picture.imgWidth > 0 && picture.imgHeight > 0)
                picture.imgWidth.toFloat() / picture.imgHeight.toFloat() else 1f
            AsyncImage(
                model = fixPic(picture.imgSrc),
                contentDescription = null,
                modifier = Modifier
                    .height(80.dp)
                    .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        initialIndex = index
                        showViewer = true
                    },
                contentScale = ContentScale.Crop
            )
        }
    }

    if (showViewer) {
        CommentImageViewer(
            pictures = pictures,
            initialIndex = initialIndex,
            onClose = { showViewer = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentImageViewer(
    pictures: List<com.bilibili.pure.data.model.CommentPicture>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex,
            pageCount = { pictures.size }
        )

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = fixPic(pictures[page].imgSrc),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${pictures.size}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
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
