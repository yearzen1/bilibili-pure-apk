package com.bilibili.pure.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSeekGestureLogicTest {

    @Test
    fun fullWidthDragCoversNinetySeconds() {
        assertEquals(90_000L, seekDeltaMs(1000f, 1000))
    }

    @Test
    fun halfWidthDragCoversFortyFiveSeconds() {
        assertEquals(45_000L, seekDeltaMs(500f, 1000))
    }

    @Test
    fun noDragMeansNoSeekDelta() {
        assertEquals(0L, seekDeltaMs(0f, 1000))
    }

    @Test
    fun dragLeftProducesNegativeDelta() {
        assertEquals(-45_000L, seekDeltaMs(-500f, 1000))
    }

    @Test
    fun zeroWidthProducesNoSeekDelta() {
        assertEquals(0L, seekDeltaMs(500f, 0))
    }

    @Test
    fun backwardZoneIsLeftThirtyPercent() {
        assertEquals(DoubleTapZone.BACKWARD, doubleTapZone(0f, 1000))
        assertEquals(DoubleTapZone.BACKWARD, doubleTapZone(299f, 1000))
    }

    @Test
    fun middleZoneSpansThirtyToSeventyPercent() {
        assertEquals(DoubleTapZone.PAUSE, doubleTapZone(300f, 1000))
        assertEquals(DoubleTapZone.PAUSE, doubleTapZone(500f, 1000))
        assertEquals(DoubleTapZone.PAUSE, doubleTapZone(700f, 1000))
    }

    @Test
    fun forwardZoneIsRightThirtyPercent() {
        assertEquals(DoubleTapZone.FORWARD, doubleTapZone(701f, 1000))
        assertEquals(DoubleTapZone.FORWARD, doubleTapZone(1000f, 1000))
    }

    @Test
    fun unknownWidthFallsBackToPauseZone() {
        assertEquals(DoubleTapZone.PAUSE, doubleTapZone(500f, 0))
    }

    @Test
    fun clampKeepsTargetInsideDuration() {
        assertEquals(50_000L, clampSeekMs(50_000, 100_000))
        assertEquals(0L, clampSeekMs(-10_000, 100_000))
        assertEquals(100_000L, clampSeekMs(200_000, 100_000))
    }

    @Test
    fun clampWithoutKnownDurationKeepsPositiveTarget() {
        assertEquals(5_000L, clampSeekMs(5_000, 0))
        assertEquals(0L, clampSeekMs(-5_000, 0))
    }

    @Test
    fun forwardDoubleTapAddsFifteenSeconds() {
        assertEquals(65_000L, doubleTapTargetMs(50_000, DoubleTapZone.FORWARD, 100_000))
    }

    @Test
    fun backwardDoubleTapSubtractsTenSeconds() {
        assertEquals(40_000L, doubleTapTargetMs(50_000, DoubleTapZone.BACKWARD, 100_000))
    }

    @Test
    fun pauseZoneKeepsPosition() {
        assertEquals(50_000L, doubleTapTargetMs(50_000, DoubleTapZone.PAUSE, 100_000))
    }

    @Test
    fun doubleTapNearEndClampsToDuration() {
        assertEquals(100_000L, doubleTapTargetMs(95_000, DoubleTapZone.FORWARD, 100_000))
    }

    @Test
    fun doubleTapNearStartClampsToZero() {
        assertEquals(0L, doubleTapTargetMs(5_000, DoubleTapZone.BACKWARD, 100_000))
    }

    @Test
    fun formatSeekTimeUnderOneHour() {
        assertEquals("00:00", formatSeekTime(0))
        assertEquals("00:05", formatSeekTime(5_000))
        assertEquals("01:05", formatSeekTime(65_000))
        assertEquals("59:59", formatSeekTime(3_599_000))
    }

    @Test
    fun formatSeekTimeOverOneHour() {
        assertEquals("1:00:00", formatSeekTime(3_600_000))
        assertEquals("1:02:03", formatSeekTime(3_723_000))
    }

    @Test
    fun formatSeekTimeClampsNegativeInput() {
        assertEquals("00:00", formatSeekTime(-1_000))
    }

    @Test
    fun singleTapShowsControllerWhenHidden() {
        assertTrue(controllerVisibilityAfterTap(currentlyVisible = false))
    }

    @Test
    fun singleTapHidesControllerWhenVisible() {
        assertFalse(controllerVisibilityAfterTap(currentlyVisible = true))
    }
}
