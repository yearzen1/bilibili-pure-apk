package com.bilibili.pure.ui.profile

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.model.NavInfo
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val uid: String = ""
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bili_prefs", 0)
    private val repository = BilibiliRepository()

    private val _navInfo = MutableStateFlow<NavInfo?>(null)
    val navInfo: StateFlow<NavInfo?> = _navInfo.asStateFlow()

    init {
        val sessdata = prefs.getString("sessdata", null)
        if (!sessdata.isNullOrEmpty()) {
            viewModelScope.launch {
                repository.getNavInfo()
                    .onSuccess { _navInfo.value = it.copy(face = it.face.replace("http://", "https://")) }
            }
        }
    }

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
        _navInfo.value = null
    }
}
