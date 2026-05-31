package com.bilibili.pure.ui.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bilibili.pure.data.model.UserVideoItem
import com.bilibili.pure.ui.search.formatCount

internal fun fixPic(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
    else -> url
}

@Composable
internal fun ChannelVideoCard(video: UserVideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = fixPic(video.pic),
                contentDescription = video.title,
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${formatCount(video.playCount)}播放",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatCount(video.danmakuCount)}弹幕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
