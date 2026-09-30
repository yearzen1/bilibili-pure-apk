package com.bilibili.pure.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.FavFolder
import com.bilibili.pure.data.model.FavResourceItem
import com.bilibili.pure.ui.common.AppBarSearchActions
import com.bilibili.pure.ui.common.AppBarSearchTitle
import com.bilibili.pure.ui.common.CreateFolderDialog
import com.bilibili.pure.ui.common.DismissSelectionCard
import com.bilibili.pure.ui.common.ScrollToTopFab
import com.bilibili.pure.ui.common.VideoCard
import com.bilibili.pure.ui.common.VideoCardSpec
import com.bilibili.pure.ui.common.rememberAppBarSearchState
import com.bilibili.pure.util.fixPic
import com.bilibili.pure.util.formatDuration
import kotlin.math.roundToInt

private val FavVideoSpec = VideoCardSpec(selectable = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    viewModel: FavoritesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderListState = rememberLazyListState()
    val resourceListState = rememberLazyListState()
    val searchState = rememberAppBarSearchState()
    val snackbarHostState = remember { SnackbarHostState() }
    var longPressFolder by remember { mutableStateOf<FavFolder?>(null) }

    val inFolder = uiState.selectedFolderId != null
    val searchActive = searchState.isSearching || uiState.searchKeyword != null

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    LaunchedEffect(uiState.searchKeyword, uiState.currentPage) {
        if (uiState.currentPage == 1) resourceListState.scrollToItem(0)
    }

    LaunchedEffect(uiState.opError) {
        uiState.opError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearOpError()
        }
    }

    fun handleBack() {
        when {
            searchActive -> {
                searchState.exit()
                viewModel.clearFavSearch()
            }
            inFolder -> viewModel.backToFolders()
            else -> onBack()
        }
    }

    BackHandler(inFolder || searchActive) {
        handleBack()
    }

    val title = if (inFolder) uiState.selectedFolderTitle else "我的收藏"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBarSearchTitle(
                        state = searchState,
                        normalTitle = title,
                        placeholder = "搜索收藏夹视频",
                        onSearch = { query ->
                            if (query.isEmpty()) {
                                viewModel.clearFavSearch()
                            } else {
                                viewModel.searchFav(query)
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!inFolder) {
                        IconButton(onClick = { viewModel.openCreateFolder() }) {
                            Icon(Icons.Default.Add, contentDescription = "新建收藏夹")
                        }
                    }
                    AppBarSearchActions(searchState)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            inFolder -> {
                ResourceListView(
                    resources = uiState.resources,
                    isLoading = uiState.isLoadingResources,
                    isLoadingMore = uiState.isLoadingMore,
                    hasMore = uiState.hasMore,
                    error = uiState.error,
                    listState = resourceListState,
                    emptyText = if (uiState.searchKeyword != null) "未找到相关视频" else "收藏夹为空",
                    onLoadMore = { viewModel.loadMore() },
                    onVideoClick = onVideoClick,
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                val displayFolders = filterFolders(uiState.folders, searchState.query)
                FolderListView(
                    folders = displayFolders,
                    isLoading = uiState.isLoadingFolders,
                    error = uiState.error,
                    covers = uiState.folderCovers,
                    listState = folderListState,
                    emptyText = if (uiState.folders.isNotEmpty() && displayFolders.isEmpty()) {
                        "未找到匹配的收藏夹"
                    } else {
                        "暂无收藏夹"
                    },
                    onFolderClick = {
                        searchState.exit()
                        viewModel.selectFolder(it)
                    },
                    onFolderLongClick = { longPressFolder = it },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (uiState.showCreateFolder) {
        CreateFolderDialog(
            creating = uiState.creatingFolder,
            onConfirm = { title, intro, privacy ->
                viewModel.createFavFolder(title, intro, privacy)
            },
            onDismiss = { viewModel.dismissCreateFolder() }
        )
    }

    longPressFolder?.let { folder ->
        FolderActionDialog(
            folder = folder,
            onEdit = {
                longPressFolder = null
                viewModel.startEditFolder(folder)
            },
            onDelete = {
                longPressFolder = null
                viewModel.openDeleteDialog(folder.id)
            },
            onDismiss = { longPressFolder = null }
        )
    }

    if (uiState.showEditFolder) {
        EditFolderDialog(
            title = uiState.editTitle,
            intro = uiState.editIntro,
            privacy = uiState.editPrivacy,
            saving = uiState.editingFolder,
            detailLoading = uiState.editDetailLoading,
            onTitleChange = { viewModel.updateEditTitle(it) },
            onIntroChange = { viewModel.updateEditIntro(it) },
            onPrivacyChange = { viewModel.updateEditPrivacy(it) },
            onConfirm = { viewModel.confirmEditFolder() },
            onDismiss = { viewModel.dismissEditFolder() }
        )
    }

    if (uiState.showDeleteDialog) {
        FolderDeleteDialog(
            folders = uiState.folders,
            selectedIds = uiState.deleteSelectedIds,
            deleting = uiState.deletingFolders,
            onToggle = { viewModel.toggleDeleteSelection(it) },
            onSelectAll = { viewModel.toggleSelectAllFolders() },
            onConfirm = { viewModel.confirmDeleteFolders() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }
}

@Composable
private fun FolderListView(
    folders: List<FavFolder>,
    isLoading: Boolean,
    error: String?,
    covers: Map<Long, String>,
    listState: LazyListState,
    emptyText: String = "暂无收藏夹",
    onFolderClick: (FavFolder) -> Unit,
    onFolderLongClick: (FavFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败：$error")
            }
        }
        folders.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText)
            }
        }
        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") {
                        Text(
                            text = "共 ${folders.size} 个收藏夹",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        FolderCard(
                            folder = folder,
                            coverUrl = covers[folder.id] ?: folder.cover,
                            onClick = { onFolderClick(folder) },
                            onLongClick = { onFolderLongClick(folder) }
                        )
                    }
                }
                ScrollToTopFab(listState = listState)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FavFolder,
    coverUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = fixPic(coverUrl),
                    contentDescription = folder.title,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${folder.mediaCount} 个内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val isPrivate = privacyFromAttr(folder.attr) == 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = privacyLabel(folder.attr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResourceListView(
    resources: List<FavResourceItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    listState: LazyListState,
    emptyText: String = "收藏夹为空",
    onLoadMore: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(nearBottom, hasMore, isLoadingMore) {
        if (nearBottom && hasMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    when {
        isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null && resources.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败：$error")
            }
        }
        resources.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText)
            }
        }
        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") {
                        Text(
                            text = "共 ${resources.size} 个视频",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(resources, key = { it.id }) { item ->
                        ResourceCard(item = item, onClick = {
                            if (item.bvid.isNotEmpty()) onVideoClick(item.bvid)
                        })
                    }
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    if (!hasMore && resources.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "已全部加载",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                ScrollToTopFab(listState = listState)
            }
        }
    }
}

