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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.core.SnapSpec
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import com.bilibili.pure.data.model.UgcSeason
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
import coil.request.ImageRequest
import coil.size.Size
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.data.model.VideoPage
import com.bilibili.pure.ui.common.DismissSelectionCard
import com.bilibili.pure.ui.common.DismissSelectionClickable
import com.bilibili.pure.ui.search.formatCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.download.DownloadManager
import com.bilibili.pure.data.local.AppSettings
import com.bilibili.pure.data.model.DownloadInfo
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

private fun fullResPic(url: String): String = fixPic(url).substringBefore("@")

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
    onCollectionVideoClick: (bvid: String) -> Unit = {},
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var isLoadingQualities by remember { mutableStateOf(false) }
    var confirmMobilePlay by remember { mutableStateOf(false) }
    var confirmMobileDownload by remember { mutableStateOf(false) }
    var showMultiPDialog by remember { mutableStateOf(false) }
    var multiPQualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var multiPSelectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var multiPSelectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isStartingBatchDownload by remember { mutableStateOf(false) }

    fun startDownloadFlow() {
        val dm = DownloadManager.getInstance(context)
        val videoInfo = uiState.videoInfo ?: return
        val pages = videoInfo.pages

        if (pages != null && pages.size > 1) {
            showMultiPDialog = true
            multiPSelectedPages = emptySet()
            multiPSelectedQuality = null
            scope.launch {
                try {
                    val api = BilibiliApi.create()
                    val response = withContext(Dispatchers.IO) {
                        api.getPlayUrlDash(bvid = bvid, cid = pages.first().cid)
                    }
                    if (response.code == 0) {
                        val info = response.data
                        val qualities = info?.accept_quality?.zip(info.accept_description ?: emptyList()) { q, desc ->
                            QualityOption(quality = q, description = desc)
                        } ?: emptyList()
                        multiPQualities = qualities
                        multiPSelectedQuality = qualities.firstOrNull()
                    } else {
                        Toast.makeText(context, "获取画质信息失败", Toast.LENGTH_SHORT).show()
                        showMultiPDialog = false
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "获取画质信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    showMultiPDialog = false
                }
            }
            return
        }

        val existing = dm.getDownload("${bvid}_${videoInfo.cid}")
        if (existing != null) {
            val msg = when (existing.status) {
                DownloadInfo.STATUS_COMPLETED -> "该视频已下载，可在“我的下载”中查看"
                DownloadInfo.STATUS_DOWNLOADING,
                DownloadInfo.STATUS_PENDING -> "正在下载中，请稍候"
                DownloadInfo.STATUS_PAUSED -> "下载已暂停，请到“我的下载”继续"
                DownloadInfo.STATUS_FAILED -> "上次下载失败，请到“我的下载”重试"
                else -> "该视频已在下载列表中"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isLoadingQualities = true
            try {
                val api = BilibiliApi.create()
                val response = withContext(Dispatchers.IO) {
                    api.getPlayUrlDash(bvid = bvid, cid = videoInfo.cid)
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
                    confirmMobilePlay = true
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
            pinnedComments = uiState.pinnedComments,
            commentSortMode = uiState.commentSortMode,
            loadingComments = uiState.loadingComments,
            onSortComments = { mode -> uiState.videoInfo?.let { viewModel.setCommentSort(it.aid, mode) } },
            togglingLikes = uiState.togglingLikes,
            onToggleCommentLike = { rpid -> uiState.videoInfo?.let { viewModel.toggleCommentLike(it.aid, rpid) } },
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
                    confirmMobileDownload = true
                } else {
                    startDownloadFlow()
                }
            },
            ugcSeason = uiState.ugcSeason,
            onCollectionVideoClick = onCollectionVideoClick
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
                                    url = url,
                                    overrideWifiOnly = true
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

    if (showMultiPDialog && uiState.videoInfo != null) {
        val videoInfo = uiState.videoInfo!!
        val pages = videoInfo.pages ?: emptyList()
        PageSelectionDialog(
            pages = pages,
            qualities = multiPQualities,
            selectedQuality = multiPSelectedQuality,
            selectedPages = multiPSelectedPages,
            onToggleAll = {
                multiPSelectedPages = if (multiPSelectedPages.size == pages.size) {
                    emptySet()
                } else {
                    pages.map { it.page }.toSet()
                }
            },
            onTogglePage = { pageNum ->
                multiPSelectedPages = if (pageNum in multiPSelectedPages) {
                    multiPSelectedPages - pageNum
                } else {
                    multiPSelectedPages + pageNum
                }
            },
            onSelectQuality = { quality ->
                multiPSelectedQuality = quality
            },
            onConfirm = {
                val quality = multiPSelectedQuality ?: return@PageSelectionDialog
                val selected = pages.filter { it.page in multiPSelectedPages }
                if (selected.isEmpty()) return@PageSelectionDialog
                showMultiPDialog = false
                isStartingBatchDownload = true
                scope.launch {
                    try {
                        DownloadManager.getInstance(context).startBatchDownload(
                            bvid = bvid,
                            videoTitle = videoInfo.title,
                            cover = videoInfo.pic,
                            pages = selected.map {
                                DownloadManager.BatchPageInfo(
                                    cid = it.cid,
                                    page = it.page,
                                    part = it.part
                                )
                            },
                            quality = quality.quality,
                            qualityDesc = quality.description
                        )
                        Toast.makeText(context, "开始下载 ${selected.size} 个分P", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isStartingBatchDownload = false
                    }
                }
            },
            onDismiss = { showMultiPDialog = false },
            isLoading = isStartingBatchDownload
        )
    }

    if (confirmMobilePlay) {
        AlertDialog(
            onDismissRequest = { confirmMobilePlay = false },
            title = { Text("移动网络播放") },
            text = { Text("当前为移动网络，播放将消耗流量，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMobilePlay = false
                    onPlay(bvid)
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMobilePlay = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (confirmMobileDownload) {
        AlertDialog(
            onDismissRequest = { confirmMobileDownload = false },
            title = { Text("移动网络下载") },
            text = { Text("当前为移动网络，下载将消耗流量，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMobileDownload = false
                    startDownloadFlow()
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMobileDownload = false }) {
                    Text("取消")
                }
            }
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
private fun PageSelectionDialog(
    pages: List<VideoPage>,
    qualities: List<QualityOption>,
    selectedQuality: QualityOption?,
    selectedPages: Set<Int>,
    onToggleAll: () -> Unit,
    onTogglePage: (Int) -> Unit,
    onSelectQuality: (QualityOption) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    val allSelected = selectedPages.size == pages.size
    var showQualityMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onToggleAll() }
                    )
                    Text("全选", style = MaterialTheme.typography.titleMedium)
                }
                Box {
                    TextButton(onClick = { showQualityMenu = true }) {
                        Text(
                            text = selectedQuality?.description ?: "画质",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        qualities.forEach { quality ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = quality.description,
                                        color = if (quality.quality == selectedQuality?.quality)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSelectQuality(quality)
                                    showQualityMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(pages.size) { index ->
                    val page = pages[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePage(page.page) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = page.page in selectedPages,
                            onCheckedChange = { onTogglePage(page.page) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "P${page.page}  ${page.part}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (page.duration > 0) {
                                Text(
                                    text = formatDuration(page.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedPages.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("下载 ${selectedPages.size} 个分P")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatDuration(seconds: Long): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
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
    pinnedComments: List<com.bilibili.pure.data.model.CommentItem> = emptyList(),
    commentSortMode: Int = 3,
    loadingComments: Boolean = false,
    onSortComments: (Int) -> Unit = {},
    togglingLikes: Set<Long> = emptySet(),
    onToggleCommentLike: (rpid: Long) -> Unit = {},
    isFavorited: Boolean = false,
    favoriteCount: Long = 0,
    isTogglingFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    isLoggedIn: Boolean = false,
    isFollowed: Boolean = false,
    isTogglingFollow: Boolean = false,
    onFollowUploader: (mid: Long) -> Unit = {},
    onDownload: () -> Unit = {},
    ugcSeason: UgcSeason? = null,
    onCollectionVideoClick: (bvid: String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var collectionExpanded by remember { mutableStateOf(false) }

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
                    SelectionContainer {
                        Text(
                            text = videoInfo.title,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DismissSelectionClickable(
                            modifier = Modifier.weight(1f),
                            onClick = { onUploaderClick(videoInfo.owner.mid) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            AsyncImage(
                                model = fixPic(videoInfo.owner.face),
                                contentDescription = videoInfo.owner.name,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SelectionContainer {
                                Text(
                                    text = videoInfo.owner.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            }
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
                        SelectionContainer {
                            Text(
                                text = videoInfo.desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { if (!expanded) isTruncated = it.hasVisualOverflow }
                            )
                        }
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
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val title = videoInfo.title
                            val url = "https://www.bilibili.com/video/${videoInfo.bvid}"
                            val shareText = "【$title】$url"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "分享到"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "未找到可分享的应用", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "分享",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (ugcSeason != null) {
                    item(key = "ugc_season") {
                        val episodes = ugcSeason.sections?.flatMap { it.episodes ?: emptyList() } ?: emptyList()
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { collectionExpanded = !collectionExpanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "合集：${ugcSeason.title}",
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (episodes.isNotEmpty()) {
                                            Text(
                                                text = "共 ${episodes.size} 集",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        if (collectionExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (collectionExpanded) "收起合集" else "展开合集"
                                    )
                                }
                                if (collectionExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (episodes.isEmpty()) {
                                            Text(
                                                text = "（暂无分集）",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            episodes.forEach { ep ->
                                                val isCurrent = ep.bvid == videoInfo.bvid
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable(enabled = !isCurrent) {
                                                            if (!isCurrent && ep.bvid.isNotBlank()) {
                                                                onCollectionVideoClick(ep.bvid)
                                                            }
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isCurrent) {
                                                        Icon(
                                                            Icons.Filled.PlayArrow,
                                                            contentDescription = "当前播放",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    } else {
                                                        Spacer(modifier = Modifier.size(20.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = ep.title.ifBlank { "P${ep.page?.page ?: 1}" },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "comments_header") {
                    Column {
                        Text(
                            text = "评论 (${videoInfo.stat.reply})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = commentSortMode == 3,
                                onClick = { onSortComments(3) },
                                label = { Text("热门") }
                            )
                            FilterChip(
                                selected = commentSortMode == 2,
                                onClick = { onSortComments(2) },
                                label = { Text("最新") }
                            )
                        }
                    }
                }

                if (loadingComments) {
                    item(key = "comments_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }

                if (pinnedComments.isNotEmpty()) {
                    pinnedComments.forEach { comment ->
                        val rpid = comment.rpid
                        val thread = replyThreads[rpid]
                        val isExpanded = rpid in expandedReplies
                        item(key = "pinned_$rpid") {
                            CommentCard(
                                comment = comment,
                                thread = thread,
                                isExpanded = isExpanded,
                                isPinned = true,
                                isLiked = comment.action == 1,
                                isTogglingLike = comment.rpid in togglingLikes,
                                onToggle = { onToggleReplies(rpid) },
                                onUserClick = onUserClick,
                                onToggleLike = {
                                    if (isLoggedIn) onToggleCommentLike(comment.rpid)
                                    else Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        if (isExpanded && thread != null) {
                            thread.items.forEach { reply ->
                                item(key = "pinned_r_$rpid:${reply.rpid}") {
                                    ReplyRow(
                                        reply = reply,
                                        onUserClick = onUserClick,
                                        isLiked = reply.action == 1,
                                        isTogglingLike = reply.rpid in togglingLikes,
                                        onToggleLike = {
                                            if (isLoggedIn) onToggleCommentLike(reply.rpid)
                                            else Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                            item(key = "pinned_collapse_$rpid") {
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
                                isPinned = false,
                                isLiked = comment.action == 1,
                                isTogglingLike = comment.rpid in togglingLikes,
                                onToggle = { onToggleReplies(rpid) },
                                onUserClick = onUserClick,
                                onToggleLike = {
                                    if (isLoggedIn) onToggleCommentLike(comment.rpid)
                                    else Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (isExpanded && thread != null) {
                            thread.items.forEach { reply ->
                                item(key = "r_$rpid:${reply.rpid}") {
                                    ReplyRow(
                                        reply = reply,
                                        onUserClick = onUserClick,
                                        isLiked = reply.action == 1,
                                        isTogglingLike = reply.rpid in togglingLikes,
                                        onToggleLike = {
                                            if (isLoggedIn) onToggleCommentLike(reply.rpid)
                                            else Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                                        }
                                    )
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
    isPinned: Boolean = false,
    isLiked: Boolean = false,
    isTogglingLike: Boolean = false,
    onToggle: () -> Unit,
    onUserClick: (mid: Long) -> Unit = {},
    onToggleLike: () -> Unit = {}
) {
    DismissSelectionCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (comment.rcount > 0) ({ onToggle() }) else null,
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
                        if (isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = "置顶",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTimestamp(comment.ctime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        CommentText(
                            message = comment.content.message,
                            emote = comment.content.emote,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    comment.content.pictures?.let { pictures ->
                        CommentPicturesRow(pictures = pictures)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (comment.rcount > 0) {
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
                        Spacer(modifier = Modifier.weight(1f))
                        CommentLikeButton(
                            liked = isLiked,
                            likeCount = comment.like,
                            enabled = !isTogglingLike,
                            onClick = onToggleLike
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: com.bilibili.pure.data.model.CommentItem,
    onUserClick: (mid: Long) -> Unit = {},
    isLiked: Boolean = false,
    isTogglingLike: Boolean = false,
    onToggleLike: () -> Unit = {}
) {
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
            SelectionContainer {
                CommentText(
                    message = reply.content.message,
                    emote = reply.content.emote,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            reply.content.pictures?.let { pictures ->
                CommentPicturesRow(pictures = pictures)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                CommentLikeButton(
                    liked = isLiked,
                    likeCount = reply.like,
                    compact = true,
                    enabled = !isTogglingLike,
                    onClick = onToggleLike
                )
            }
        }
    }
}

@Composable
private fun CommentLikeButton(
    liked: Boolean,
    likeCount: Int,
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (liked) "取消点赞" else "点赞",
            modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = formatCount(likeCount.toLong()),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var showActions by remember { mutableStateOf(false) }
        var currentScale by remember { mutableStateOf(1f) }
        val pagerState = rememberPagerState(
            initialPage = initialIndex,
            pageCount = { pictures.size }
        )

        val savePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                scope.launch {
                    CommentImageActions.saveToGallery(context, fullResPic(pictures[pagerState.currentPage].imgSrc))
                }
            } else {
                Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            currentScale = 1f
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.01f,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImage(
                    model = fullResPic(pictures[page].imgSrc),
                    contentDescription = null,
                    onTap = {
                        if (showActions) showActions = false else onClose()
                    },
                    onLongPress = { showActions = true },
                    onScaleChanged = { currentScale = it }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showActions) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        ViewerActionButton(
                            icon = Icons.Outlined.FileDownload,
                            label = "保存",
                            onClick = {
                                showActions = false
                                val url = fullResPic(pictures[pagerState.currentPage].imgSrc)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    scope.launch {
                                        CommentImageActions.saveToGallery(context, url)
                                    }
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                    scope.launch {
                                        CommentImageActions.saveToGallery(context, url)
                                    }
                                } else {
                                    savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                        )
                        ViewerActionButton(
                            icon = Icons.Outlined.ContentCopy,
                            label = "复制",
                            onClick = {
                                showActions = false
                                scope.launch {
                                    CommentImageActions.copyToClipboard(context, fullResPic(pictures[pagerState.currentPage].imgSrc))
                                }
                            }
                        )
                    }
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${pictures.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onScaleChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .size(4096)
            .build()
    }
    // telephoto:捏合缩放/双击切换/拖动/单击/长按全部内置,并自动把未缩放时的
    // 横滑让位给外层 HorizontalPager 翻页、放大后拖动归自己
    val zoomState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 3f)
    )

    LaunchedEffect(model) {
        zoomState.resetZoom(SnapSpec())
    }

    // zoomFraction:0=完全缩小(未放大),>0=已放大。用于驱动外层 Pager 的翻页开关
    LaunchedEffect(zoomState) {
        snapshotFlow { zoomState.zoomFraction }
            .collect { fraction -> onScaleChanged(fraction ?: 0f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zoomable(
                state = zoomState,
                onClick = { onTap() },
                onLongClick = { onLongPress() },
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Black.copy(alpha = 0.4f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
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
