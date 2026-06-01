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

    suspend fun getHistory(page: Int = 1, pageSize: Int = 20): Result<List<HistoryItem>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getHistory: page=$page pageSize=$pageSize")
        return runCatching {
            val response = api.getHistory(page = page, pageSize = pageSize)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getHistory response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: emptyList()
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getHistory failed", it) }
    }

    private var fullHistoryCache: List<HistoryItem>? = null

    suspend fun getAllHistory(): Result<List<HistoryItem>> {
        fullHistoryCache?.let { return Result.success(it) }
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getAllHistory: start")
        val all = mutableListOf<HistoryItem>()
        var page = 1
        while (true) {
            val r = getHistory(page = page)
            if (r.isFailure) return Result.failure(
                r.exceptionOrNull() ?: Exception("Unknown error")
            )
            val list = r.getOrNull() ?: break
            all.addAll(list)
            if (list.size < 20) break
            page++
        }
        fullHistoryCache = all
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getAllHistory: done, ${all.size} items")
        return Result.success(all)
    }

    fun clearHistoryCache() {
        fullHistoryCache = null
    }

    suspend fun reportProgress(aid: Long, cid: Long, progress: Long): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "reportProgress: aid=$aid cid=$cid progress=${progress}s")
        return runCatching {
            val response = api.reportProgress(aid = aid, cid = cid, progress = progress, csrf = BilibiliApi.biliJct)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "reportProgress response: code=${response.code} msg=${response.message}")
            if (response.code != 0) {
                Log.w(BilibiliApp.TAG, "reportProgress failed: code=${response.code} msg=${response.message}")
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "reportProgress exception", it) }
    }

    suspend fun getUserVideos(mid: Long, page: Int = 1): Result<Pair<List<UserVideoItem>, UserSpacePage?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserVideos: mid=$mid page=$page")
        return runCatching<Pair<List<UserVideoItem>, UserSpacePage?>> {
            val response = api.getUserVideos(mid = mid, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserVideos response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list?.vlist ?: emptyList(), data?.page)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getUserVideos failed", it) }
    }

    suspend fun searchChannelVideos(mid: Long, keyword: String, page: Int = 1): Result<Pair<List<UserVideoItem>, UserSpacePage?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchChannelVideos: mid=$mid keyword=$keyword page=$page")
        return runCatching<Pair<List<UserVideoItem>, UserSpacePage?>> {
            val response = api.getUserVideos(mid = mid, keyword = keyword, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchChannelVideos response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list?.vlist ?: emptyList(), data?.page)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "searchChannelVideos failed", it) }
    }

    suspend fun getFavFolders(upMid: Long): Result<List<FavFolder>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavFolders: upMid=$upMid")
        return runCatching {
            val response = api.getFavFolders(upMid = upMid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavFolders response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data?.list ?: emptyList()
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFavFolders failed", it) }
    }

    suspend fun getFavResources(mediaId: Long, page: Int = 1): Result<Pair<List<FavResourceItem>, Boolean>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavResources: mediaId=$mediaId page=$page")
        return runCatching<Pair<List<FavResourceItem>, Boolean>> {
            val response = api.getFavResources(mediaId = mediaId, pn = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavResources response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.medias ?: emptyList(), data?.hasMore ?: false)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFavResources failed", it) }
    }

    suspend fun checkFavoured(aid: Long): Result<Boolean> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "checkFavoured: aid=$aid")
        return runCatching {
            val response = api.getFavoured(aid = aid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "checkFavoured response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data?.favoured ?: false
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "checkFavoured failed", it) }
    }

    suspend fun dealFavResource(rid: Long, addMediaIds: String, delMediaIds: String): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "dealFavResource: rid=$rid add=$addMediaIds del=$delMediaIds")
        return runCatching {
            val response = api.dealFavResource(
                rid = rid,
                addMediaIds = addMediaIds,
                delMediaIds = delMediaIds,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "dealFavResource response: code=${response.code} msg=${response.message}")
            if (response.code != 0) {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "dealFavResource failed", it) }
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

    suspend fun modifyRelation(fid: Long, act: Int): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "modifyRelation: fid=$fid act=$act")
        return runCatching {
            val response = api.modifyRelation(fid = fid, act = act, csrf = BilibiliApi.biliJct)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "modifyRelation response: code=${response.code} msg=${response.message}")
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "modifyRelation failed", it) }
    }

    suspend fun getFollowings(vmid: Long, page: Int = 1): Result<Pair<List<FollowingItem>, Int>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFollowings: vmid=$vmid page=$page")
        return runCatching<Pair<List<FollowingItem>, Int>> {
            val response = api.getFollowings(vmid = vmid, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFollowings response: code=${response.code}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list ?: emptyList(), data?.total ?: 0)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFollowings failed", it) }
    }

    suspend fun getRelationStat(vmid: Long): Result<RelationStat> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getRelationStat: vmid=$vmid")
        return runCatching {
            val response = api.getRelationStat(vmid = vmid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getRelationStat response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty stat")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getRelationStat failed", it) }
    }

    suspend fun getSpaceAccInfo(mid: Long): Result<SpaceAccInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceAccInfo: mid=$mid")
        return runCatching {
            val response = api.getSpaceAccInfo(mid = mid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceAccInfo response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty space info")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getSpaceAccInfo failed", it) }
    }

    suspend fun checkRelation(fid: Long): Result<Boolean> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "checkRelation: fid=$fid")
        return try {
            val response = api.getRelation(fid)
            if (response.code != 0) {
                Result.failure(Exception("getRelation failed: code=${response.code} msg=${response.message}"))
            } else {
                val attr = response.data?.attribute ?: 0
                Result.success(attr == 2 || attr == 6)
            }
        } catch (e: Exception) {
            Log.e(BilibiliApp.TAG, "checkRelation failed", e)
            Result.failure(e)
        }
    }

    suspend fun isFollowing(selfMid: Long, targetMid: Long): Result<Boolean> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "isFollowing: self=$selfMid target=$targetMid")
        var page = 1
        val maxPages = 10
        val pageSize = 100
        while (page <= maxPages) {
            try {
                val response = api.getFollowings(vmid = selfMid, pageSize = 100, page = page)
                if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "isFollowing page=$page: code=${response.code}")
                if (response.code != 0) {
                    Log.w(BilibiliApp.TAG, "isFollowing page $page failed: code=${response.code} msg=${response.message}")
                    page++
                    continue
                }
                val list = response.data?.list ?: emptyList()
                if (list.any { it.mid == targetMid }) return Result.success(true)
                if (list.size < pageSize) break
                page++
            } catch (e: Exception) {
                Log.w(BilibiliApp.TAG, "isFollowing page $page exception", e)
                page++
            }
        }
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "isFollowing: not found after $page pages")
        return Result.success(false)
    }
}
