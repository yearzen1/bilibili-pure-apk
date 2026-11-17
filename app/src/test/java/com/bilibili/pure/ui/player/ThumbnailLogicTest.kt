package com.bilibili.pure.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailLogicTest {

    @Test
    fun emptyFrameTimesFallBackToFirstFrame() {
        assertEquals(0, frameIndexFor(emptyList(), 12))
    }

    @Test
    fun exactFrameTimeUsesThatFrame() {
        assertEquals(1, frameIndexFor(listOf(0, 5, 10), 5))
    }

    @Test
    fun timeBetweenFramesUsesPreviousFrame() {
        assertEquals(1, frameIndexFor(listOf(0, 5, 10), 7))
    }

    @Test
    fun timeBeforeFirstFrameUsesFirstFrame() {
        assertEquals(0, frameIndexFor(listOf(3, 8, 13), 0))
    }

    @Test
    fun timeAfterLastFrameUsesLastFrame() {
        assertEquals(2, frameIndexFor(listOf(0, 5, 10), 999))
    }

    @Test
    fun firstFrameMapsToTopLeftTile() {
        val slice = tileSliceFor(
            frameIndex = 0, cols = 10, rows = 10,
            tileWidth = 480, tileHeight = 270,
            bitmapWidth = 4800, bitmapHeight = 2700
        )
        assertEquals(0, slice.sheetIndex)
        assertEquals(0, slice.srcLeft)
        assertEquals(0, slice.srcTop)
        assertEquals(480, slice.srcWidth)
        assertEquals(270, slice.srcHeight)
    }

    @Test
    fun frameInMiddleMapsToRowAndColumn() {
        val slice = tileSliceFor(
            frameIndex = 23, cols = 10, rows = 10,
            tileWidth = 480, tileHeight = 270,
            bitmapWidth = 4800, bitmapHeight = 2700
        )
        assertEquals(0, slice.sheetIndex)
        assertEquals(1440, slice.srcLeft)
        assertEquals(540, slice.srcTop)
        assertEquals(480, slice.srcWidth)
        assertEquals(270, slice.srcHeight)
    }

    @Test
    fun lastTileOfSheetMapsToBottomRight() {
        val slice = tileSliceFor(
            frameIndex = 99, cols = 10, rows = 10,
            tileWidth = 480, tileHeight = 270,
            bitmapWidth = 4800, bitmapHeight = 2700
        )
        assertEquals(4320, slice.srcLeft)
        assertEquals(2430, slice.srcTop)
    }

    @Test
    fun frameBeyondOneSheetMovesToNextSheet() {
        val slice = tileSliceFor(
            frameIndex = 100, cols = 10, rows = 10,
            tileWidth = 480, tileHeight = 270,
            bitmapWidth = 4800, bitmapHeight = 2700
        )
        assertEquals(1, slice.sheetIndex)
        assertEquals(0, slice.srcLeft)
        assertEquals(0, slice.srcTop)
    }

    @Test
    fun downsampledBitmapScalesSourceRect() {
        val slice = tileSliceFor(
            frameIndex = 23, cols = 10, rows = 10,
            tileWidth = 480, tileHeight = 270,
            bitmapWidth = 2400, bitmapHeight = 1350
        )
        assertEquals(720, slice.srcLeft)
        assertEquals(270, slice.srcTop)
        assertEquals(240, slice.srcWidth)
        assertEquals(135, slice.srcHeight)
    }

    @Test
    fun sourceRectNeverExceedsBitmapBounds() {
        val slice = tileSliceFor(
            frameIndex = 99, cols = 10, rows = 10,
            tileWidth = 480, tileHeight = 270,
            bitmapWidth = 4700, bitmapHeight = 2600
        )
        assertTrue(slice.srcLeft + slice.srcWidth <= 4700)
        assertTrue(slice.srcTop + slice.srcHeight <= 2600)
    }

    @Test
    fun previewCenterFollowsProgressBarPosition() {
        assertEquals(
            516,
            previewCenterXPx(
                targetMs = 50_000, durationMs = 100_000,
                barLeftPx = 16, barWidthPx = 1000,
                previewWidthPx = 200, containerWidthPx = 1080
            )
        )
    }

    @Test
    fun previewCenterIsClampedAtLeftEdge() {
        assertEquals(
            100,
            previewCenterXPx(
                targetMs = 0, durationMs = 100_000,
                barLeftPx = 16, barWidthPx = 1000,
                previewWidthPx = 200, containerWidthPx = 1080
            )
        )
    }

    @Test
    fun previewCenterIsClampedAtRightEdge() {
        assertEquals(
            980,
            previewCenterXPx(
                targetMs = 100_000, durationMs = 100_000,
                barLeftPx = 16, barWidthPx = 1000,
                previewWidthPx = 200, containerWidthPx = 1080
            )
        )
    }

    @Test
    fun unknownDurationKeepsPreviewAtLeftEdge() {
        assertEquals(
            100,
            previewCenterXPx(
                targetMs = 42_000, durationMs = 0,
                barLeftPx = 16, barWidthPx = 1000,
                previewWidthPx = 200, containerWidthPx = 1080
            )
        )
    }

    @Test
    fun targetBeyondDurationIsClampedToRightEdge() {
        assertEquals(
            980,
            previewCenterXPx(
                targetMs = 999_000, durationMs = 100_000,
                barLeftPx = 16, barWidthPx = 1000,
                previewWidthPx = 200, containerWidthPx = 1080
            )
        )
    }
}

