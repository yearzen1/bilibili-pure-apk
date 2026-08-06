package com.bilibili.pure.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?,
    val ttl: Int = 0
)

data class SearchResult(
    val result: List<SearchVideoItem>?,
    val page: Int = 1,
    val pagesize: Int = 20,
    @SerializedName("numResults") val totalResults: Int = 0,
    @SerializedName("numPages") val totalPages: Int = 0
)

data class SearchVideoItem(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    val author: String,
    @SerializedName("play") val playCount: Long,
    @SerializedName("video_review") val danmakuCount: Long,
    val pubdate: Long,
    val duration: String,
    val description: String,
    @SerializedName("upic") val authorAvatar: String? = null,
    @SerializedName("mid") val authorId: Long = 0
)

data class VideoInfo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    val desc: String,
    val owner: VideoOwner,
    val stat: VideoStat,
    val cid: Long,
    val tid: Long = 0,
    val tname: String = "",
    val videos: Int = 1,
    val pubdate: Long = 0,
    val pages: List<VideoPage>? = null
)

data class VideoOwner(
    val mid: Long,
    val name: String,
    val face: String
)

data class VideoStat(
    val view: Long = 0,
    val like: Long = 0,
    val coin: Long = 0,
    val favorite: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val share: Long = 0
)

data class VideoPage(
    val cid: Long,
    val page: Int,
    val part: String,
    val duration: Long
)

data class CommentList(
    val replies: List<CommentItem>?,
    val cursor: CommentCursor? = null
)

data class CommentItem(
    val rpid: Long,
    val content: CommentContent,
    val member: CommentMember,
    val like: Int = 0,
    val rcount: Int = 0,
    val ctime: Long = 0
)

data class CommentContent(
    val message: String,
    val pictures: List<CommentPicture>? = null
)

data class CommentPicture(
    @SerializedName("img_src") val imgSrc: String,
    @SerializedName("img_width") val imgWidth: Int,
    @SerializedName("img_height") val imgHeight: Int,
    @SerializedName("img_size") val imgSize: Double
)

data class CommentMember(
    val mid: Long = 0,
    val uname: String,
    val avatar: String
)

data class CommentCursor(
    @SerializedName("prev") val prev: Int = 0,
    @SerializedName("next") val next: Int = 0,
    @SerializedName("is_end") val isEnd: Boolean = true
)

data class PlayUrlInfo(
    val quality: Int,
    val format: String,
    val timelength: Long,
    val accept_quality: List<Int>? = null,
    val accept_description: List<String>? = null,
    val durl: List<DurlItem>? = null,
    val dash: DashData? = null
)

data class DurlItem(
    val url: String,
    val length: Long,
    val size: Long
)

data class DashData(
    val duration: Int = 0,
    val minBufferTime: Double = 1.5,
    val video: List<DashStream>? = null,
    val audio: List<DashStream>? = null,
    val dolby: DashDolby? = null,
    val flac: DashFlac? = null
)

data class DashStream(
    val id: Int = 0,
    @SerializedName("baseUrl") val baseUrl: String = "",
    @SerializedName("base_url") val base_url: String = "",
    @SerializedName("backupUrl") val backupUrl: List<String>? = null,
    @SerializedName("backup_url") val backup_url: List<String>? = null,
    val bandwidth: Int = 0,
    @SerializedName("mimeType") val mimeType: String = "",
    @SerializedName("mime_type") val mime_type: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerializedName("frameRate") val frameRate: String = "",
    @SerializedName("frame_rate") val frame_rate: String = "",
    val codecid: Int = 0
) {
    fun getUrl(): String = baseUrl.ifEmpty { base_url }
    fun getBackupUrls(): List<String> = (backupUrl ?: backup_url ?: emptyList())
}

data class DashDolby(
    val type: Int = 0,
    val audio: List<DashStream>? = null
)

data class DashFlac(
    val display: Boolean = false,
    val audio: DashStream? = null
)

data class QualityOption(
    val quality: Int,
    val description: String
)

