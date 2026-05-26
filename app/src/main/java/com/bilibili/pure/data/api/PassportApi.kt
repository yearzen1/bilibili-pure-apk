package com.bilibili.pure.data.api

import com.bilibili.pure.data.model.ApiResponse
import com.bilibili.pure.data.model.QRLoginData
import com.bilibili.pure.data.model.QRPollData
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface PassportApi {

    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQRCode(): ApiResponse<QRLoginData>

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQRCode(@Query("qrcode_key") key: String): ApiResponse<QRPollData>

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
