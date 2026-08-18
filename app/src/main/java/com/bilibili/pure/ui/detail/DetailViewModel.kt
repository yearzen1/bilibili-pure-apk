package com.bilibili.pure.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.CommentItem
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.data.repository.BilibiliRepository
import com.bilibili.pure.util.decodeHtmlEntities
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val defaultFolderId: Long? = null
) {
    fun getDefaultFolderId() = defaultFolderId
    fun withDefaultFolderId(id: Long) = copy(defaultFolderId = id)
}

class DetailViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(bvid: String) {
        Log.d(BilibiliApp.TAG, "load detail: bvid=$bvid")
        val isLoggedIn = BilibiliApi.loginCookies.isNotEmpty()
        _uiState.value = DetailUiState(isLoading = true, isLoggedIn = isLoggedIn)

        viewModelScope.launch {
            repository.getVideoInfo(bvid)
                .onSuccess { info ->
                    Log.d(BilibiliApp.TAG, "detail loaded: title=${info.title} aid=${info.aid}")
                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        isLoading = false,
                        favoriteCount = info.stat.favorite
                    )
                    loadComments(info.aid)
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

    fun toggleFavorite(aid: Long) {
        val state = _uiState.value
        if (state.isTogglingFavorite) return
        _uiState.value = state.copy(isTogglingFavorite = true)

        viewModelScope.launch {
            var folderId = state.getDefaultFolderId()

            if (folderId == null) {
                val uid = BilibiliApi.loginCookies
                    .split(";").firstOrNull { it.trim().startsWith("DedeUserID=") }
                    ?.substringAfter("DedeUserID=")?.trim()?.toLongOrNull()
                if (uid == null) {
                    _uiState.value = _uiState.value.copy(isTogglingFavorite = false)
                    return@launch
                }
                repository.getFavFolders(uid)
                    .onSuccess { folders ->
                        val id = folders.firstOrNull()?.id
                        if (id != null) {
                            _uiState.value = _uiState.value.withDefaultFolderId(id)
                            doToggleFavorite(aid, id)
                        } else {
                            _uiState.value = _uiState.value.copy(isTogglingFavorite = false)
                        }
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(isTogglingFavorite = false)
                    }
            } else {
                doToggleFavorite(aid, folderId)
            }
        }
    }

    private suspend fun doToggleFavorite(aid: Long, folderId: Long) {
        val state = _uiState.value
        val add = if (state.isFavorited) "" else folderId.toString()
        val del = if (state.isFavorited) folderId.toString() else ""

        repository.dealFavResource(rid = aid, addMediaIds = add, delMediaIds = del)
            .onSuccess {
                val newFav = !state.isFavorited
                val newCount = state.favoriteCount + if (newFav) 1 else -1
                _uiState.value = _uiState.value.copy(
                    isFavorited = newFav,
                    favoriteCount = maxOf(0L, newCount),
                    isTogglingFavorite = false
                )
            }
            .onFailure { e ->
                Log.e(BilibiliApp.TAG, "toggleFavorite failed", e)
                _uiState.value = _uiState.value.copy(isTogglingFavorite = false)
            }
    }

    private suspend fun loadComments(aid: Long, mode: Int = _uiState.value.commentSortMode) {
        Log.d(BilibiliApp.TAG, "load comments: aid=$aid mode=$mode")
        _uiState.value = _uiState.value.copy(loadingComments = true)
        repository.getComments(aid, mode = mode)
            .onSuccess { commentList ->
                val pinned = (commentList.topReplies ?: emptyList()).map { c ->
                    c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                }
                val pinnedRpids = pinned.map { it.rpid }.toSet()
                val replies = (commentList.replies ?: emptyList())
                    .filter { it.rpid !in pinnedRpids }
                    .map { c ->
                        c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                    }
                val cursor = commentList.cursor
                Log.d(BilibiliApp.TAG, "comments loaded: ${replies.size} comments, ${pinned.size} pinned, cursor=${cursor}")
                _uiState.value = _uiState.value.copy(
                    comments = replies,
                    pinnedComments = pinned,
                    nextCursor = cursor?.next ?: 0,
                    hasMoreComments = cursor?.isEnd != true,
                    loadingComments = false
                )
            }
            .onFailure {
                Log.e(BilibiliApp.TAG, "load comments failed", it)
                _uiState.value = _uiState.value.copy(loadingComments = false)
            }
    }

    fun setCommentSort(aid: Long, mode: Int) {
        val state = _uiState.value
        if (mode == state.commentSortMode) return
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
            loadComments(aid, mode)
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
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.getComments(aid, page = state.nextCursor, mode = state.commentSortMode)
                .onSuccess { commentList ->
                    val pinnedRpids = _uiState.value.pinnedComments.map { it.rpid }.toSet()
                    val newReplies = (commentList.replies ?: emptyList())
                        .filter { it.rpid !in pinnedRpids }
                        .map { c ->
                            c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                        }
                    val cursor = commentList.cursor
                    Log.d(BilibiliApp.TAG, "more comments loaded: ${newReplies.size} comments, cursor=${cursor}")
                    _uiState.value = _uiState.value.copy(
                        comments = (_uiState.value.comments ?: emptyList()) + newReplies,
                        nextCursor = cursor?.next ?: 0,
                        hasMoreComments = cursor?.isEnd != true,
                        loadingMore = false
                    )
                }
                .onFailure {
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
                    val items = (commentList.replies ?: emptyList()).map { c ->
                        c.copy(content = c.content.copy(message = decodeHtmlEntities(c.content.message)))
                    }
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
                        items = thread.items + newItems,
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
}
