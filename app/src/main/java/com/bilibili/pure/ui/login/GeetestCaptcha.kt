package com.bilibili.pure.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

data class GeetestResult(
    val challenge: String,
    val validate: String,
    val seccode: String
)

private const val TAG = "BiliPure"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestCaptcha(
    gt: String,
    challenge: String,
    onResult: (GeetestResult) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var resultHandled by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "请完成验证",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        Log.d(TAG, "GeetestCaptcha: creating WebView, gt=$gt challenge=${challenge.take(20)}")
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                    Log.d(TAG, "Geetest JS: ${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                                    return true
                                }
                                override fun onJsPrompt(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    defaultValue: String?,
                                    result: JsPromptResult?
                                ): Boolean {
                                    Log.d(TAG, "Geetest onJsPrompt: $message")
                                    if (message != null && !resultHandled) {
                                        val colonIndex = message.indexOf(':')
                                        if (colonIndex > 0) {
                                            val type = message.substring(0, colonIndex)
                                            val jsonStr = message.substring(colonIndex + 1)
                                            Log.d(TAG, "Geetest type=$type json=${jsonStr.take(100)}")
                                            when (type) {
                                                "success" -> {
                                                    try {
                                                        val json = JSONObject(jsonStr)
                                                        val gChallenge = json.optString("geetest_challenge", "")
                                                        val validate = json.optString("geetest_validate", "")
                                                        val seccode = json.optString("geetest_seccode", "")
                                                        if (gChallenge.isNotEmpty() && validate.isNotEmpty() && seccode.isNotEmpty()) {
                                                            resultHandled = true
                                                            Log.d(TAG, "Geetest SUCCESS: challenge=${gChallenge.take(20)} validate=${validate.take(30)} seccode=${seccode.take(30)}")
                                                            onResult(GeetestResult(gChallenge, validate, seccode))
                                                        } else {
                                                            Log.e(TAG, "Geetest: empty geetest fields $jsonStr")
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Geetest parse error: $jsonStr", e)
                                                    }
                                                }
                                                "error" -> {
                                                    Log.e(TAG, "Geetest error from JS: $jsonStr")
                                                }
                                                "close" -> {
                                                    Log.d(TAG, "Geetest closed by user")
                                                }
                                            }
                                        }
                                    }
                                    result?.confirm("")
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    Log.d(TAG, "Geetest page started: $url")
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    Log.d(TAG, "Geetest page finished: $url")
                                }
                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    Log.e(TAG, "Geetest WebView error: code=$errorCode desc=$description url=$failingUrl")
                                }
                            }

                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
                                <style>
                                    * { margin: 0; padding: 0; box-sizing: border-box; }
                                    body { background: #f5f5f5; display: flex; justify-content: center; align-items: flex-start; padding-top: 20px; min-height: 300px; }
                                    #captcha-box { background: white; border-radius: 8px; padding: 10px; width: 100%; max-width: 340px; }
                                </style>
                                </head>
                                <body>
                                <div id="captcha-box"></div>
                                <script src="https://static.geetest.com/static/tools/gt.js"></script>
                                <script>
                                    function R(type, data) {
                                        try {
                                            prompt(type + ':' + JSON.stringify(data));
                                        } catch(e) {
                                            console.log('R error: ' + e.message);
                                        }
                                    }
                                    try {
                                        console.log('Geetest: init, gt=$gt challenge=${challenge.take(20)}');
                                        initGeetest({
                                            gt: "$gt",
                                            challenge: "$challenge",
                                            lang: "zh-cn",
                                            width: "100%",
                                            product: "float",
                                            offline: false
                                        }, function(captchaObj) {
                                            console.log('Geetest: captchaObj created');
                                            captchaObj.appendTo("#captcha-box");
                                            captchaObj.onSuccess(function() {
                                                console.log('Geetest: onSuccess');
                                                try {
                                                    var r = captchaObj.getValidate();
                                                    console.log('Geetest: validate=' + JSON.stringify(r));
                                                    R("success", r);
                                                } catch(e) {
                                                    console.log('Geetest: getValidate error: ' + e.message);
                                                    R("error", {"msg": e.message});
                                                }
                                            });
                                            captchaObj.onError(function(e) {
                                                console.log('Geetest: onError: ' + JSON.stringify(e));
                                                R("error", e);
                                            });
                                            captchaObj.onClose(function() {
                                                console.log('Geetest: onClose');
                                                R("close", {});
                                            });
                                        });
                                    } catch(e) {
                                        console.log('Geetest init error: ' + e.message);
                                        R("error", {"msg": e.message});
                                    }
                                </script>
                                </body>
                                </html>
                            """.trimIndent()

                            loadDataWithBaseURL("https://www.bilibili.com", html, "text/html", "UTF-8", null)
                            Log.d(TAG, "Geetest: loadDataWithBaseURL called")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
