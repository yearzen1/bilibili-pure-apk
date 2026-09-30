package com.bilibili.pure.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.fav.extractFolderCovers
import com.bilibili.pure.data.model.CommentCursor
import com.bilibili.pure.data.model.CommentItem
import com.bilibili.pure.data.model.FavFolder
import com.bilibili.pure.data.model.SeasonArchiveItem
import com.bilibili.pure.data.model.SeasonMeta
import com.bilibili.pure.data.model.UgcSeason
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.data.repository.BilibiliRepository
import com.bilibili.pure.util.decodeHtmlEntities
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun appendUniqueComments(
    existing: List<CommentItem>,
    incoming: List<CommentItem>
): List<CommentItem> = (existing + incoming).distinctBy { it.rpid }

internal data class CommentPageState(
    val nextCursor: Int,
    val hasMore: Boolean
)

internal fun resolveCommentPage(
    cursor: CommentCursor?,
    requestedCursor: Int
): CommentPageState {
    val nextCursor = cursor?.next ?: 0
    return CommentPageState(
        nextCursor = nextCursor,
        hasMore = cursor != null && !cursor.isEnd && nextCursor > requestedCursor
    )
}

internal fun isCurrentCommentRequest(
    requestGeneration: Long,
    currentGeneration: Long
): Boolean = requestGeneration == currentGeneration

internal data class FavDealParams(
    val addMediaIds: String,
    val delMediaIds: String
)

internal fun parseDedeUserId(cookies: String): Long? = cookies
    .split(";")
    .firstOrNull { it.trim().startsWith("DedeUserID=") }
    ?.substringAfter("DedeUserID=")
    ?.trim()
    ?.toLongOrNull()

internal fun pickDefaultFolderId(folders: List<FavFolder>): Long? {
    if (folders.isEmpty()) return null
    val default = folders.firstOrNull { (it.attr and 0b10) == 0 }
    return (default ?: folders.first()).id
}

internal fun favDealParams(favor: Boolean, folderIds: List<Long>): FavDealParams? {
    if (folderIds.isEmpty()) return null
    val ids = folderIds.joinToString(",")
    return if (favor) {
        FavDealParams(addMediaIds = ids, delMediaIds = "")
    } else {
        FavDealParams(addMediaIds = "", delMediaIds = ids)
    }
}

internal fun favouredFolderIds(folders: List<FavFolder>): List<Long> =
    folders.filter { it.favState != 0 }.map { it.id }

data class ReplyThread(
    val items: List<CommentItem> = emptyList(),
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false
)

data class DetailUiState(
    val videoInfo: VideoInfo? = null,
    val comments: List<CommentItem>? = null,
    val pinnedComments: List<CommentItem> = emptyList(),
    val commentSortMode: Int = 3,
    val loadingComments: Boolean = false,
    val togglingLikes: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val replyThreads: Map<Long, ReplyThread> = emptyMap(),
    val expandedReplies: Set<Long> = emptySet(),
    val nextCursor: Int = 0,
    val hasMoreComments: Boolean = true,
    val loadingMore: Boolean = false,
    val isFavorited: Boolean = false,
    val favoriteCount: Long = 0,
    val isTogglingFavorite: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isFollowed: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val favPickerVisible: Boolean = false,
    val favPickerLoading: Boolean = false,
    val favFolders: List<FavFolder> = emptyList(),
    val selectedFolderId: Long? = null,
    val showCreateFolder: Boolean = false,
    val creatingFolder: Boolean = false,
    val favError: String? = null,
    val folderCovers: Map<Long, String> = emptyMap(),
    val ugcSeason: UgcSeason? = null,
    // Collection pagination state
    val collectionEpisodes: List<SeasonArchiveItem> = emptyList(),
    val collectionMeta: SeasonMeta? = null,
    val collectionPage: Int = 1,
    val collectionHasMore: Boolean = true,
    val collectionLoadingMore: Boolean = false,
    val collectionSortDesc: Boolean = true
)

class DetailViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private var commentsRequestGeneration = 0L

    private fun startCommentRequest(): Long {
        commentsRequestGeneration += 1
        return commentsRequestGeneration
    }

    fun load(bvid: String) {
        Log.d(BilibiliApp.TAG, "load detail: bvid=$bvid")
        val isLoggedIn = BilibiliApi.loginCookies.isNotEmpty()
        _uiState.value = DetailUiState(isLoading = true, isLoggedIn = isLoggedIn)
        val commentsGeneration = startCommentRequest()

        viewModelScope.launch {
            repository.getVideoInfo(bvid)
                .onSuccess { info ->
                    Log.d(BilibiliApp.TAG, "detail loaded: title=${info.title} aid=${info.aid}")
                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        isLoading = false,
                        favoriteCount = info.stat.favorite,
                        ugcSeason = info.ugcSeason
                    )
                    loadComments(info.aid, _uiState.value.commentSortMode, commentsGeneration)
                    checkFavoured(info.aid)
                    if (isLoggedIn) {
                        checkFollowStatus(info.owner.mid)
                    }
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "load detail failed: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
        }
    }

    private suspend fun checkFavoured(aid: Long) {
        if (BilibiliApi.loginCookies.isEmpty()) return
        repository.checkFavoured(aid)
            .onSuccess { favoured ->
                _uiState.value = _uiState.value.copy(isFavorited = favoured)
            }
            .onFailure { Log.e(BilibiliApp.TAG, "checkFavoured failed", it) }
    }

    private suspend fun checkFollowStatus(mid: Long) {
        withContext(NonCancellable) {
            repository.checkRelation(mid)
        }
            .onSuccess { followed ->
                _uiState.value = _uiState.value.copy(isFollowed = followed)
            }
            .onFailure { e ->
                Log.e(BilibiliApp.TAG, "checkRelation failed", e)
            }
    }

    fun toggleFollowUploader(mid: Long) {
        val state = _uiState.value
        if (state.isTogglingFollow) return
        _uiState.value = state.copy(isTogglingFollow = true)
        viewModelScope.launch {
            val act = if (state.isFollowed) 2 else 1
            repository.modifyRelation(fid = mid, act = act)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isFollowed = !state.isFollowed,
                        isTogglingFollow = false
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "toggleFollow failed", e)
                    _uiState.value = _uiState.value.copy(isTogglingFollow = false)
                }
        }
    }

    fun onFavoriteClick(aid: Long) {
        val state = _uiState.value
        if (state.isTogglingFavorite) return
        if (!state.isLoggedIn) return
        if (state.isFavorited) {
            cancelFavorite(aid)
        } else {
            openFavPicker(aid)
        }
    }

    private fun favUid(): Long? = parseDedeUserId(BilibiliApi.loginCookies)

    private fun openFavPicker(aid: Long) {
        val uid = favUid() ?: return
        val state = _uiState.value
        if (state.favPickerVisible) return
        _uiState.value = state.copy(
            favPickerVisible = true,
            favPickerLoading = true,
            isTogglingFavorite = true
        )
        viewModelScope.launch {
            repository.getFavFolders(uid, rid = aid)
                .onSuccess { folders ->
                    _uiState.value = _uiState.value.copy(
                        favPickerLoading = false,
                        favFolders = folders,
                        folderCovers = extractFolderCovers(folders),
                        selectedFolderId = pickDefaultFolderId(folders),
                        isTogglingFavorite = false
                    )
                    Log.d(BilibiliApp.TAG, "favPicker loaded: ${folders.size} folders")
                }
                .onFailure {
                    Log.e(BilibiliApp.TAG, "openFavPicker failed", it)
                    _uiState.value = _uiState.value.copy(
                        favPickerVisible = false,
                        favPickerLoading = false,
                        isTogglingFavorite = false,
                        favError = it.message ?: "加载收藏夹失败"
                    )
                }
        }
    }

    fun clearFavError() {
        _uiState.value = _uiState.value.copy(favError = null)
    }

    fun dismissFavPicker() {
        _uiState.value = _uiState.value.copy(
            favPickerVisible = false,
            showCreateFolder = false
        )
    }

    fun selectFavFolder(id: Long) {
        _uiState.value = _uiState.value.copy(selectedFolderId = id)
    }

    fun confirmFavorite(aid: Long) {
        val state = _uiState.value
        if (state.isTogglingFavorite) return
        val folderId = state.selectedFolderId ?: return
        val params = favDealParams(favor = true, folderIds = listOf(folderId)) ?: return
        _uiState.value = state.copy(isTogglingFavorite = true)
        viewModelScope.launch {
            repository.dealFavResource(
                rid = aid,
                addMediaIds = params.addMediaIds,
                delMediaIds = params.delMediaIds
            )
                .onSuccess {
                    val s = _uiState.value
                    _uiState.value = s.copy(
                        isFavorited = true,
                        favoriteCount = s.favoriteCount + 1,
                        isTogglingFavorite = false,
                        favPickerVisible = false,
                        showCreateFolder = false
                    )
                    Log.d(BilibiliApp.TAG, "favorite added: folderId=$folderId")
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "confirmFavorite failed", e)
                    _uiState.value = _uiState.value.copy(
                        isTogglingFavorite = false,
                        favError = e.message ?: "收藏失败"
                    )
                }
        }
    }

    private fun cancelFavorite(aid: Long) {
        val uid = favUid() ?: return
        _uiState.value = _uiState.value.copy(isTogglingFavorite = true)
        viewModelScope.launch {
            repository.getFavFolders(uid, rid = aid)
                .onSuccess { folders ->
                    var ids = favouredFolderIds(folders)
                    if (ids.isEmpty()) {
                        ids = listOfNotNull(pickDefaultFolderId(folders))
                    }
                    val params = favDealParams(favor = false, folderIds = ids)
                    if (params == null) {
                        _uiState.value = _uiState.value.copy(isTogglingFavorite = false)
                        return@onSuccess
                    }
                    repository.dealFavResource(
                        rid = aid,
                        addMediaIds = params.addMediaIds,
                        delMediaIds = params.delMediaIds
                    )
                        .onSuccess {
                            val s = _uiState.value
                            _uiState.value = s.copy(
                                isFavorited = false,
                                favoriteCount = maxOf(0L, s.favoriteCount - 1),
                                isTogglingFavorite = false
                            )
                            Log.d(BilibiliApp.TAG, "favorite removed: folderIds=${params.delMediaIds}")
                        }
                        .onFailure { e ->
                            Log.e(BilibiliApp.TAG, "cancelFavorite failed", e)
                            _uiState.value = _uiState.value.copy(
                                isTogglingFavorite = false,
                                favError = e.message ?: "取消收藏失败"
                            )
                        }
                }
                .onFailure {
                    Log.e(BilibiliApp.TAG, "cancelFavorite load folders failed", it)
                    _uiState.value = _uiState.value.copy(
                        isTogglingFavorite = false,
                        favError = it.message ?: "取消收藏失败"
                    )
                }
        }
    }

    fun openCreateFolder() {
        _uiState.value = _uiState.value.copy(showCreateFolder = true)
    }

    fun dismissCreateFolder() {
        _uiState.value = _uiState.value.copy(showCreateFolder = false)
    }

    fun createFavFolder(aid: Long, title: String, intro: String, privacy: Int) {
        val state = _uiState.value
        if (state.creatingFolder) return
        if (title.isBlank()) return
        _uiState.value = state.copy(creatingFolder = true)
        viewModelScope.launch {
            repository.addFavFolder(title = title.trim(), intro = intro, privacy = privacy)
                .onSuccess { newId ->
                    val s = _uiState.value
                    val newFolders = s.favFolders + FavFolder(
                        id = newId,
                        title = title.trim(),
                        mediaCount = 0
                    )
                    _uiState.value = s.copy(
                        creatingFolder = false,
                        showCreateFolder = false,
                        favFolders = newFolders,
                        folderCovers = extractFolderCovers(newFolders),
                        selectedFolderId = newId
                    )
                    Log.d(BilibiliApp.TAG, "favFolder created: id=$newId title=$title")
                    confirmFavorite(aid)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "createFavFolder failed", e)
                    _uiState.value = _uiState.value.copy(
                        creatingFolder = false,
                        favError = e.message ?: "创建收藏夹失败"
                    )
                }
        }
    }

    private suspend fun loadComments(aid: Long, mode: Int, requestGeneration: Long) {
        if (!isCurrentCommentRequest(requestGeneration, commentsRequestGeneration)) return
        Log.d(BilibiliApp.TAG, "load comments: aid=$aid mode=$mode")
        _uiState.value = _uiState.value.copy(loadingComments = true)
        repository.getComments(aid, mode = mode)
            .onSuccess { commentList ->
                if (!isCurrentCommentRequest(requestGeneration, commentsRequestGeneration)) return@onSuccess
                val pinned = appendUniqueComments(
                    emptyList(),
                    (commentList.topReplies ?: emptyList()).map { c ->
                        c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                    }
                )
                val pinnedRpids = pinned.map { it.rpid }.toSet()
                val replies = appendUniqueComments(
                    emptyList(),
                    (commentList.replies ?: emptyList())
                        .filter { it.rpid !in pinnedRpids }
                        .map { c ->
                            c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                        }
                )
                val cursor = commentList.cursor
                val pageState = resolveCommentPage(cursor, requestedCursor = 0)
                Log.d(BilibiliApp.TAG, "comments loaded: ${replies.size} comments, ${pinned.size} pinned, cursor=${cursor}")
                _uiState.value = _uiState.value.copy(
                    comments = replies,
                    pinnedComments = pinned,
                    nextCursor = pageState.nextCursor,
                    hasMoreComments = pageState.hasMore,
                    loadingComments = false
                )
            }
            .onFailure {
                if (!isCurrentCommentRequest(requestGeneration, commentsRequestGeneration)) return@onFailure
                Log.e(BilibiliApp.TAG, "load comments failed", it)
                _uiState.value = _uiState.value.copy(loadingComments = false)
            }
    }

    fun setCommentSort(aid: Long, mode: Int) {
        val state = _uiState.value
        if (mode == state.commentSortMode) return
        val requestGeneration = startCommentRequest()
        _uiState.value = state.copy(
            commentSortMode = mode,
            comments = null,
            pinnedComments = emptyList(),
            nextCursor = 0,
            hasMoreComments = true,
            loadingMore = false,
            loadingComments = true,
            expandedReplies = emptySet(),
            replyThreads = emptyMap(),
            togglingLikes = emptySet()
        )
        viewModelScope.launch {
            loadComments(aid, mode, requestGeneration)
        }
    }

    fun toggleCommentLike(aid: Long, rpid: Long) {
        val state = _uiState.value
        if (rpid in state.togglingLikes) return
        if (!state.isLoggedIn) return
        val original = findComment(rpid) ?: return
        val liked = original.action != 1
        _uiState.value = applyLike(state, rpid, liked)
        viewModelScope.launch {
            repository.likeComment(aid, rpid, liked)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        togglingLikes = _uiState.value.togglingLikes - rpid
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "toggleCommentLike failed", e)
                    _uiState.value = revertLike(_uiState.value, rpid, original)
                }
        }
    }

    private fun findComment(rpid: Long): CommentItem? {
        val state = _uiState.value
        state.pinnedComments.forEach { if (it.rpid == rpid) return it }
        state.comments?.forEach { if (it.rpid == rpid) return it }
        state.replyThreads.values.forEach { t ->
            t.items.forEach { if (it.rpid == rpid) return it }
        }
        return null
    }

    private fun updateCommentLike(items: List<CommentItem>, rpid: Long, liked: Boolean): List<CommentItem> =
        items.map {
            if (it.rpid == rpid) {
                it.copy(
                    like = maxOf(0, it.like + (if (liked) 1 else -1)),
                    action = if (liked) 1 else 0
                )
            } else it
        }

    private fun restoreCommentLike(items: List<CommentItem>, original: CommentItem): List<CommentItem> =
        items.map { if (it.rpid == original.rpid) original else it }

    private fun applyLike(state: DetailUiState, rpid: Long, liked: Boolean): DetailUiState {
        return state.copy(
            togglingLikes = state.togglingLikes + rpid,
            pinnedComments = updateCommentLike(state.pinnedComments, rpid, liked),
            comments = state.comments?.let { updateCommentLike(it, rpid, liked) },
            replyThreads = state.replyThreads.mapValues { (_, t) ->
                t.copy(items = updateCommentLike(t.items, rpid, liked))
            }
        )
    }

    private fun revertLike(state: DetailUiState, rpid: Long, original: CommentItem): DetailUiState {
        return state.copy(
            togglingLikes = state.togglingLikes - rpid,
            pinnedComments = restoreCommentLike(state.pinnedComments, original),
            comments = state.comments?.let { restoreCommentLike(it, original) },
            replyThreads = state.replyThreads.mapValues { (_, t) ->
                t.copy(items = restoreCommentLike(t.items, original))
            }
        )
    }

    fun loadMoreComments(aid: Long) {
        val state = _uiState.value
        if (state.loadingMore || !state.hasMoreComments) return
        val requestedCursor = state.nextCursor
        val requestGeneration = commentsRequestGeneration
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.getComments(aid, page = requestedCursor, mode = state.commentSortMode)
                .onSuccess { commentList ->
                    if (!isCurrentCommentRequest(requestGeneration, commentsRequestGeneration)) return@onSuccess
                    val pinnedRpids = _uiState.value.pinnedComments.map { it.rpid }.toSet()
                    val newReplies = (commentList.replies ?: emptyList())
                        .filter { it.rpid !in pinnedRpids }
                        .map { c ->
                            c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                        }
                    val cursor = commentList.cursor
                    val pageState = resolveCommentPage(cursor, requestedCursor)
                    Log.d(BilibiliApp.TAG, "more comments loaded: ${newReplies.size} comments, cursor=${cursor}")
                    _uiState.value = _uiState.value.copy(
                        comments = appendUniqueComments(_uiState.value.comments ?: emptyList(), newReplies),
                        nextCursor = pageState.nextCursor,
                        hasMoreComments = pageState.hasMore,
                        loadingMore = false
                    )
                }
                .onFailure {
                    if (!isCurrentCommentRequest(requestGeneration, commentsRequestGeneration)) return@onFailure
                    Log.e(BilibiliApp.TAG, "load more comments failed", it)
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
        }
    }

    fun toggleReplies(aid: Long, rpid: Long) {
        val state = _uiState.value
        if (rpid in state.expandedReplies) {
            _uiState.value = state.copy(expandedReplies = state.expandedReplies - rpid)
        } else {
            if (state.replyThreads.containsKey(rpid)) {
                _uiState.value = state.copy(expandedReplies = state.expandedReplies + rpid)
            } else {
                loadReplies(aid, rpid)
            }
        }
    }

    private fun loadReplies(aid: Long, rpid: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                replyThreads = _uiState.value.replyThreads + (rpid to ReplyThread(isLoading = true))
            )
            repository.getReplies(aid, rpid, page = 1)
                .onSuccess { commentList ->
                    val items = appendUniqueComments(
                        emptyList(),
                        (commentList.replies ?: emptyList()).map { c ->
                            c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                        }
                    )
                    _uiState.value = _uiState.value.copy(
                        replyThreads = _uiState.value.replyThreads + (rpid to ReplyThread(
                            items = items,
                            currentPage = 1,
                            hasMore = false
                        )),
                        expandedReplies = _uiState.value.expandedReplies + rpid
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        replyThreads = _uiState.value.replyThreads - rpid
                    )
                }
        }
    }

    fun loadMoreReplies(aid: Long, rpid: Long) {
        val thread = _uiState.value.replyThreads[rpid] ?: return
        if (thread.isLoading || !thread.hasMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                replyThreads = _uiState.value.replyThreads + (rpid to thread.copy(isLoading = true))
            )
            val nextPage = thread.currentPage + 1
            repository.getReplies(aid, rpid, page = nextPage)
                .onSuccess { commentList ->
                    val newItems = (commentList.replies ?: emptyList()).map { c ->
                        c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                    }
                    val cursor = commentList.cursor
                    val updated = thread.copy(
                        items = appendUniqueComments(thread.items, newItems),
                        currentPage = nextPage,
                        hasMore = cursor?.isEnd != true,
                        isLoading = false
                    )
                    _uiState.value = _uiState.value.copy(
                        replyThreads = _uiState.value.replyThreads + (rpid to updated)
                    )
                }
                .onFailure {
                    val failed = thread.copy(isLoading = false)
                    _uiState.value = _uiState.value.copy(
                        replyThreads = _uiState.value.replyThreads + (rpid to failed)
                    )
                }
        }
    }

    // --- Collection pagination ---

    fun loadCollectionEpisodes(mid: Long, seasonId: Long, sortDesc: Boolean = true) {
        val state = _uiState.value
        if (state.collectionEpisodes.isNotEmpty() && state.collectionSortDesc == sortDesc) return
        _uiState.value = state.copy(
            collectionEpisodes = emptyList(),
            collectionPage = 1,
            collectionHasMore = true,
            collectionLoadingMore = true,
            collectionSortDesc = sortDesc
        )
        viewModelScope.launch {
            repository.getSeasonArchives(mid = mid, seasonId = seasonId, pageNum = 1, sortReverse = sortDesc)
                .onSuccess { data ->
                    val archives = data.archives ?: emptyList()
                    val meta = data.meta
                    _uiState.value = _uiState.value.copy(
                        collectionEpisodes = archives,
                        collectionMeta = meta,
                        collectionPage = 1,
                        collectionHasMore = archives.size >= 20,
                        collectionLoadingMore = false
                    )
                    Log.d(BilibiliApp.TAG, "collection loaded: ${archives.size} episodes, meta=${meta?.name}")
                }
                .onFailure {
                    Log.e(BilibiliApp.TAG, "loadCollectionEpisodes failed", it)
                    _uiState.value = _uiState.value.copy(collectionLoadingMore = false)
                }
        }
    }

    fun loadMoreCollectionEpisodes(mid: Long, seasonId: Long) {
        val state = _uiState.value
        if (state.collectionLoadingMore || !state.collectionHasMore) return
        val nextPage = state.collectionPage + 1
        _uiState.value = state.copy(collectionLoadingMore = true)
        viewModelScope.launch {
            repository.getSeasonArchives(mid = mid, seasonId = seasonId, pageNum = nextPage, sortReverse = state.collectionSortDesc)
                .onSuccess { data ->
                    val archives = data.archives ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        collectionEpisodes = _uiState.value.collectionEpisodes + archives,
                        collectionPage = nextPage,
                        collectionHasMore = archives.size >= 20,
                        collectionLoadingMore = false
                    )
                    Log.d(BilibiliApp.TAG, "more collection loaded: +${archives.size} episodes, page=$nextPage")
                }
                .onFailure {
                    Log.e(BilibiliApp.TAG, "loadMoreCollectionEpisodes failed", it)
                    _uiState.value = _uiState.value.copy(collectionLoadingMore = false)
                }
        }
    }

    fun toggleCollectionSort(mid: Long, seasonId: Long) {
        val newSortDesc = !_uiState.value.collectionSortDesc
        loadCollectionEpisodes(mid, seasonId, sortDesc = newSortDesc)
    }
}
