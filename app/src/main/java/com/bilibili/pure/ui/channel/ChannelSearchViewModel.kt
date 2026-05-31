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

data class ChannelSearchUiState(
    val mid: Long = 0,
    val keyword: String = "",
    val videos: List<UserVideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false
)

class ChannelSearchViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelSearchUiState())
    val uiState: StateFlow<ChannelSearchUiState> = _uiState.asStateFlow()

    fun search(mid: Long, keyword: String) {
        if (mid == _uiState.value.mid && keyword == _uiState.value.keyword) return
        Log.d(BilibiliApp.TAG, "ChannelSearchVM: search mid=$mid keyword=$keyword")
        viewModelScope.launch {
            _uiState.value = ChannelSearchUiState(mid = mid, keyword = keyword, isLoading = true)
            repository.searchChannelVideos(mid, keyword)
                .onSuccess { (videos, page) ->
                    Log.d(BilibiliApp.TAG, "ChannelSearchVM: loaded ${videos.size} videos")
                    _uiState.value = ChannelSearchUiState(
                        mid = mid,
                        keyword = keyword,
                        videos = videos,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = page?.let { it.pn * it.ps < it.count } ?: false
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "ChannelSearchVM: search failed", e)
                    _uiState.value = ChannelSearchUiState(mid = mid, keyword = keyword, error = e.message)
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || !state.hasMore) return
        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.searchChannelVideos(state.mid, state.keyword, nextPage)
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
