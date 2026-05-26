package com.bilibili.pure.ui.login

import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bilibili.pure.data.api.BilibiliApi

@Composable
fun PasswordLoginContent(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val succeeded = remember { mutableStateOf(false) }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            if (!succeeded.value) {
                                if (checkCookiesAndSave(context, url)) {
                                    succeeded.value = true
                                    onLoginSuccess()
                                }
                            }
                        }
                    }

                    loadUrl("https://passport.bilibili.com/login")
                }.also { webView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun checkCookiesAndSave(context: android.content.Context, pageUrl: String?): Boolean {
    val urlsToTry = listOfNotNull(
        pageUrl?.let { if (it.startsWith("http")) it else null },
        "https://www.bilibili.com",
        "https://passport.bilibili.com"
    )

    val map = mutableMapOf<String, String>()
    for (url in urlsToTry) {
        val cookies = CookieManager.getInstance().getCookie(url) ?: continue
        cookies.split("; ").forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) {
                val key = entry.substring(0, idx)
                val value = entry.substring(idx + 1)
                if (key !in map) map[key] = value
            }
        }
    }

    val sessdata = map["SESSDATA"]
    val biliJct = map["bili_jct"]
    val dedeUserId = map["DedeUserID"]

    if (!sessdata.isNullOrEmpty() && !biliJct.isNullOrEmpty() && !dedeUserId.isNullOrEmpty()) {
        BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
        context.getSharedPreferences("bili_prefs", 0)
            .edit()
            .putString("sessdata", sessdata)
            .putString("bili_jct", biliJct)
            .putString("dede_userid", dedeUserId)
            .apply()
        return true
    }
    return false
}