data class DownloadInfo(
    val id: String,
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val quality: Int,
    val qualityDesc: String,
    val filePath: String,
    val fileSize: Long = 0,
    val totalSize: Long = -1,
    val status: Int = 0,
    val speed: Long = 0,
    val createTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
        const val STATUS_PAUSED = 4
    }
}

data class HistoryPage(
    val page: Int = 1,
    val part: String = "",
    val duration: Long = 0,
    val cid: Long = 0
)

data class HistoryItem(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    val owner: VideoOwner,
    val stat: VideoStat,
    val duration: Long,
    val progress: Long,
    @SerializedName("view_at") val viewAt: Long,
    val cid: Long = 0,
    val page: HistoryPage? = null,
    val videos: Int = 1
)

data class QRLoginData(
    val url: String,
    @SerializedName("qrcode_key") val qrcodeKey: String
)

data class QRPollData(
    val url: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val timestamp: Long = 0,
    val code: Int? = null,
    val message: String? = null
)

data class CaptchaData(
    val type: String = "",
    val token: String = "",
    val geetest: GeetestData? = null
)

data class GeetestData(
    val gt: String = "",
    val challenge: String = ""
)

data class SmsSendData(
    @SerializedName("captcha_key") val captchaKey: String = ""
)

data class WebKeyData(
    val hash: String = "",
    val key: String = ""
)

data class WebLoginData(
    val url: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val message: String? = null
)

data class UserSpaceData(
    val list: UserVideoList?,
    val page: UserSpacePage?
)

data class UserVideoList(
    val vlist: List<UserVideoItem>?
)

data class UserVideoItem(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    @SerializedName("play") val playCount: Long,
    @SerializedName("video_review") val danmakuCount: Long,
    val pubdate: Long,
    val description: String,
    val mid: Long,
    val author: String
)

data class UserSpacePage(
    val pn: Int = 1,
    val ps: Int = 30,
    val count: Int = 0
)

data class FavFolderList(
    val count: Int = 0,
    val list: List<FavFolder>? = null
)

data class FavFolder(
    val id: Long,
    val title: String,
    @SerializedName("media_count") val mediaCount: Int,
    val cover: String? = null,
    val upper: FavFolderOwner? = null
)

data class FavFolderOwner(
    val mid: Long,
    val name: String,
    val face: String? = null
)

data class FavResourceList(
    val medias: List<FavResourceItem>? = null,
    @SerializedName("has_more") val hasMore: Boolean = false
)

data class FavResourceItem(
    val id: Long,
    val type: Int,
    val title: String,
    val cover: String,
    val upper: FavResourceUpper,
    @SerializedName("cnt_info") val cntInfo: FavResourceCntInfo? = null,
    val duration: Long = 0,
    val pubtime: Long = 0,
    val bvid: String = "",
    val aid: Long = 0
)

data class FavResourceUpper(
    val mid: Long,
    val name: String,
    val face: String? = null
)

data class FavResourceCntInfo(
    val play: Long = 0,
    val danmaku: Long = 0,
    val collect: Long = 0
)

data class FavouredData(
    val favoured: Boolean = false
)

data class FollowingItem(
    val mid: Long,
    val uname: String,
    val face: String,
    val sign: String = "",
    val attribute: Int = 0
)

data class FollowingListData(
    val list: List<FollowingItem>?,
    val total: Int = 0
)

data class RelationStat(
    val mid: Long,
    val following: Int = 0,
    val follower: Int = 0
)

data class RelationData(
    val mid: Long,
    val attribute: Int = 0,
    val mtime: Long = 0,
    val tag: List<Long>? = null,
    val special: Int = 0
)

data class SpaceAccInfo(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String = "",
    val attribute: Int = 0
)

data class NavInfo(
    val isLogin: Boolean = false,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = ""
)

data class UserCardData(
    val card: UserCardInfo? = null,
    val following: Boolean = false,
    val archive_count: Int = 0,
    val follower: Int = 0
)

data class UserCardInfo(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = ""
)
