package com.bilibili.pure.data.api

import android.os.Build
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.model.*
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

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

    companion object {
        private const val BASE_URL = "https://api.bilibili.com/"
        lateinit var buvid3: String
        var loginCookies: String = ""

        fun setLoginCookies(sessdata: String, biliJct: String, dedeUserId: String) {
            loginCookies = "SESSDATA=$sessdata; bili_jct=$biliJct; DedeUserID=$dedeUserId"
        }

        val httpClient: OkHttpClient by lazy {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
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
                    val isCdn = url.contains("bilivideo.com") || url.contains("upos") || url.contains("hdslb.com")
                    val builder = orig.newBuilder()
                        .header("Referer", "https://www.bilibili.com/")
                    if (isCdn) {
                        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    } else {
                        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .header("Origin", "https://www.bilibili.com")
                            .header("Cookie", buildString {
                                append("buvid3=$buvid3; buvid4=$buvid3")
                                if (loginCookies.isNotEmpty()) {
                                    append("; $loginCookies")
                                }
                            })
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                            .header("sec-ch-ua", "\"Google Chrome\";v=\"120\", \"Not?A_Brand\";v=\"8\"")
                            .header("sec-ch-ua-mobile", "?1")
                            .header("sec-ch-ua-platform", "\"Android\"")
                            .header("Sec-Fetch-Dest", "empty")
                            .header("Sec-Fetch-Mode", "cors")
                            .header("Sec-Fetch-Site", "same-site")
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
