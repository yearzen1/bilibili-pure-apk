package com.bilibili.pure.ui.detail

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilibili.pure.data.model.NoteArticle
import com.bilibili.pure.data.repository.BilibiliRepository

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun sanitizeNoteContent(html: String): String {
    var out = html
    out = out.replace(Regex("(?is)<script[\\s\\S]*?</script>"), "")
    out = out.replace(Regex("(?is)<style[\\s\\S]*?</style>"), "")
    // 把懒加载 data-src 提升为 src（兼容两种属性顺序），并丢弃占位 src
    out = out.replace(
        Regex("(?i)(<img[^>]*?)\\ssrc=\"[^\"]*\"([^>]*?)data-src=\"([^\"]*)\""),
        "$1 src=\"$3\"$2"
    )
    out = out.replace(
        Regex("(?i)(<img[^>]*?)data-src=\"([^\"]*)\"([^>]*?)\\ssrc=\"[^\"]*\""),
        "$1 src=\"$2\"$3"
    )
    // 去掉内联固定宽度/样式，避免提前换行
    out = out.replace(Regex("(?i)\\sstyle=\"[^\"]*\""), " ")
    out = out.replace(Regex("(?i)\\swidth=\"[^\"]*\""), " ")
    out = out.replace(Regex("(?i)\\sheight=\"[^\"]*\""), " ")
    return out
}

private class NoteJsBridge(private val onOpenPre: (String) -> Unit) {
    @JavascriptInterface
    fun openPre(text: String) {
        onOpenPre(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    cvid: String,
    onBack: () -> Unit
) {
    val repository = remember { BilibiliRepository() }
    var state by remember { mutableStateOf<NoteLoadState>(NoteLoadState.Loading) }
    var fullscreenPre by remember { mutableStateOf<String?>(null) }

    val isDark = isSystemInDarkTheme()
    val bgHex = "#%06X".format(0xFFFFFF and MaterialTheme.colorScheme.surface.toArgb())
    val fgHex = "#%06X".format(0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val bgArgb = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(cvid) {
        val id = cvid.toLongOrNull()
        if (id == null || id == 0L) {
            state = NoteLoadState.Error("无效的笔记ID")
            return@LaunchedEffect
        }
        repository.getArticleView(id)
            .onSuccess { state = NoteLoadState.Success(it) }
            .onFailure { state = NoteLoadState.Error(it.message ?: "加载失败") }
    }

    BackHandler(enabled = true) { onBack() }

    val title = (state as? NoteLoadState.Success)?.article?.title?.takeIf { it.isNotBlank() } ?: "笔记"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is NoteLoadState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is NoteLoadState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is NoteLoadState.Success -> {
                val html = buildNoteHtml(s.article, bgHex, fgHex)
                val onOpenPre: (String) -> Unit = { fullscreenPre = it }
                key(isDark) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.defaultTextEncodingName = "utf-8"
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = DESKTOP_UA
                            setBackgroundColor(bgArgb)
                            addJavascriptInterface(NoteJsBridge(onOpenPre), "NoteApp")
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL(
                                "https://www.bilibili.com",
                                html,
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    },
                            update = { /* no-op */ }
                )
                }
            }
        }
    }

    fullscreenPre?.let { code ->
        Dialog(
            onDismissRequest = { fullscreenPre = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                            settings.defaultTextEncodingName = "utf-8"
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            setBackgroundColor(bgArgb)
                            webViewClient = WebViewClient()
                            val overlayHtml = buildString {
                                append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
                                append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                                append("<style>html,body{margin:0;padding:0;background:$bgHex;color:$fgHex;}")
                                append("pre{white-space:pre;word-break:normal;font-family:monospace;font-size:14px;line-height:1.5;}</style>")
                                append("</head><body><pre>")
                                append(code.escapeHtml())
                                append("</pre></body></html>")
                            }
                            loadDataWithBaseURL(
                                "https://www.bilibili.com",
                                overlayHtml,
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    },
                    update = { /* no-op */ }
                )
                IconButton(
                    onClick = { fullscreenPre = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun buildNoteHtml(article: NoteArticle, bgHex: String, fgHex: String): String {
    val sb = StringBuilder()
    sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
    sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
    sb.append("<style>")
    sb.append("html, body { width:100% !important; max-width:100% !important; min-width:0 !important; margin:0 !important; padding:0 !important; overflow-x:hidden !important; background:$bgHex; color:$fgHex; }")
    sb.append("* { max-width:100% !important; min-width:0 !important; box-sizing:border-box !important; }")
    sb.append("div, p, section, article, figure, figcaption, table, ul, ol, blockquote, h1, h2, h3, h4, h5, h6, pre, code { width:auto !important; max-width:100% !important; min-width:0 !important; float:none !important; margin-left:0 !important; margin-right:0 !important; white-space:normal !important; display:block !important; }")
    sb.append("pre { white-space:pre !important; overflow-x:auto !important; }")
    sb.append("code { white-space:pre-wrap !important; word-break:break-word !important; overflow-wrap:break-word !important; }")
    sb.append("img { display:block !important; width:100% !important; max-width:100% !important; height:auto !important; }")
    sb.append("body { padding:16px; font-size:16px; line-height:1.7; font-family:-apple-system,Segoe UI,Roboto,sans-serif; word-break:break-word; }")
    sb.append(".banner { width:100% !important; border-radius:8px; margin-bottom:12px; }")
    sb.append(".title { font-size:22px; font-weight:700; margin:0 0 12px; }")
    sb.append(".author { display:flex !important; align-items:center; margin:0 0 16px; }")
    sb.append(".author img { width:40px !important; height:40px !important; border-radius:50%; margin-right:10px; }")
    sb.append(".author span { font-size:15px; color:$fgHex; }")
    sb.append(".summary { color:$fgHex; opacity:0.6; font-size:14px; margin:0 0 16px; }")
    sb.append("a { color:#5ab0f6; }")
    sb.append("</style></head><body>")
    article.bannerUrl?.let { sb.append("<img class='banner' src='$it'>") }
    sb.append("<div class='title'>").append(article.title.escapeHtml()).append("</div>")
    article.author?.let {
        sb.append("<div class='author'><img src='${it.face}'><span>")
            .append(it.name.escapeHtml())
            .append("</span></div>")
    }
    article.summary?.let { sb.append("<div class='summary'>").append(it.escapeHtml()).append("</div>") }
    sb.append(sanitizeNoteContent(article.content))
    sb.append("<script>document.querySelectorAll('pre').forEach(function(el){el.style.cursor='pointer';el.addEventListener('click',function(e){e.stopPropagation();NoteApp.openPre(el.textContent);});});</script>")
    sb.append("</body></html>")
    return sb.toString()
}

private sealed class NoteLoadState {
    object Loading : NoteLoadState()
    data class Success(val article: NoteArticle) : NoteLoadState()
    data class Error(val message: String) : NoteLoadState()
}
