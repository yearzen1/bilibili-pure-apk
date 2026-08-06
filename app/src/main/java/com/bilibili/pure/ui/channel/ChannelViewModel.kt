package com.bilibili.pure.ui.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.SpaceAccInfo
import com.bilibili.pure.data.model.UserCardData
import com.bilibili.pure.data.model.UserSpacePage
import com.bilibili.pure.data.model.UserVideoItem
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelUiState(
    val mid: Long = 0,
    val videos: List<UserVideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    val spaceAccInfo: SpaceAccInfo? = null,
    val userCard: UserCardData? = null,
    val isLoggedIn: Boolean = false,
    val isFollowed: Boolean = false,
    val isTogglingFollow: Boolean = false
)

class ChannelViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    fun load(mid: Long) {
        if (mid == _uiState.value.mid) return
        val isLoggedIn = BilibiliApi.loginCookies.isNotEmpty()
        Log.d(BilibiliApp.TAG, "ChannelVM: load mid=$mid isLoggedIn=$isLoggedIn")
        viewModelScope.launch {
            _uiState.value = ChannelUiState(mid = mid, isLoading = true, isLoggedIn = isLoggedIn)
            repository.getUserVideos(mid)
                .onSuccess { (videos, page) ->
                    Log.d(BilibiliApp.TAG, "ChannelVM: loaded ${videos.size} videos")
                    val defaultInfo = videos.firstOrNull()?.let { video ->
                        SpaceAccInfo(
                            mid = video.mid,
                            name = video.author,
                            face = "",
                            sign = "",
                            attribute = 0
                        )
                    }
                    _uiState.value = ChannelUiState(
                        mid = mid,
                        videos = videos,
                        spaceAccInfo = defaultInfo,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = page?.let { it.pn * it.ps < it.count } ?: false,
                        isLoggedIn = isLoggedIn
                    )
                    loadSpaceInfo(mid)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "ChannelVM: load failed", e)
                    _uiState.value = ChannelUiState(mid = mid, error = e.message, isLoggedIn = isLoggedIn)
                }
        }
    }

    private suspend fun loadSpaceInfo(mid: Long) {
        repository.getUserCard(mid)
            .onSuccess { card ->
                val info = card.card?.let {
                    SpaceAccInfo(mid = it.mid, name = it.name, face = it.face, sign = it.sign, attribute = 0)
                }
                _uiState.value = _uiState.value.copy(
                    spaceAccInfo = info ?: _uiState.value.spaceAccInfo,
                    userCard = card,
                    isFollowed = card.following
                )
            }
            .onFailure {
                Log.e(BilibiliApp.TAG, "ChannelVM: loadSpaceInfo failed", it)
            }
        withContext(NonCancellable) {
            repository.checkRelation(mid)
        }
            .onSuccess { followed ->
                _uiState.value = _uiState.value.copy(isFollowed = followed)
            }
            .onFailure { e ->
                Log.e(BilibiliApp.TAG, "ChannelVM: checkRelation also failed", e)
            }
    }

    fun toggleFollow() {
        val state = _uiState.value
        if (state.isTogglingFollow) return
        _uiState.value = state.copy(isTogglingFollow = true)
        viewModelScope.launch {
            val act = if (state.isFollowed) 2 else 1
            repository.modifyRelation(fid = state.mid, act = act)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isFollowed = !state.isFollowed,
                        isTogglingFollow = false
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "ChannelVM: toggleFollow failed", e)
                    _uiState.value = _uiState.value.copy(isTogglingFollow = false)
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
