package com.bilibili.pure.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TextUtilsTest {

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
    fun formatCountKeepsSmallNumbersAsIs() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("9999", formatCount(9999))
    }

    @Test
    fun formatCountConvertsTenThousandUnits() {
        assertEquals("1万", formatCount(10000))
        assertEquals("12万", formatCount(123456))
        assertEquals("1亿", formatCount(100000000))
    }

    @Test
    fun formatDurationSecondsRendersMinutesSeconds() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:59", formatDuration(59L))
        assertEquals("1:14", formatDuration(74L))
    }

    @Test
    fun formatDurationSecondsRendersHoursWhenOverOneHour() {
        assertEquals("1:02:03", formatDuration(3723L))
        assertEquals("39:42:55", formatDuration(142975L))
    }

    @Test
    fun formatDurationSecondsCoercesNegativeToZero() {
        assertEquals("0:00", formatDuration(-5L))
    }

    @Test
    fun formatDurationRawStringPadsSeconds() {
        assertEquals("59:09", formatDuration("59:9"))
        assertEquals("1:05", formatDuration("1:05"))
        assertEquals("abc", formatDuration("abc"))
    }

    @Test
    fun fixPicUpgradesProtocolRelativeUrls() {
        assertEquals("https://i0.hdslb.com/a.jpg", fixPic("//i0.hdslb.com/a.jpg"))
    }

    @Test
    fun fixPicUpgradesInsecureHttpUrls() {
        assertEquals("https://i0.hdslb.com/a.jpg", fixPic("http://i0.hdslb.com/a.jpg"))
    }

    @Test
    fun fixPicKeepsHttpsUrlsUntouched() {
        assertEquals("https://i0.hdslb.com/a.jpg", fixPic("https://i0.hdslb.com/a.jpg"))
        assertEquals("", fixPic(""))
    }

    @Test
    fun formatPubdateReturnsEmptyForMissingTimestamps() {
        assertEquals("", formatPubdate(0L))
        assertEquals("", formatPubdate(-100L))
    }

    @Test
    fun formatPubdateFormatsSecondsTimestampAsDate() {
        assertEquals("2019-12-06", formatPubdate(1575621902L))
    }

    private fun ts(text: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(text)!!.time / 1000

    @Test
    fun formatPubdateCompactReturnsEmptyForMissingTimestamps() {
        val now = ts("2025-10-10 12:00:00")
        assertEquals("", formatPubdateCompact(0L, now))
        assertEquals("", formatPubdateCompact(-100L, now))
    }

    @Test
    fun formatPubdateCompactUsesRelativeTimeInsideSevenDays() {
        val now = ts("2025-10-10 12:00:00")
        assertEquals("刚刚", formatPubdateCompact(now, now))
        assertEquals("刚刚", formatPubdateCompact(now - 30, now))
        assertEquals("1分钟前", formatPubdateCompact(now - 90, now))
        assertEquals("30分钟前", formatPubdateCompact(now - 30 * 60, now))
        assertEquals("2小时前", formatPubdateCompact(now - 7200, now))
        assertEquals("3天前", formatPubdateCompact(now - 3 * 86400, now))
        assertEquals("6天前", formatPubdateCompact(now - 6 * 86400, now))
    }

    @Test
    fun formatPubdateCompactUsesMonthDayWithinSameYear() {
        val now = ts("2025-10-10 12:00:00")
        assertEquals("10-03", formatPubdateCompact(now - 7 * 86400, now))
        assertEquals("09-10", formatPubdateCompact(ts("2025-09-10 08:00:00"), now))
        assertEquals("01-01", formatPubdateCompact(ts("2025-01-01 00:00:00"), now))
    }

    @Test
    fun formatPubdateCompactKeepsYearForOlderDates() {
        val now = ts("2025-10-10 12:00:00")
        assertEquals("2019-12-06", formatPubdateCompact(1575621902L, now))
        assertEquals("2024-12-31", formatPubdateCompact(ts("2024-12-31 23:59:59"), now))
    }

    @Test
    fun formatPubdateCompactRendersFutureTimestampsAsDates() {
        val now = ts("2025-10-10 12:00:00")
        assertEquals("10-13", formatPubdateCompact(now + 3 * 86400, now))
        assertEquals("2026-01-01", formatPubdateCompact(ts("2026-01-01 00:00:00"), now))
    }
}