@Composable
private fun ResourceCard(item: FavResourceItem, onClick: () -> Unit) {
    DismissSelectionCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        VideoCard(
            spec = FavVideoSpec,
            coverUrl = item.cover,
            title = item.title,
            author = item.upper.name,
            playCount = item.cntInfo?.play ?: 0,
            durationText = if (item.duration > 0) formatDuration(item.duration) else "",
            pubdate = item.pubtime
        )
    }
}

@Composable
private fun FolderActionDialog(
    folder: FavFolder,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("修改收藏夹", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除收藏夹", modifier = Modifier.fillMaxWidth())
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
private fun EditFolderDialog(
    title: String,
    intro: String,
    privacy: Int,
    saving: Boolean,
    detailLoading: Boolean,
    onTitleChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onPrivacyChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修改收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = intro,
                    onValueChange = onIntroChange,
                    label = { Text("简介（可选）") },
                    enabled = !saving && !detailLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("私密收藏夹", modifier = Modifier.weight(1f))
                    Switch(
                        checked = privacy == 1,
                        onCheckedChange = { onPrivacyChange(if (it) 1 else 0) },
                        enabled = !saving
                    )
                }
                when {
                    detailLoading -> Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    saving -> Text(
                        text = "保存中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = title.isNotBlank() && !saving && !detailLoading
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun FolderDeleteDialog(
    folders: List<FavFolder>,
    selectedIds: Set<Long>,
    deleting: Boolean,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val allSelected = folders.isNotEmpty() && selectedIds.containsAll(folders.map { it.id })

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("删除收藏夹") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已选 ${selectedIds.size}/${folders.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSelectAll, enabled = !deleting && folders.isNotEmpty()) {
                        Text(if (allSelected) "清空" else "全选")
                    }
                }
                Text(
                    text = "删除后视频将从该收藏夹移除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !deleting) { onToggle(folder.id) }
                        ) {
                            Checkbox(
                                checked = folder.id in selectedIds,
                                onCheckedChange = { onToggle(folder.id) },
                                enabled = !deleting
                            )
                            Text(
                                text = folder.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folder.mediaCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedIds.isNotEmpty() && !deleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(if (deleting) "删除中…" else "删除(${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text("取消")
            }
        }
    )
}
