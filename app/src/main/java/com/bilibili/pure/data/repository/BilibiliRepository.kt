package com.bilibili.pure.data.repository

import android.util.Log
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.*

class BilibiliRepository(
    private val api: BilibiliApi = BilibiliApi.create()
) {
    suspend fun search(keyword: String, page: Int = 1, order: String? = null): Result<Pair<List<SearchVideoItem>, Int>> {
        Log.d(BilibiliApp.TAG, "search: keyword=$keyword page=$page order=$order")
        return runCatching<Pair<List<SearchVideoItem>, Int>> {
            val response = api.search(keyword = keyword, page = page, order = order?.ifEmpty { null })
            Log.d(BilibiliApp.TAG, "search response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.result ?: emptyList(), data?.totalPages ?: 1)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "search failed", it) }
    }

    suspend fun getVideoInfo(bvid: String): Result<VideoInfo> {
        Log.d(BilibiliApp.TAG, "getVideoInfo: bvid=$bvid")
        return runCatching {
            val response = api.getVideoInfo(bvid)
            Log.d(BilibiliApp.TAG, "getVideoInfo response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: throw Exception("Video not found")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getVideoInfo failed", it) }
    }

    suspend fun getPlayUrl(bvid: String, cid: Long, qn: Int = 80): Result<String> {
        Log.d(BilibiliApp.TAG, "getPlayUrl: bvid=$bvid cid=$cid qn=$qn")
        return runCatching {
            val response = api.getPlayUrl(bvid = bvid, cid = cid, qn = qn)
            Log.d(BilibiliApp.TAG, "getPlayUrl response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val playUrl = response.data
                Log.d(BilibiliApp.TAG, "getPlayUrl success: quality=${playUrl?.quality} format=${playUrl?.format} durl=${playUrl?.durl?.size}")
                val url = playUrl?.durl?.firstOrNull()?.url
                    ?: throw Exception("No playable URL")
                Log.d(BilibiliApp.TAG, "getPlayUrl url: ${url.take(80)}...")
                url
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPlayUrl failed", it) }
    }

    suspend fun getComments(aid: Long, page: Int = 0): Result<CommentList> {
        Log.d(BilibiliApp.TAG, "getComments: aid=$aid page=$page")
        return runCatching {
            val response = api.getComments(oid = aid, next = page)
            Log.d(BilibiliApp.TAG, "getComments response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getComments failed", it) }
    }
}
