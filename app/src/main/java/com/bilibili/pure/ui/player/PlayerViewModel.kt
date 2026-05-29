package com.bilibili.pure.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.local.PlaybackProgressManager
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
    val title: String = "",
    val historyProgress: Long = 0L,
    val historyCid: Long = 0L
)

class PlayerViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
    private val playbackProgressManager: PlaybackProgressManager = PlaybackProgressManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    )
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

                    val localProgress = playbackProgressManager.load(info.aid)
                    val historyProgress = localProgress?.progress ?: 0L
                    val historyCid = localProgress?.cid ?: 0L

                    val startPage = if (historyCid > 0) {
                        pages.find { it.cid == historyCid } ?: firstPage
                    } else {
                        firstPage
                    }

                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        pages = pages,
                        currentPage = startPage,
                        title = info.title,
                        historyProgress = historyProgress,
                        historyCid = historyCid
                    )
                    Log.d(BilibiliApp.TAG, "PlayerVM: resume aid=${info.aid} -> progress=${historyProgress}s pageCid=${historyCid} startCid=${startPage.cid}")
                    loadPlayUrl(bvid, startPage.cid)
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

    fun reportProgress(aid: Long, cid: Long, progress: Long, duration: Long = 0L) {
        if (BilibiliApi.loginCookies.isNotBlank()) {
            viewModelScope.launch {
                repository.reportProgress(aid, cid, progress)
            }
        }
        if (duration > 0) {
            playbackProgressManager.save(aid, cid, progress, duration)
        }
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
