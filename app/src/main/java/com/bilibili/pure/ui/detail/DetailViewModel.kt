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

data class DetailUiState(
    val videoInfo: VideoInfo? = null,
    val comments: List<CommentItem>? = null,
    val isLoading: Boolean = false,
    val error: String? = null
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
                Log.d(BilibiliApp.TAG, "comments loaded: ${replies.size} comments")
                _uiState.value = _uiState.value.copy(
                    comments = replies
                )
            }
            .onFailure { Log.e(BilibiliApp.TAG, "load comments failed", it) }
    }
}
