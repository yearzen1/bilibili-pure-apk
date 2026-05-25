package com.bilibili.pure.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.model.VideoInfo
import com.bilibili.pure.data.model.VideoPage
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val videoUrl: String? = null,
    val videoInfo: VideoInfo? = null,
    val currentPage: VideoPage? = null,
    val pages: List<VideoPage> = emptyList(),
    val title: String = ""
)

class PlayerViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun load(bvid: String) {
        Log.d(BilibiliApp.TAG, "PlayerVM: load bvid=$bvid")
        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)

            repository.getVideoInfo(bvid)
                .onSuccess { info ->
                    val pages = info.pages?.ifEmpty {
                        listOf(VideoPage(info.cid, 1, "", info.pages?.firstOrNull()?.duration ?: 0))
                    } ?: listOf(VideoPage(info.cid, 1, "", 0))

                    val firstPage = pages.first()
                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        pages = pages,
                        currentPage = firstPage,
                        title = info.title
                    )
                    loadPlayUrl(bvid, firstPage.cid)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: load failed", e)
                    _uiState.value = PlayerUiState(isLoading = false, error = e.message ?: "加载失败")
                }
        }
    }

    fun selectPage(page: VideoPage) {
        val bvid = _uiState.value.videoInfo?.bvid ?: return
        Log.d(BilibiliApp.TAG, "PlayerVM: selectPage page=${page.page} cid=${page.cid} part=${page.part}")
        _uiState.value = _uiState.value.copy(currentPage = page, isLoading = true, videoUrl = null)
        loadPlayUrl(bvid, page.cid)
    }

    private fun loadPlayUrl(bvid: String, cid: Long) {
        viewModelScope.launch {
            repository.getPlayUrl(bvid, cid)
                .onSuccess { url ->
                    Log.d(BilibiliApp.TAG, "PlayerVM: playUrl loaded")
                    _uiState.value = _uiState.value.copy(videoUrl = url, isLoading = false)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: loadPlayUrl failed", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载播放链接失败")
                }
        }
    }
}
