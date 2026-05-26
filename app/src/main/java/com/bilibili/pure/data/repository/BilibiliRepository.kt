package com.bilibili.pure.data.repository

import android.util.Log
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.api.PassportApi
import com.bilibili.pure.data.model.*

class BilibiliRepository(
    private val api: BilibiliApi = BilibiliApi.create(),
    private val passportApi: PassportApi = PassportApi.create()
) {
    suspend fun search(keyword: String, page: Int = 1, order: String? = null): Result<Pair<List<SearchVideoItem>, Int>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "search: keyword=$keyword page=$page order=$order")
        return runCatching<Pair<List<SearchVideoItem>, Int>> {
            val response = api.search(keyword = keyword, page = page, order = order?.ifEmpty { null })
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "search response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.result ?: emptyList(), data?.totalPages ?: 1)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "search failed", it) }
    }

    suspend fun getVideoInfo(bvid: String): Result<VideoInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getVideoInfo: bvid=$bvid")
        return runCatching {
            val response = api.getVideoInfo(bvid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getVideoInfo response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: throw Exception("Video not found")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getVideoInfo failed", it) }
    }

    suspend fun getPlayUrl(bvid: String, cid: Long, qn: Int = 80): Result<String> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrl: bvid=$bvid cid=$cid qn=$qn fnval=1 platform=android")
        return runCatching {
            val response = api.getPlayUrl(bvid = bvid, cid = cid, qn = qn)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrl response: code=${response.code} msg=${response.message} ttl=${response.ttl}")
            if (response.code == 0) {
                val playUrl = response.data
                if (BuildConfig.DEBUG) {
                    val acceptDesc = playUrl?.accept_description?.joinToString(", ") ?: "N/A"
                    val acceptQual = playUrl?.accept_quality?.joinToString(", ") ?: "N/A"
                    Log.d(BilibiliApp.TAG, "getPlayUrl success: quality=${playUrl?.quality}(${playUrl?.format}) " +
                        "duration=${playUrl?.timelength}ms accept=[$acceptQual]($acceptDesc) " +
                        "durl=${playUrl?.durl?.size} segments")
                    playUrl?.durl?.forEachIndexed { i, d ->
                        Log.d(BilibiliApp.TAG, "getPlayUrl durl[$i]: size=${d.size} len=${d.length} url=${d.url.take(100)}...")
                    }
                }
                val url = playUrl?.durl?.firstOrNull()?.url
                    ?: throw Exception("No playable URL")
                url
            } else {
                Log.w(BilibiliApp.TAG, "getPlayUrl failed: code=${response.code} msg=${response.message}")
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPlayUrl exception", it) }
    }

    suspend fun getComments(aid: Long, page: Int = 0): Result<CommentList> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getComments: aid=$aid page=$page")
        return runCatching {
            val response = api.getComments(oid = aid, next = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getComments response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getComments failed", it) }
    }

    suspend fun getReplies(aid: Long, root: Long, page: Int = 1): Result<CommentList> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getReplies: aid=$aid root=$root page=$page")
        return runCatching {
            val response = api.getReplies(oid = aid, root = root, pn = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getReplies response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getReplies failed", it) }
    }

    suspend fun generateQRCode(): Result<QRLoginData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "generateQRCode")
        return runCatching {
            val response = passportApi.generateQRCode()
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "generateQRCode response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty QR code data")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "generateQRCode failed", it) }
    }

    suspend fun pollQRCode(key: String): Result<Pair<Int, QRPollData?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "pollQRCode: key=$key")
        return runCatching {
            val response = passportApi.pollQRCode(key)
            val statusCode = response.data?.code ?: -1
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "pollQRCode response: outer=${response.code} inner=$statusCode msg=${response.data?.message}")
            Pair(statusCode, response.data)
        }.onFailure { Log.e(BilibiliApp.TAG, "pollQRCode failed", it) }
    }
}
