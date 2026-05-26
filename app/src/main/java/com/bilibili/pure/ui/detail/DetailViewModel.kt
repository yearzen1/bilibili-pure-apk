package com.bilibili.pure.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.model.CommentItem
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReplyThread(
    val items: List<CommentItem> = emptyList(),
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false
)

data class DetailUiState(
    val videoInfo: VideoInfo? = null,
    val comments: List<CommentItem>? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val replyThreads: Map<Long, ReplyThread> = emptyMap(),
    val expandedReplies: Set<Long> = emptySet(),
    val nextCursor: Int = 0,
    val hasMoreComments: Boolean = true,
    val loadingMore: Boolean = false
)

class DetailViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(bvid: String) {
        Log.d(BilibiliApp.TAG, "load detail: bvid=$bvid")
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)

            repository.getVideoInfo(bvid)
                .onSuccess { info ->
                    Log.d(BilibiliApp.TAG, "detail loaded: title=${info.title} aid=${info.aid}")
                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        isLoading = false
                    )
                    loadComments(info.aid)
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

    private suspend fun loadComments(aid: Long) {
        Log.d(BilibiliApp.TAG, "load comments: aid=$aid")
        repository.getComments(aid)
            .onSuccess { commentList ->
                val replies = commentList.replies ?: emptyList()
                val cursor = commentList.cursor
                Log.d(BilibiliApp.TAG, "comments loaded: ${replies.size} comments, cursor=${cursor}")
                _uiState.value = _uiState.value.copy(
                    comments = replies,
                    nextCursor = cursor?.next ?: 0,
                    hasMoreComments = cursor?.isEnd != true
                )
            }
            .onFailure { Log.e(BilibiliApp.TAG, "load comments failed", it) }
    }

    fun loadMoreComments(aid: Long) {
        val state = _uiState.value
        if (state.loadingMore || !state.hasMoreComments) return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.getComments(aid, page = state.nextCursor)
                .onSuccess { commentList ->
                    val newReplies = commentList.replies ?: emptyList()
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
                    val items = commentList.replies ?: emptyList()
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
                    val newItems = commentList.replies ?: emptyList()
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
