package com.bilibili.pure.data.repository

import android.util.Log
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.api.PassportApi
import com.bilibili.pure.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun getArticleView(cvid: Long): Result<NoteArticle> {
        return runCatching {
            val response = api.getArticleView(id = cvid)
            if (response.code == 0) {
                response.data ?: throw Exception("笔记不存在")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getArticleView failed", it) }
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

    suspend fun getSpaceSeasons(mid: Long, pageNum: Int = 1): Result<SpaceSeasonsData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceSeasons: mid=$mid pageNum=$pageNum")
        return runCatching {
            val response = api.getSpaceSeasons(mid = mid, pageNum = pageNum)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceSeasons response: code=${response.code} msg=${response.message}")
            if (response.code == 0) response.data ?: throw Exception("No data")
            else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "getSpaceSeasons failed", it) }
    }

    suspend fun getSeasonArchives(mid: Long, seasonId: Long, pageNum: Int = 1, sortReverse: Boolean? = null): Result<SeasonArchiveData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchives: mid=$mid seasonId=$seasonId pageNum=$pageNum sortReverse=$sortReverse")
        return runCatching {
            val response = api.getSeasonArchives(mid = mid, seasonId = seasonId, pageNum = pageNum, sortReverse = sortReverse)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchives response: code=${response.code} msg=${response.message}")
            if (response.code == 0) response.data ?: throw Exception("No data")
            else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "getSeasonArchives failed", it) }
    }

    // 合集视频列表：使用 seasons_archives_list API（已验证无需 WBI 签名，Safari UA 可通过）
    suspend fun getSeasonArchivesViaView(bvid: String): Result<Pair<List<SeasonArchiveItem>, SeasonMeta?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchivesViaView: bvid=$bvid")
        return runCatching {
            val response = api.getVideoInfo(bvid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchivesViaView response: code=${response.code} msg=${response.message}")
            if (response.code != 0) throw Exception(response.message)
            val ugc = response.data?.ugcSeason ?: throw Exception("该视频不属于合集")
            val episodes = ugc.sections?.flatMap { it.episodes ?: emptyList() } ?: emptyList()
            val items = episodes.map { ep ->
                SeasonArchiveItem(
                    bvid = ep.bvid,
                    title = ep.arc?.title ?: ep.title,
                    pic = ep.arc?.pic ?: "",
                    duration = ep.arc?.duration ?: ep.page?.duration ?: 0,
                    pubdate = ep.arc?.pubdate ?: 0,
                    stat = ep.arc?.stat
                )
            }
            val meta = SeasonMeta(
                seasonId = ugc.id,
                name = ugc.title,
                cover = ugc.cover,
                total = items.size,
                description = ugc.intro
            )
            items to meta
        }.onFailure { Log.e(BilibiliApp.TAG, "getSeasonArchivesViaView failed", it) }
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

    suspend fun getPlayUrlDash(bvid: String, cid: Long): Result<PlayUrlInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrlDash: bvid=$bvid cid=$cid fnval=16")
        return runCatching {
            val response = api.getPlayUrlDash(bvid = bvid, cid = cid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrlDash response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val playUrl = response.data ?: throw Exception("No play URL data")
                if (BuildConfig.DEBUG) {
                    val acceptDesc = playUrl.accept_description?.joinToString(", ") ?: "N/A"
                    val acceptQual = playUrl.accept_quality?.joinToString(", ") ?: "N/A"
                    val videoCount = playUrl.dash?.video?.size ?: 0
                    val audioCount = playUrl.dash?.audio?.size ?: 0
                    Log.d(BilibiliApp.TAG, "getPlayUrlDash success: accept=[$acceptQual]($acceptDesc) video=$videoCount audio=$audioCount")
                    playUrl.dash?.video?.forEach { v ->
                        Log.d(BilibiliApp.TAG, "  video: id=${v.id} ${v.width}x${v.height} codec=${v.codecs} bw=${v.bandwidth}")
                    }
                    playUrl.dash?.audio?.forEach { a ->
                        Log.d(BilibiliApp.TAG, "  audio: id=${a.id} codec=${a.codecs} bw=${a.bandwidth}")
                    }
                }
                playUrl
            } else {
                Log.w(BilibiliApp.TAG, "getPlayUrlDash failed: code=${response.code} msg=${response.message}")
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPlayUrlDash exception", it) }
    }

    suspend fun getComments(aid: Long, page: Int = 0, mode: Int = 3): Result<CommentList> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getComments: aid=$aid page=$page mode=$mode")
        return runCatching {
            val response = api.getComments(oid = aid, mode = mode, next = page)
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

    suspend fun likeComment(aid: Long, rpid: Long, like: Boolean): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "likeComment: aid=$aid rpid=$rpid like=$like")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) {
                throw Exception("请先登录")
            }
            val response = api.likeComment(
                oid = aid,
                rpid = rpid,
                action = if (like) 1 else 0,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "likeComment response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                Unit
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "likeComment failed", it) }
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

    suspend fun getNavInfo(): Result<NavInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getNavInfo")
        return runCatching {
            val response = api.getNavInfo()
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getNavInfo response: code=${response.code} face=${response.data?.face}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty nav info")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getNavInfo failed", it) }
    }

    suspend fun getUserCard(mid: Long): Result<UserCardData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserCard: mid=$mid")
        return runCatching {
            val response = api.getUserCard(mid = mid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserCard response: code=${response.code} face=${response.data?.card?.face}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty user card")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getUserCard failed", it) }
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

    suspend fun getCaptcha(): Result<CaptchaData> {
        return runCatching {
            val response = passportApi.getCaptcha()
            Log.d(BilibiliApp.TAG, "getCaptcha response: code=${response.code} type=${response.data?.type} token=${response.data?.token?.take(20)} challenge=${response.data?.geetest?.challenge?.take(20)}")
            if (response.code == 0) response.data ?: throw Exception("Empty captcha")
            else throw Exception(response.message)
        }
    }

    suspend fun sendSmsCode(tel: String, token: String, challenge: String, validate: String, seccode: String): Result<SmsSendData> {
        return runCatching {
            Log.d(BilibiliApp.TAG, "sendSmsCode: tel=$tel token=${token.take(20)} challenge=${challenge.take(20)} validate=${validate.take(30)} seccode=${seccode.take(30)}")
            val response = passportApi.sendSmsCode(tel = tel, token = token, challenge = challenge, validate = validate, seccode = seccode)
            Log.d(BilibiliApp.TAG, "sendSmsCode response: code=${response.code} message=${response.message} data=${response.data}")
            if (response.code == 0) response.data ?: throw Exception("Failed to send SMS")
            else throw Exception(response.message)
        }
    }

    suspend fun smsLoginRaw(tel: String, code: Int, captchaKey: String): Result<Map<String, String>> {
        return runCatching {
            val formBody = okhttp3.FormBody.Builder()
                .add("cid", "86")
                .add("tel", tel)
                .add("code", code.toString())
                .add("source", "main_web")
                .add("captcha_key", captchaKey)
                .add("go_url", "https://www.bilibili.com")
                .add("keep", "1")
                .build()
            val request = okhttp3.Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/login/sms")
                .post(formBody)
                .build()
            withContext(Dispatchers.IO) {
                val response = BilibiliApi.httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                val cookies = mutableMapOf<String, String>()
                response.headers("Set-Cookie").forEach { setCookie ->
                    val part = setCookie.substringBefore(";")
                    val eq = part.indexOf("=")
                    if (eq > 0) cookies[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
                }
                response.close()
                Log.d(BilibiliApp.TAG, "smsLoginRaw code=${response.code} cookies=${cookies.keys} body=$bodyStr")
                val json = com.google.gson.Gson().fromJson(bodyStr, java.util.Map::class.java)
                val rc = (json?.get("code") as? Number)?.toInt() ?: -1
                if (rc != 0) {
                    throw Exception(json?.get("message")?.toString() ?: "登录失败($rc)")
                }
                val hasSession = cookies.containsKey("SESSDATA") &&
                        cookies.containsKey("bili_jct") &&
                        cookies.containsKey("DedeUserID")
                if (!hasSession) {
                    val dataMsg = (json?.get("data") as? java.util.Map<*, *>)?.get("message")?.toString().orEmpty()
                    throw Exception(dataMsg.ifBlank { "本次登录环境存在风险，无法获取登录凭证" })
                }
                cookies
            }
        }
    }

    suspend fun getWebKey(): Result<WebKeyData> {
        return runCatching {
            val response = passportApi.getWebKey()
            if (response.code == 0) response.data ?: throw Exception("Empty key")
            else throw Exception(response.message)
        }
    }

    suspend fun passwordLoginRaw(username: String, password: String, token: String, challenge: String, validate: String, seccode: String): Result<Map<String, String>> {
        return runCatching {
            val formBody = okhttp3.FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("keep", "0")
                .add("token", token)
                .add("challenge", challenge)
                .add("validate", validate)
                .add("seccode", seccode)
                .add("go_url", "https://www.bilibili.com")
                .add("source", "main_web")
                .build()
            val request = okhttp3.Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/login")
                .post(formBody)
                .build()
            val cookies = withContext(Dispatchers.IO) {
                val response = BilibiliApi.httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                val c = mutableMapOf<String, String>()
                response.headers("Set-Cookie").forEach { setCookie ->
                    val part = setCookie.substringBefore(";")
                    val eq = part.indexOf("=")
                    if (eq > 0) c[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
                }
                response.close()
                Log.d(BilibiliApp.TAG, "passwordLoginRaw code=${response.code} cookies=${c.keys} body=$bodyStr")
                val json = com.google.gson.Gson().fromJson(bodyStr, java.util.Map::class.java)
                val rc = (json?.get("code") as? Number)?.toInt() ?: -1
                if (rc != 0) {
                    throw Exception(json?.get("message")?.toString() ?: "登录失败($rc)")
                }
                val hasSession = c.containsKey("SESSDATA") &&
                        c.containsKey("bili_jct") &&
                        c.containsKey("DedeUserID")
                if (!hasSession) {
                    val dataMsg = (json?.get("data") as? java.util.Map<*, *>)?.get("message")?.toString().orEmpty()
                    throw Exception(dataMsg.ifBlank { "本次登录环境存在风险，无法获取登录凭证" })
                }
                c
            }
            Log.d(BilibiliApp.TAG, "passwordLoginRaw cookies: ${cookies.keys}")
            cookies
        }
    }

    suspend fun getSubtitleTracks(aid: Long, cid: Long): Result<List<SubtitleTrack>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSubtitleTracks: aid=$aid cid=$cid")
        return runCatching {
            val response = api.getPlayerInfo(aid = aid, cid = cid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSubtitleTracks response: code=${response.code}")
            if (response.code == 0) {
                val subtitles = response.data?.subtitle?.subtitles ?: emptyList()
                subtitles.map { track ->
                    SubtitleTrack(
                        id = track.id,
                        lan = track.lan,
                        lanDoc = track.lan_doc,
                        subtitleUrl = track.subtitleUrl.let { url ->
                            if (url.startsWith("//")) "https:$url" else url
                        },
                        type = track.type,
                        aiStatus = track.aiStatus
                    )
                }.filter { it.subtitleUrl.isNotBlank() }
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getSubtitleTracks failed", it) }
    }

    suspend fun getSubtitleContent(url: String): Result<SubtitleBody> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSubtitleContent: url=${url.take(80)}")
        return runCatching {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            val responseBody = withContext(Dispatchers.IO) {
                BilibiliApi.httpClient.newCall(request).execute().use { response ->
                    response.body?.string() ?: throw Exception("Empty response")
                }
            }
            val body = com.google.gson.Gson().fromJson(responseBody, SubtitleBody::class.java)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSubtitleContent: ${body.body.size} cues")
            body
        }.onFailure { Log.e(BilibiliApp.TAG, "getSubtitleContent failed", it) }
    }
}
