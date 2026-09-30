package com.bilibili.pure.ui.downloads

import com.bilibili.pure.data.model.DownloadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsFilterTest {

    @Test
    fun blankQueryReturnsAllItems() {
        val items = listOf(single(id = 1, title = "视频A"), single(id = 2, title = "视频B"))

        val filtered = filterDownloadItems(items, "   ")

        assertEquals(2, filtered.size)
    }

    @Test
    fun matchesSingleByTitleCaseInsensitive() {
        val items = listOf(
            single(id = 1, title = "How To Ride"),
            single(id = 2, title = "别的视频")
        )

        val filtered = filterDownloadItems(items, "ride")

        assertEquals(1, filtered.size)
        assertEquals("id_1", (filtered[0] as DownloadListItem.Single).download.id)
    }

    @Test
    fun matchesGroupByTitle() {
        val group = DownloadListItem.Group(
            DownloadGroup(
                bvid = "BV1",
                title = "合集标题",
                cover = "",
                downloads = listOf(download(id = 1, title = "其他标题"))
            )
        )

        val filtered = filterDownloadItems(listOf(group), "合集")

        assertEquals(1, filtered.size)
    }

    @Test
    fun groupMatchesBySubItemPart() {
        val group = DownloadListItem.Group(
            DownloadGroup(
                bvid = "BV1",
                title = "长视频合集",
                cover = "",
                downloads = listOf(download(id = 1, title = "长视频合集", part = "开场介绍"))
            )
        )

        val filtered = filterDownloadItems(listOf(group), "开场")

        assertEquals(1, filtered.size)
    }

    @Test
    fun noMatchReturnsEmpty() {
        val items = listOf(single(id = 1, title = "视频A"))

        val filtered = filterDownloadItems(items, "不存在的关键词")

        assertTrue(filtered.isEmpty())
    }

    private fun download(id: Int, title: String, part: String = "") = DownloadInfo(
        id = "id_$id",
        bvid = "BV_$id",
        cid = id.toLong(),
        title = title,
        cover = "",
        quality = 32,
        qualityDesc = "1080P",
        filePath = "/tmp/video_$id.mp4",
        status = DownloadInfo.STATUS_COMPLETED,
        part = part
    )

    private fun single(id: Int, title: String) = DownloadListItem.Single(download(id, title))
}
