package com.bilibili.pure.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.EmoteInfo

private fun fixPic(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
    else -> url
}

@Composable
fun CommentText(
    message: String,
    emote: Map<String, EmoteInfo>?,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    // 仅当评论确实含可渲染表情时才走 BasicText（支持内联图），
    // 普通评论保持 Material3 Text，颜色/选中等行为与改造前完全一致
    val hasEmote = !emote.isNullOrEmpty() && Regex("\\[[^\\]]+\\]").containsMatchIn(message)
    if (!hasEmote) {
        Text(text = message, style = style, modifier = modifier)
        return
    }
    val emoteMap = emote!!

    val hasLargeEmote = emoteMap.values.any { it.meta?.size == 2 }
    val resolvedStyle = style.copy(
        color = if (style.color == Color.Unspecified) LocalContentColor.current else style.color,
        lineHeight = if (hasLargeEmote) 3.0.em else style.lineHeight
    )
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        Regex("\\[[^\\]]+\\]").findAll(message).forEach { match ->
            val info = emoteMap[match.value]
            if (info == null) {
                append(message.substring(lastIndex, match.range.last + 1))
                lastIndex = match.range.last + 1
                return@forEach
            }
            append(message.substring(lastIndex, match.range.first))
            val tag = "emote_${match.value}"
            appendInlineContent(tag, match.value)
            val em = if (info.meta?.size == 2) 2.2.em else 1.1.em
            inlineContent[tag] = InlineTextContent(
                placeholder = Placeholder(
                    width = em,
                    height = em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) { _ ->
                AsyncImage(
                    model = fixPic(info.url),
                    contentDescription = info.text,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            lastIndex = match.range.last + 1
        }
        append(message.substring(lastIndex))
    }

    BasicText(
        text = annotated,
        style = resolvedStyle,
        inlineContent = inlineContent,
        modifier = modifier
    )
}
