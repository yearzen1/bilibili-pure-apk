package com.bilibili.pure.ui.profile

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import com.bilibili.pure.data.api.BilibiliApi

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val uid: String = ""
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bili_prefs", 0)

    fun getState(): ProfileUiState {
        val sessdata = prefs.getString("sessdata", null)
        val uid = prefs.getString("dede_userid", "")
        return ProfileUiState(
            isLoggedIn = !sessdata.isNullOrEmpty(),
            uid = uid ?: ""
        )
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        prefs.edit()
            .remove("sessdata")
            .remove("bili_jct")
            .remove("dede_userid")
            .apply()
        BilibiliApi.loginCookies = ""
        BilibiliApi.biliJct = ""
    }
}
