package com.bilibili.pure.ui.login

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.data.api.BilibiliApi
import com.bilibili.pure.data.repository.BilibiliRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class QRReady(val url: String, val key: String) : LoginUiState()
    data object Scanned : LoginUiState()
    data object Expired : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BilibiliRepository()

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    init {
        generateQR()
    }

    private fun generateQR() {
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            repository.generateQRCode()
                .onSuccess { data ->
                    _state.value = LoginUiState.QRReady(data.url, data.qrcodeKey)
                    startPolling(data.qrcodeKey)
                }
                .onFailure { e ->
                    _state.value = LoginUiState.Error(e.message ?: "生成二维码失败")
                }
        }
    }

    private fun startPolling(key: String) {
        viewModelScope.launch {
            while (true) {
                delay(1500)
                repository.pollQRCode(key)
                    .onSuccess { (statusCode, pollData) ->
                        when (statusCode) {
                            0 -> {
                                val url = pollData?.url
                                if (url != null && extractAndSaveCookies(url)) {
                                    if (BuildConfig.DEBUG) Log.d("LoginVM", "Login success, cookies saved")
                                    _state.value = LoginUiState.Success
                                    _loginSuccess.value = true
                                } else {
                                    _state.value = LoginUiState.Error("登录成功但无法获取凭证")
                                }
                                return@launch
                            }
                            86038 -> {
                                _state.value = LoginUiState.Expired
                                return@launch
                            }
                            86090 -> {
                                _state.value = LoginUiState.Scanned
                            }
                        }
                    }
                    .onFailure { e ->
                        if (BuildConfig.DEBUG) Log.e("LoginVM", "Poll failed", e)
                    }
            }
        }
    }

    private fun extractAndSaveCookies(url: String): Boolean {
        return try {
            val queryUri = Uri.parse(url)
            val sessdata = queryUri.getQueryParameter("SESSDATA")
            val biliJct = queryUri.getQueryParameter("bili_jct")
            val dedeUserId = queryUri.getQueryParameter("DedeUserID")

            if (!sessdata.isNullOrEmpty() && !biliJct.isNullOrEmpty() && !dedeUserId.isNullOrEmpty()) {
                BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
                val prefs = getApplication<Application>().getSharedPreferences("bili_prefs", 0)
                prefs.edit()
                    .putString("sessdata", sessdata)
                    .putString("bili_jct", biliJct)
                    .putString("dede_userid", dedeUserId)
                    .apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("LoginVM", "Failed to extract cookies", e)
            false
        }
    }

    fun retry() {
        generateQR()
    }
}
