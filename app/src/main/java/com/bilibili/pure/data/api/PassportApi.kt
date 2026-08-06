package com.bilibili.pure.data.api

import com.bilibili.pure.data.model.ApiResponse
import com.bilibili.pure.data.model.CaptchaData
import com.bilibili.pure.data.model.QRLoginData
import com.bilibili.pure.data.model.QRPollData
import com.bilibili.pure.data.model.SmsSendData
import com.bilibili.pure.data.model.WebKeyData
import com.bilibili.pure.data.model.WebLoginData
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PassportApi {

    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQRCode(): ApiResponse<QRLoginData>

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQRCode(@Query("qrcode_key") key: String): ApiResponse<QRPollData>

    @GET("x/passport-login/captcha")
    suspend fun getCaptcha(@Query("source") source: String = "main_web"): ApiResponse<CaptchaData>

    @FormUrlEncoded
    @POST("x/passport-login/web/sms/send")
    suspend fun sendSmsCode(
        @Field("cid") cid: Int = 86,
        @Field("tel") tel: String,
        @Field("source") source: String = "main_web",
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String
    ): ApiResponse<SmsSendData>

    @FormUrlEncoded
    @POST("x/passport-login/web/login/sms")
    suspend fun smsLogin(
        @Field("cid") cid: Int = 86,
        @Field("tel") tel: String,
        @Field("code") code: Int,
        @Field("source") source: String = "main_web",
        @Field("captcha_key") captchaKey: String,
        @Field("go_url") goUrl: String = "https://www.bilibili.com",
        @Field("keep") keep: Int = 1
    ): ApiResponse<WebLoginData>

    @GET("x/passport-login/web/key")
    suspend fun getWebKey(): ApiResponse<WebKeyData>

    @FormUrlEncoded
    @POST("x/passport-login/web/login")
    suspend fun passwordLogin(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("keep") keep: Int = 0,
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String,
        @Field("go_url") goUrl: String = "https://www.bilibili.com",
        @Field("source") source: String = "main_web"
    ): ApiResponse<WebLoginData>

    companion object {
        fun create(): PassportApi {
            return Retrofit.Builder()
                .baseUrl("https://passport.bilibili.com/")
                .client(BilibiliApi.httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PassportApi::class.java)
        }
    }
}
