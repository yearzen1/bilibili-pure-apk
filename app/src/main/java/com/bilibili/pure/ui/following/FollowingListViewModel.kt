package com.bilibili.pure.ui.following

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.FollowingItem
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowingUiState(
    val items: List<FollowingItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val total: Int = 0,
    val loadingMore: Boolean = false
)

class FollowingListViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowingUiState())
    val uiState: StateFlow<FollowingUiState> = _uiState.asStateFlow()

    private var loggedInUid: Long? = null

    fun load() {
        if (_uiState.value.items.isNotEmpty()) return
        val uid = getLoggedInUid() ?: run {
            _uiState.value = FollowingUiState(error = "未登录")
            return
        }
        loggedInUid = uid
        viewModelScope.launch {
            _uiState.value = FollowingUiState(isLoading = true)
            repository.getFollowings(uid)
                .onSuccess { (items, total) ->
                    _uiState.value = FollowingUiState(
                        items = items,
                        currentPage = 1,
                        total = total
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "load followings failed", e)
                    _uiState.value = FollowingUiState(error = e.message ?: "加载失败")
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val uid = loggedInUid ?: return
        if (state.loadingMore || state.items.size >= state.total) return
        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.getFollowings(uid, page = nextPage)
                .onSuccess { (items, _) ->
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items + items,
                        currentPage = nextPage,
                        loadingMore = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
        }
    }

    fun unfollow(mid: Long) {
        viewModelScope.launch {
            repository.modifyRelation(fid = mid, act = 2)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items.filter { it.mid != mid },
                        total = maxOf(0, _uiState.value.total - 1)
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "unfollow failed", e)
                }
        }
    }

    private fun getLoggedInUid(): Long? {
        val cookies = BilibiliApi.loginCookies
        if (cookies.isEmpty()) return null
        return cookies.split(";")
            .firstOrNull { it.trim().startsWith("DedeUserID=") }
            ?.substringAfter("DedeUserID=")
            ?.trim()
            ?.toLongOrNull()
    }
}
