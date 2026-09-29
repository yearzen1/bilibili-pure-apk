package com.bilibili.pure.ui.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilibili.pure.data.model.UserVideoItem
import com.bilibili.pure.ui.common.VideoCard
import com.bilibili.pure.ui.common.VideoCardSpec

private val ChannelVideoSpec = VideoCardSpec(
    author = false,
    compact = true,
    coverWidth = 120.dp,
    coverHeight = 68.dp,
    contentSpacing = 8.dp
)

@Composable
internal fun ChannelVideoCard(video: UserVideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        VideoCard(
            spec = ChannelVideoSpec,
            coverUrl = video.pic,
            title = video.title,
            playCount = video.playCount,
            durationText = video.duration,
            pubdate = video.pubdate
        )
    }
}
