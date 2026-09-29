package com.bilibili.pure.ui.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class VideoMetaTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun segmentsFollowFixedOrderPlayDurationDate() {
        val segments = buildMetaSegments(
            spec = VideoCardSpec(),
            playCount = 123456,
            durationText = "12:34",
            pubdate = 1575621902L
        )
        assertEquals(
            listOf(MetaSegment("12万播放", shrinkable = true)),
            segments.filter { it.shrinkable }
        )
        assertEquals(
            listOf("12万播放", "12:34", "2019-12-06"),
            segments.map { it.text }
        )
    }

    @Test
    fun segmentsOnlyLetPlayCountShrink() {
        val segments = buildMetaSegments(
            spec = VideoCardSpec(),
            playCount = 123456,
            durationText = "12:34",
            pubdate = 1575621902L
        )
        assertEquals(listOf(true, false, false), segments.map { it.shrinkable })
    }

    @Test
    fun segmentsUseCompactDateWithinSameYear() {
        val now = 1759953600L
        val segments = buildMetaSegments(
            spec = VideoCardSpec(),
            playCount = 123456,
            durationText = "12:34",
            pubdate = now - 3 * 86400,
            nowSeconds = now
        )
        assertEquals("3天前", segments.last().text)
        assertEquals(listOf(true, false, false), segments.map { it.shrinkable })
    }

    @Test
    fun segmentsSkipFieldsDisabledBySpec() {
        val segments = buildMetaSegments(
            spec = VideoCardSpec(play = false, duration = false, date = false),
            playCount = 123456,
            durationText = "12:34",
            pubdate = 1575621902L
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun segmentsKeepZeroPlayCountVisible() {
        val segments = buildMetaSegments(
            spec = VideoCardSpec(),
            playCount = 0,
            durationText = "12:34",
            pubdate = 0L
        )
        assertEquals(listOf("0播放", "12:34"), segments.map { it.text })
    }

    @Test
    fun segmentsSkipBlankDurationAndMissingDate() {
        val segments = buildMetaSegments(
            spec = VideoCardSpec(),
            playCount = 100,
            durationText = "",
            pubdate = 0L
        )
        assertEquals(listOf("100播放"), segments.map { it.text })
    }
}
