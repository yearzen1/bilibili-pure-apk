package com.bilibili.pure.data.api

import android.os.Build
import android.util.Log
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.model.*
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface BilibiliApi {

    @GET("x/web-interface/search/type")
    suspend fun search(
        @Query("search_type") searchType: String = "video",
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("order") order: String? = null
    ): ApiResponse<SearchResult>

    @GET("x/web-interface/view")
    suspend fun getVideoInfo(@Query("bvid") bvid: String): ApiResponse<VideoInfo>

    @GET("x/v2/reply/main")
    suspend fun getComments(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("mode") mode: Int = 3,
        @Query("ps") pageSize: Int = 20,
        @Query("next") next: Int = 0
    ): ApiResponse<CommentList>

    @GET("x/v2/reply/reply")
    suspend fun getReplies(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("root") root: Long,
        @Query("ps") pageSize: Int = 20,
        @Query("pn") pn: Int = 1
    ): ApiResponse<CommentList>

    @FormUrlEncoded
    @POST("x/v2/history/report")
    suspend fun reportProgress(
        @Field("aid") aid: Long,
        @Field("cid") cid: Long,
        @Field("progress") progress: Long,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @GET("x/v2/history")
    suspend fun getHistory(
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 20
    ): ApiResponse<List<HistoryItem>>

    @GET("x/space/wbi/arc/search")
    suspend fun getUserVideos(
        @Query("mid") mid: Long,
        @Query("keyword") keyword: String? = null,
        @Query("ps") pageSize: Int = 30,
        @Query("pn") page: Int = 1
    ): ApiResponse<UserSpaceData>

    @GET("x/player/playurl")
    suspend fun getPlayUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 1,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "android"
    ): ApiResponse<PlayUrlInfo>

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getFavFolders(
        @Query("up_mid") upMid: Long,
        @Query("type") type: Int = 0
    ): ApiResponse<FavFolderList>

    @GET("x/v3/fav/resource/list")
    suspend fun getFavResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web"
    ): ApiResponse<FavResourceList>

    @GET("x/v2/fav/video/favoured")
    suspend fun getFavoured(
        @Query("aid") aid: Long
    ): ApiResponse<FavouredData>

    @FormUrlEncoded
    @POST("x/v3/fav/resource/deal")
    suspend fun dealFavResource(
        @Field("rid") rid: Long,
        @Field("type") type: Int = 2,
        @Field("add_media_ids") addMediaIds: String,
        @Field("del_media_ids") delMediaIds: String,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/relation/modify")
    suspend fun modifyRelation(
        @Field("fid") fid: Long,
        @Field("act") act: Int,
        @Field("re_src") reSrc: Int = 14,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @GET("x/relation/followings")
    suspend fun getFollowings(
        @Query("vmid") vmid: Long,
        @Query("ps") pageSize: Int = 50,
        @Query("pn") page: Int = 1
    ): ApiResponse<FollowingListData>

    @GET("x/relation/stat")
    suspend fun getRelationStat(@Query("vmid") vmid: Long): ApiResponse<RelationStat>

    @GET("x/relation")
    suspend fun getRelation(@Query("fid") fid: Long): ApiResponse<RelationData>

    @GET("x/space/acc/info")
    suspend fun getSpaceAccInfo(@Query("mid") mid: Long): ApiResponse<SpaceAccInfo>

    companion object {
        private const val BASE_URL = "https://api.bilibili.com/"
        lateinit var buvid3: String
        var loginCookies: String = ""
        var biliJct: String = ""

        private var wbiImgKey: String? = null
        private var wbiSubKey: String? = null
        private val wbiFetchedAt = AtomicLong(0L)
        private val wbiTtlMs = 24 * 60 * 60 * 1000L

        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )

        fun setLoginCookies(sessdata: String, biliJct: String, dedeUserId: String) {
            loginCookies = "SESSDATA=$sessdata; bili_jct=$biliJct; DedeUserID=$dedeUserId"
            this.biliJct = biliJct
        }

        fun parseSelfMid(): Long? {
            val patterns = listOf("DedeUserID=", "DedeUserID=")
            for (pattern in patterns) {
                try {
                    loginCookies.split(";").firstOrNull { it.trim().startsWith(pattern) }
                        ?.substringAfter(pattern)?.trim()?.toLongOrNull()?.let { return it }
                } catch (_: Exception) {}
            }
            return null
        }

        private fun fetchWbiKeys(): Pair<String, String>? {
            return try {
                val client = OkHttpClient.Builder()
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val orig = chain.request()
                        val builder = orig.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.bilibili.com/")
                            .header("Cookie", "buvid3=$buvid3${if (loginCookies.isNotEmpty()) "; $loginCookies" else ""}")
                        chain.proceed(builder.build())
                    }
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("${BASE_URL}x/web-interface/nav")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (body != null) {
                    val navResp = com.google.gson.Gson().fromJson(body, java.util.Map::class.java)
                    val data = navResp["data"] as? java.util.Map<*, *>
                    val wbiImg = data?.get("wbi_img") as? java.util.Map<*, *>
                    val imgUrl = wbiImg?.get("img_url") as? String
                    val subUrl = wbiImg?.get("sub_url") as? String
                    if (imgUrl != null && subUrl != null) {
                        val imgKey = imgUrl.substringAfterLast("/").removeSuffix(".png")
                        val subKey = subUrl.substringAfterLast("/").removeSuffix(".png")
                        Pair(imgKey, subKey)
                    } else {
                        if (BuildConfig.DEBUG) Log.w(BilibiliApp.TAG, "WBI keys not found in nav response: $body")
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(BilibiliApp.TAG, "fetchWbiKeys failed", e)
                null
            }
        }

        private fun getWbiKeys(): Pair<String, String>? {
            val now = System.currentTimeMillis()
            if (now - wbiFetchedAt.get() > wbiTtlMs || wbiImgKey == null || wbiSubKey == null) {
                val keys = fetchWbiKeys()
                if (keys != null) {
                    wbiImgKey = keys.first
                    wbiSubKey = keys.second
                    wbiFetchedAt.set(now)
                    if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "WBI keys refreshed: img=$wbiImgKey sub=$wbiSubKey")
                }
            }
            return if (wbiImgKey != null && wbiSubKey != null) {
                Pair(wbiImgKey!!, wbiSubKey!!)
            } else {
                null
            }
        }

        private fun encodeUriComponent(s: String): String {
            val sb = StringBuilder()
            for (c in s) {
                when (c) {
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> sb.append(c)
                    ' ' -> sb.append("%20")
                    else -> {
                        for (b in c.toString().encodeToByteArray()) {
                            sb.append('%')
                            sb.append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
                        }
                    }
                }
            }
            return sb.toString()
        }

        private fun wbiSign(params: Map<String, String>): String? {
            val keys = getWbiKeys() ?: return null
            val rawKey = keys.first + keys.second
            val mixinKey = MIXIN_KEY_ENC_TAB.map { rawKey[it] }.joinToString("").take(32)

            val mutableParams = params.toMutableMap()
            val wts = (System.currentTimeMillis() / 1000L).toString()
            mutableParams["wts"] = wts

            val sortedKeys = mutableParams.keys.sorted()
            val queryParts = sortedKeys.joinToString("&") { key ->
                val encodedKey = encodeUriComponent(key)
                var encodedValue = encodeUriComponent(mutableParams[key] ?: "")
                encodedValue = encodedValue.replace("!", "")
                    .replace("'", "")
                    .replace("(", "")
                    .replace(")", "")
                    .replace("*", "")
                "$encodedKey=$encodedValue"
            }

            val signStr = queryParts + mixinKey
            val md5 = MessageDigest.getInstance("MD5")
            val wRid = md5.digest(signStr.encodeToByteArray()).joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
            return "$queryParts&w_rid=$wRid"
        }

        val httpClient: OkHttpClient by lazy {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                        else HttpLoggingInterceptor.Level.NONE
            }

            OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectionSpecs(listOf(
                    ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .supportsTlsExtensions(true)
                        .build()
                ))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val orig = chain.request()
                    val url = orig.url.toString()
                    val isCdn = !url.contains("api.bilibili.com")
                    val isSpaceApi = !isCdn && url.contains("/x/space/") && !url.contains("acc/info")
                    val spaceOrigin = "https://space.bilibili.com"
                    val apiOrigin = "https://www.bilibili.com"
                    val builder = orig.newBuilder()
                        .header("Referer", if (isSpaceApi) "$spaceOrigin/" else "$apiOrigin/")
                    if (isCdn) {
                        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    } else {
                        builder.header("User-Agent", if (isSpaceApi) "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" else "Mozilla/5.0 (Linux; Android ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .header("Origin", if (isSpaceApi) spaceOrigin else apiOrigin)
                            .header("Cookie", buildString {
                                append("buvid3=$buvid3; buvid4=$buvid3")
                                if (loginCookies.isNotEmpty()) {
                                    append("; $loginCookies")
                                }
                            })
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                            .header("sec-ch-ua", "\"Google Chrome\";v=\"120\", \"Not?A_Brand\";v=\"8\"")
                            .header("sec-ch-ua-mobile", if (isSpaceApi) "?0" else "?1")
                            .header("sec-ch-ua-platform", if (isSpaceApi) "\"Windows\"" else "\"Android\"")
                            .header("Sec-Fetch-Dest", "empty")
                            .header("Sec-Fetch-Mode", "cors")
                            .header("Sec-Fetch-Site", "same-site")
                    }
                    // WBI signing for endpoints that require w_rid/wts
                    if (!isCdn && (url.contains("/wbi/") || url.contains("search/type"))) {
                        val httpUrl = url.toHttpUrlOrNull()
                        if (httpUrl != null) {
                            val params = mutableMapOf<String, String>()
                            for (i in 0 until httpUrl.querySize) {
                                httpUrl.queryParameterName(i)?.let { name ->
                                    httpUrl.queryParameterValue(i)?.let { value ->
                                        params[name] = value
                                    }
                                }
                            }
                            val wbiResult = wbiSign(params)
                            if (wbiResult != null) {
                                val newUrl = httpUrl.newBuilder()
                                    .encodedQuery(wbiResult)
                                    .build()
                                builder.url(newUrl)
                            }
                        }
                    }
                    chain.proceed(builder.build())
                }
                .addInterceptor(logging)
                .build()
        }

        fun create(): BilibiliApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BilibiliApi::class.java)
        }
    }
}
