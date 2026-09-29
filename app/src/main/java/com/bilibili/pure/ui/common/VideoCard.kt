package com.bilibili.pure.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.util.fixPic
import com.bilibili.pure.util.formatCount
import com.bilibili.pure.util.formatPubdateCompact

data class VideoCardSpec(
    val author: Boolean = true,
    val play: Boolean = true,
    val duration: Boolean = true,
    val date: Boolean = true,
    val selectable: Boolean = false,
    val compact: Boolean = false,
    val coverWidth: Dp = 140.dp,
    val coverHeight: Dp = 88.dp,
    val contentSpacing: Dp = 12.dp
)

data class MetaSegment(
    val text: String,
    val shrinkable: Boolean
)

fun buildMetaSegments(
    spec: VideoCardSpec,
    playCount: Long,
    durationText: String,
    pubdate: Long,
    nowSeconds: Long = System.currentTimeMillis() / 1000
): List<MetaSegment> {
    val segments = mutableListOf<MetaSegment>()
    if (spec.play) segments += MetaSegment("${formatCount(playCount)}播放", shrinkable = true)
    if (spec.duration && durationText.isNotBlank()) {
        segments += MetaSegment(durationText.trim(), shrinkable = false)
    }
    if (spec.date && pubdate > 0) {
        segments += MetaSegment(formatPubdateCompact(pubdate, nowSeconds), shrinkable = false)
    }
    return segments
}

@Composable
fun VideoMetaLine(
    spec: VideoCardSpec,
    playCount: Long,
    durationText: String,
    pubdate: Long,
    modifier: Modifier = Modifier
) {
    val segments = buildMetaSegments(spec, playCount, durationText, pubdate)
    if (segments.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            val base = if (segment.shrinkable) {
                Modifier.weight(1f, fill = false)
            } else {
                Modifier
            }
            Text(
                text = segment.text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = if (segment.shrinkable) TextOverflow.Ellipsis
                else TextOverflow.Clip,
                modifier = base
            )
        }
    }
}

@Composable
fun VideoCard(
    spec: VideoCardSpec,
    coverUrl: String,
    title: String,
    author: String = "",
    playCount: Long = 0,
    durationText: String = "",
    pubdate: Long = 0,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    Row(modifier = modifier.padding(8.dp)) {
        AsyncImage(
            model = fixPic(coverUrl),
            contentDescription = title,
            modifier = Modifier
                .width(spec.coverWidth)
                .height(spec.coverHeight),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(spec.contentSpacing))
        Column(modifier = Modifier.weight(1f)) {
            if (spec.selectable) {
                SelectionContainer {
                    TitleText(title, spec)
                }
            } else {
                TitleText(title, spec)
            }
            if (spec.author && author.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val authorText: @Composable () -> Unit = {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (spec.selectable) SelectionContainer { authorText() } else authorText()
            }
            Spacer(modifier = Modifier.height(4.dp))
            VideoMetaLine(
                spec = spec,
                playCount = playCount,
                durationText = durationText,
                pubdate = pubdate
            )
            footer()
        }
    }
}

@Composable
private fun TitleText(title: String, spec: VideoCardSpec) {
    Text(
        text = title,
        style = if (spec.compact) MaterialTheme.typography.bodyMedium
        else MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
