package com.bilibili.pure.data.repository

import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.*

class BilibiliRepository(
    private val api: BilibiliApi = BilibiliApi.create()
) {
    suspend fun search(keyword: String, page: Int = 1): Result<List<SearchVideoItem>> {
        return runCatching {
            val response = api.search(keyword = keyword, page = page)
            if (response.code == 0) {
                response.data?.result ?: emptyList()
            } else {
                throw Exception(response.message)
            }
        }
    }

    suspend fun getVideoInfo(bvid: String): Result<VideoInfo> {
        return runCatching {
            val response = api.getVideoInfo(bvid)
            if (response.code == 0) {
                response.data ?: throw Exception("Video not found")
            } else {
                throw Exception(response.message)
            }
        }
    }

    suspend fun getComments(aid: Long, page: Int = 0): Result<CommentList> {
        return runCatching {
            val response = api.getComments(oid = aid, next = page)
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }
    }
}
