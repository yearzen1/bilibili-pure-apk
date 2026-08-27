package com.bilibili.pure.ui.channel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.SeasonArchiveItem
import com.bilibili.pure.data.model.SeasonSummary
import com.bilibili.pure.data.model.SpaceAccInfo
import com.bilibili.pure.data.model.UserVideoItem
import com.bilibili.pure.ui.search.formatCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    mid: Long,
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
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

    val inSeason = uiState.viewMode == ChannelViewMode.SEASON_DETAIL
    val inSearch = isSearching || uiState.viewMode == ChannelViewMode.SEARCH

    BackHandler(enabled = inSeason || inSearch) {
        when {
            inSearch -> {
                isSearching = false
                viewModel.clearSearch()
            }
            inSeason -> viewModel.backFromSeason()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            inSearch -> {
                                isSearching = false
                                viewModel.clearSearch()
                            }
                            inSeason -> viewModel.backFromSeason()
                            else -> onBack()
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
                            placeholder = { Text("搜索UP主视频/合集") },
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
                                        viewModel.search(searchQuery.trim())
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
                    } else if (inSeason) {
                        Text(uiState.seasonMeta?.name ?: "合集")
                    } else if (uiState.viewMode == ChannelViewMode.SEARCH) {
                        Text("搜索结果")
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
        ChannelBody(
            uiState = uiState,
            onVideoClick = onVideoClick,
            onSeasonClick = { seasonId, firstBvid -> viewModel.openSeason(seasonId, firstBvid) },
            onSwitchMode = { mode -> viewModel.switchMode(mode) },
            onLoadMore = { viewModel.loadMore() },
            onToggleFollow = { viewModel.toggleFollow() },
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
internal fun ChannelBody(
    uiState: ChannelUiState,
    onVideoClick: (bvid: String) -> Unit,
    onSeasonClick: (seasonId: Long, firstBvid: String?) -> Unit,
    onSwitchMode: (ChannelViewMode) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFollow: () -> Unit,
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

    LaunchedEffect(nearBottom, uiState.hasMore, uiState.loadingMore, uiState.viewMode) {
        if (uiState.viewMode == ChannelViewMode.VIDEOS && nearBottom && uiState.hasMore && !uiState.loadingMore) {
            delay(500)
            if (uiState.hasMore && !uiState.loadingMore) {
                onLoadMore()
            }
        }
    }

    val seasonsSelected = uiState.viewMode == ChannelViewMode.SEASONS ||
            uiState.viewMode == ChannelViewMode.SEASON_DETAIL

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.spaceAccInfo != null) {
            item(key = "up_info") {
                UpInfoCard(
                    spaceAccInfo = uiState.spaceAccInfo!!,
                    isLoggedIn = uiState.isLoggedIn,
                    isFollowed = uiState.isFollowed,
                    isTogglingFollow = uiState.isTogglingFollow,
                    onToggleFollow = onToggleFollow
                )
            }
        }

        item(key = "mode_tabs") {
            if (uiState.viewMode == ChannelViewMode.VIDEOS || uiState.viewMode == ChannelViewMode.SEASONS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !seasonsSelected,
                        onClick = { onSwitchMode(ChannelViewMode.VIDEOS) },
                        label = { Text("视频") }
                    )
                    FilterChip(
                        selected = seasonsSelected,
                        onClick = { onSwitchMode(ChannelViewMode.SEASONS) },
                        label = { Text("合集") }
                    )
                }
            }
        }

        when (uiState.viewMode) {
            ChannelViewMode.VIDEOS -> {
                if (uiState.videos.isNotEmpty()) {
                    item(key = "vheader") {
                        Text(
                            text = "共 ${uiState.videos.size} 个视频",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                items(uiState.videos, key = { it.bvid }) { video ->
                    ChannelVideoCard(video = video, onClick = { onVideoClick(video.bvid) })
                }
                item(key = "vbottom") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            uiState.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                            uiState.hasMore -> TextButton(onClick = onLoadMore) {
                                Text("加载更多", style = MaterialTheme.typography.bodySmall)
                            }
                            uiState.videos.isNotEmpty() -> Text(
                                text = "已全部加载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            ChannelViewMode.SEASONS -> {
                if (uiState.isLoadingSeasons) {
                    item(key = "sloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else if (uiState.seasonError != null) {
                    item(key = "serror") {
                        Text("合集加载失败：${uiState.seasonError}")
                    }
                } else if (uiState.seasons.isEmpty()) {
                    item(key = "sempty") {
                        Text("该UP主暂无合集", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    item(key = "sheader") {
                        Text(
                            text = "共 ${uiState.seasons.size} 个合集",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(uiState.seasons, key = { it.meta?.seasonId ?: it.hashCode() }) { season ->
                        SeasonCard(season = season, onClick = {
                            season.meta?.seasonId?.let { id ->
                                onSeasonClick(id, season.archives?.firstOrNull()?.bvid)
                            }
                        })
                    }
                }
            }

            ChannelViewMode.SEASON_DETAIL -> {
                if (uiState.isLoadingSeason) {
                    item(key = "sdloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else if (uiState.seasonError != null) {
                    item(key = "sderror") {
                        Text("加载失败：${uiState.seasonError}")
                    }
                } else if (uiState.seasonVideos.isEmpty()) {
                    item(key = "sdempty") {
                        Text("该合集暂无视频", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    item(key = "sdheader") {
                        Text(
                            text = "共 ${uiState.seasonMeta?.total ?: uiState.seasonVideos.size} 个视频",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(uiState.seasonVideos, key = { it.bvid }) { item ->
                        SeasonVideoRow(item = item, onClick = { onVideoClick(item.bvid) })
                    }
                }
            }

            ChannelViewMode.SEARCH -> {
                if (uiState.isLoading) {
                    item(key = "schloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else {
                    if (uiState.searchSeasons.isNotEmpty()) {
                        item(key = "sch_s_header") {
                            Text(
                                text = "合集 (${uiState.searchSeasons.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(uiState.searchSeasons, key = { "s_${it.meta?.seasonId ?: it.hashCode()}" }) { season ->
                            SeasonCard(season = season, onClick = {
                                season.meta?.seasonId?.let { id ->
                                    onSeasonClick(id, season.archives?.firstOrNull()?.bvid)
                                }
                            })
                        }
                    }
                    if (uiState.searchVideos.isNotEmpty()) {
                        item(key = "sch_v_header") {
                            Text(
                                text = "视频 (${uiState.searchVideos.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(uiState.searchVideos, key = { "v_${it.bvid}" }) { video ->
                            ChannelVideoCard(video = video, onClick = { onVideoClick(video.bvid) })
                        }
                    }
                    if (uiState.searchSeasons.isEmpty() && uiState.searchVideos.isEmpty()) {
                        item(key = "schempty") {
                            Text("未找到相关内容", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpInfoCard(
    spaceAccInfo: SpaceAccInfo,
    isLoggedIn: Boolean = false,
    isFollowed: Boolean = false,
    isTogglingFollow: Boolean = false,
    onToggleFollow: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (spaceAccInfo.face.isNotBlank()) {
                AsyncImage(
                    model = fixPic(spaceAccInfo.face),
                    contentDescription = spaceAccInfo.name,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = spaceAccInfo.name,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    Text(
                        text = spaceAccInfo.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (spaceAccInfo.sign.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SelectionContainer {
                        Text(
                            text = spaceAccInfo.sign,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (isLoggedIn) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onToggleFollow,
                    enabled = !isTogglingFollow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isFollowed) "已关注" else "关注",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun SeasonCard(season: SeasonSummary, onClick: () -> Unit) {
    val meta = season.meta
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = fixPic(meta?.cover ?: ""),
                contentDescription = meta?.name,
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta?.name ?: "合集",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共 ${meta?.total ?: 0} 个视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SeasonVideoRow(item: SeasonArchiveItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = fixPic(item.pic),
                contentDescription = item.title,
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${formatCount(item.stat?.view ?: 0)}播放",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(item.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%d:%02d".format(m, sec)
}
