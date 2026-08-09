package com.bilibili.pure.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.local.PlaybackProgressManager
import com.bilibili.pure.data.model.*
import com.bilibili.pure.data.local.AppSettings
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
    val resumePositionMs: Long = 0L,
    val availableQualities: List<QualityOption> = emptyList(),
    val currentQuality: QualityOption? = null,
    val dashVideo: DashStream? = null,
    val dashAudio: DashStream? = null,
    val isDash: Boolean = false
)

class PlayerViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
    private val playbackProgressManager: PlaybackProgressManager = PlaybackProgressManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    )
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var cachedDashData: DashData? = null

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

                    val lastPageCid = playbackProgressManager.lastCid(info.aid)
                    val startPage = if (lastPageCid != null) {
                        pages.find { it.cid == lastPageCid } ?: firstPage
                    } else {
                        firstPage
                    }

                    val resumeMs = (playbackProgressManager.load(info.aid, startPage.cid) ?: 0L) * 1000L

                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        pages = pages,
                        currentPage = startPage,
                        title = info.title,
                        resumePositionMs = resumeMs
                    )
                    Log.d(BilibiliApp.TAG, "PlayerVM: resume aid=${info.aid} -> pageCid=${startPage.cid} resumeMs=${resumeMs}")
                    loadPlayUrlDash(bvid, startPage.cid)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: load failed", e)
                    _uiState.value = PlayerUiState(isLoading = false, error = e.message ?: "加载失败")
                }
        }
    }

    fun loadLocal(filePath: String) {
        Log.d(BilibiliApp.TAG, "PlayerVM: loadLocal filePath=$filePath")
        _uiState.value = PlayerUiState(
            isLoading = false,
            videoUrl = "file://$filePath",
            title = filePath.substringAfterLast("/").substringBeforeLast("."),
            isDash = false
        )
    }

    fun selectPage(page: VideoPage) {
        val info = _uiState.value.videoInfo ?: return
        val bvid = info.bvid
        Log.d(BilibiliApp.TAG, "PlayerVM: selectPage page=${page.page} cid=${page.cid} part=${page.part}")

        // Resume this page's own progress (never inherit the previous page's position).
        val resumeMs = (playbackProgressManager.load(info.aid, page.cid) ?: 0L) * 1000L

        _uiState.value = _uiState.value.copy(
            currentPage = page,
            resumePositionMs = resumeMs,
            isLoading = true,
            videoUrl = null
        )
        loadPlayUrlDash(bvid, page.cid)
    }

    fun selectQuality(quality: QualityOption) {
        val bvid = _uiState.value.videoInfo?.bvid ?: return
        val cid = _uiState.value.currentPage?.cid ?: return
        if (quality.quality == _uiState.value.currentQuality?.quality) return
        Log.d(BilibiliApp.TAG, "PlayerVM: selectQuality qn=${quality.quality} desc=${quality.description}")

        val dash = cachedDashData ?: return
        val videoStream = pickVideoStream(dash.video, quality.quality) ?: return
        val audioStream = dash.audio?.firstOrNull() ?: dash.flac?.audio

        val url = videoStream.getUrl()
        _uiState.value = _uiState.value.copy(
            currentQuality = quality,
            videoUrl = url,
            dashVideo = videoStream,
            dashAudio = audioStream,
            isDash = true
        )
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

    private fun loadPlayUrlDash(bvid: String, cid: Long) {
        viewModelScope.launch {
            repository.getPlayUrlDash(bvid, cid)
                .onSuccess { playUrlInfo ->
                    Log.d(BilibiliApp.TAG, "PlayerVM: dash loaded, accept=${playUrlInfo.accept_quality}")
                    val dash = playUrlInfo.dash
                    if (dash != null) {
                        cachedDashData = dash

                        val qualities = buildQualityList(playUrlInfo)
                        val defaultQuality = qualities.firstOrNull()

                        val videoStream = defaultQuality?.let { pickVideoStream(dash.video, it.quality) }
                        val audioStream = dash.audio?.firstOrNull() ?: dash.flac?.audio

                        val videoUrl = videoStream?.getUrl()

                        _uiState.value = _uiState.value.copy(
                            videoUrl = videoUrl,
                            availableQualities = qualities,
                            currentQuality = defaultQuality,
                            dashVideo = videoStream,
                            dashAudio = audioStream,
                            isDash = true,
                            isLoading = false
                        )
                        Log.d(BilibiliApp.TAG, "PlayerVM: default quality=${defaultQuality?.description} videoUrl=${videoUrl?.take(80)}")
                    } else {
                        val url = playUrlInfo.durl?.firstOrNull()?.url
                        if (url != null) {
                            _uiState.value = _uiState.value.copy(
                                videoUrl = url,
                                isDash = false,
                                isLoading = false
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "无法获取播放链接"
                            )
                        }
                    }
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: loadPlayUrlDash failed", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载播放链接失败")
                }
        }
    }

    private fun buildQualityList(playUrlInfo: PlayUrlInfo): List<QualityOption> {
        val acceptQuality = playUrlInfo.accept_quality ?: return emptyList()
        val acceptDesc = playUrlInfo.accept_description ?: return emptyList()

        return acceptQuality.zip(acceptDesc).map { (q, desc) ->
            QualityOption(quality = q, description = desc)
        }
    }

    private fun pickVideoStream(videos: List<DashStream>?, targetQuality: Int): DashStream? {
        if (videos.isNullOrEmpty()) return null

        val matching = videos.filter { it.id == targetQuality }
        if (matching.isNotEmpty()) {
            return matching.minByOrNull { it.bandwidth } ?: matching.first()
        }

        val lower = videos.filter { it.id < targetQuality }
        if (lower.isNotEmpty()) {
            return lower.maxByOrNull { it.id } ?: lower.first()
        }

        return videos.minByOrNull { it.id }
    }
}
