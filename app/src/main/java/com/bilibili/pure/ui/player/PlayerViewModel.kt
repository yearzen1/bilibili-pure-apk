package com.bilibili.pure.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.local.PlaybackProgressManager
import com.bilibili.pure.data.local.SubtitlePreference
import com.bilibili.pure.data.local.SubtitlePreferenceManager
import com.bilibili.pure.data.model.*
import com.bilibili.pure.data.download.DownloadManager
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlaybackSource { ONLINE, LOCAL }

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
    val isDash: Boolean = false,
    val localFiles: Map<Long, String> = emptyMap(),
    val availableSubtitles: List<SubtitleTrack> = emptyList(),
    val currentSubtitle: SubtitleTrack? = null,
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val subtitleEnabled: Boolean = false,
    val subtitleOffsetX: Float = 0f,
    val subtitleOffsetY: Float = 0f
)

class PlayerViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
    private val playbackProgressManager: PlaybackProgressManager = PlaybackProgressManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    ),
    private val subtitlePreferenceManager: SubtitlePreferenceManager = SubtitlePreferenceManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    )
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var cachedDashData: DashData? = null

    fun load(bvid: String, cid: Long? = null, source: PlaybackSource = PlaybackSource.ONLINE) {
        Log.d(BilibiliApp.TAG, "PlayerVM: load bvid=$bvid cid=$cid source=$source")
        viewModelScope.launch {
            if (source == PlaybackSource.LOCAL) {
                // 下载会话：只查「已完成」的下载记录，离线可活；不碰网络
                val completed = DownloadManager.getInstance(BilibiliApp.instance)
                    .getDownloads()
                    .filter { it.bvid == bvid && it.status == DownloadInfo.STATUS_COMPLETED }
                    .sortedBy { it.page }
                if (completed.isEmpty()) {
                    _uiState.value = PlayerUiState(isLoading = false, error = "未找到本地下载")
                    return@launch
                }
                val pages = completed.map { VideoPage(cid = it.cid, page = it.page, part = it.part, duration = 0) }
                val current = completed.find { it.cid == cid } ?: completed.first()
                val currentPage = pages.find { it.cid == current.cid } ?: pages.first()
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    videoUrl = "file://${current.filePath}",
                    pages = pages,
                    currentPage = currentPage,
                    title = completed.first().title,
                    isDash = false,
                    localFiles = completed.associate { it.cid to it.filePath }
                )
                return@launch
            }

            // 在线会话：纯联网，localFiles 留空（不混本地文件）
            _uiState.value = PlayerUiState(isLoading = true)

            repository.getVideoInfo(bvid)
                .onSuccess { info ->
                val pages = info.pages?.ifEmpty {
                    listOf(VideoPage(info.cid, 1, "", info.pages?.firstOrNull()?.duration ?: 0))
                } ?: listOf(VideoPage(info.cid, 1, "", 0))

                val firstPage = pages.first()

                val lastPageCid = playbackProgressManager.lastCid(info.aid)
                val startPage = cid?.let { pages.find { p -> p.cid == cid } }
                    ?: lastPageCid?.let { pages.find { p -> p.cid == it } }
                    ?: firstPage

                val resumeMs = (playbackProgressManager.load(info.aid, startPage.cid) ?: 0L) * 1000L

                _uiState.value = _uiState.value.copy(
                    videoInfo = info,
                    pages = pages,
                    currentPage = startPage,
                    title = info.title,
                    resumePositionMs = resumeMs,
                    localFiles = emptyMap()
                )
                Log.d(BilibiliApp.TAG, "PlayerVM: resume aid=${info.aid} -> pageCid=${startPage.cid} resumeMs=${resumeMs}")
                loadPlayUrlDash(bvid, startPage.cid)
                loadSubtitles(info.aid, startPage.cid)
            }
            .onFailure { e ->
                Log.e(BilibiliApp.TAG, "PlayerVM: load failed", e)
                _uiState.value = PlayerUiState(isLoading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun selectPage(page: VideoPage) {
        // 本地已下载的分 P：直接切本地文件，不联网
        val localPath = _uiState.value.localFiles[page.cid]
        if (localPath != null) {
            Log.d(BilibiliApp.TAG, "PlayerVM: selectPage local page=${page.page} cid=${page.cid}")
            _uiState.value = _uiState.value.copy(
                currentPage = page,
                videoUrl = "file://$localPath",
                isDash = false,
                isLoading = false
            )
            return
        }

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

    private fun loadSubtitles(aid: Long, cid: Long) {
        viewModelScope.launch {
            repository.getSubtitleTracks(aid, cid)
                .onSuccess { tracks ->
                    Log.d(BilibiliApp.TAG, "loadSubtitles: ${tracks.size} tracks")
                    val sortedTracks = tracks.sortedWith(
                        compareBy<SubtitleTrack> { it.type }.thenBy { it.lan }
                    )
                    _uiState.value = _uiState.value.copy(availableSubtitles = sortedTracks)

                    val saved = subtitlePreferenceManager.load(aid, cid)
                    if (saved != null) {
                        _uiState.value = _uiState.value.copy(
                            subtitleOffsetX = saved.offsetX,
                            subtitleOffsetY = saved.offsetY
                        )
                        if (saved.enabled && saved.lan.isNotBlank()) {
                            val matched = sortedTracks.find { it.lan == saved.lan }
                            if (matched != null) {
                                selectSubtitle(matched)
                            }
                        }
                    }
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadSubtitles failed", e)
                }
        }
    }

    fun selectSubtitle(track: SubtitleTrack?) {
        val aid = _uiState.value.videoInfo?.aid ?: return
        val cid = _uiState.value.currentPage?.cid ?: return
        Log.d(BilibiliApp.TAG, "selectSubtitle: ${track?.lanDoc}")

        if (track == null) {
            _uiState.value = _uiState.value.copy(
                currentSubtitle = null,
                subtitleCues = emptyList(),
                subtitleEnabled = false
            )
            subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                lan = "", enabled = false,
                offsetX = _uiState.value.subtitleOffsetX,
                offsetY = _uiState.value.subtitleOffsetY
            ))
            return
        }

        _uiState.value = _uiState.value.copy(
            currentSubtitle = track,
            subtitleEnabled = true
        )
        subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
            lan = track.lan, enabled = true,
            offsetX = _uiState.value.subtitleOffsetX,
            offsetY = _uiState.value.subtitleOffsetY
        ))

        viewModelScope.launch {
            repository.getSubtitleContent(track.subtitleUrl)
                .onSuccess { body ->
                    Log.d(BilibiliApp.TAG, "selectSubtitle: ${body.body.size} cues")
                    _uiState.value = _uiState.value.copy(subtitleCues = body.body)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "selectSubtitle failed", e)
                }
        }
    }

    fun toggleSubtitle() {
        val aid = _uiState.value.videoInfo?.aid ?: return
        val cid = _uiState.value.currentPage?.cid ?: return
        val current = _uiState.value.currentSubtitle
        val enabled = _uiState.value.subtitleEnabled

        if (current == null) {
            val firstTrack = _uiState.value.availableSubtitles.firstOrNull()
            if (firstTrack != null) {
                selectSubtitle(firstTrack)
            }
        } else {
            val newEnabled = !enabled
            _uiState.value = _uiState.value.copy(subtitleEnabled = newEnabled)
            subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                lan = current.lan, enabled = newEnabled,
                offsetX = _uiState.value.subtitleOffsetX,
                offsetY = _uiState.value.subtitleOffsetY
            ))
        }
    }

    fun updateSubtitlePosition(offsetX: Float, offsetY: Float) {
        val aid = _uiState.value.videoInfo?.aid
        val cid = _uiState.value.currentPage?.cid
        _uiState.value = _uiState.value.copy(
            subtitleOffsetX = offsetX,
            subtitleOffsetY = offsetY
        )
        if (aid != null && cid != null) {
            val current = _uiState.value.currentSubtitle
            subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                lan = current?.lan ?: "",
                enabled = _uiState.value.subtitleEnabled,
                offsetX = offsetX,
                offsetY = offsetY
            ))
        }
    }

}
