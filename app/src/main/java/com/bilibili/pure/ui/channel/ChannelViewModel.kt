package com.bilibili.pure.ui.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.model.UserSpacePage
import com.bilibili.pure.data.model.UserVideoItem
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelUiState(
    val mid: Long = 0,
    val videos: List<UserVideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false
)

class ChannelViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    fun load(mid: Long) {
        if (mid == _uiState.value.mid) return
        Log.d(BilibiliApp.TAG, "ChannelVM: load mid=$mid")
        viewModelScope.launch {
            _uiState.value = ChannelUiState(mid = mid, isLoading = true)
            repository.getUserVideos(mid)
                .onSuccess { (videos, page) ->
                    Log.d(BilibiliApp.TAG, "ChannelVM: loaded ${videos.size} videos")
                    _uiState.value = ChannelUiState(
                        mid = mid,
                        videos = videos,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = page?.let { it.pn * it.ps < it.count } ?: false
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "ChannelVM: load failed", e)
                    _uiState.value = ChannelUiState(mid = mid, error = e.message)
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || !state.hasMore) return
        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.getUserVideos(state.mid, page = nextPage)
                .onSuccess { (videos, page) ->
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos + videos,
                        currentPage = nextPage,
                        hasMore = page?.let { it.pn * it.ps < it.count } ?: false,
                        loadingMore = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
        }
    }
}
