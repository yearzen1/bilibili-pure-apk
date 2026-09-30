package com.bilibili.pure.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonUiLogicTest {

    @Test
    fun scrollFabHiddenAtTop() {
        assertFalse(shouldShowScrollToTop(firstVisibleIndex = 0))
    }

    @Test
    fun scrollFabHiddenBelowThreshold() {
        assertFalse(shouldShowScrollToTop(firstVisibleIndex = 2))
    }

    @Test
    fun scrollFabShownAboveThreshold() {
        assertTrue(shouldShowScrollToTop(firstVisibleIndex = 3))
    }

    @Test
    fun scrollFabRespectsCustomThreshold() {
        assertFalse(shouldShowScrollToTop(firstVisibleIndex = 5, threshold = 6))
        assertTrue(shouldShowScrollToTop(firstVisibleIndex = 6, threshold = 6).not())
        assertTrue(shouldShowScrollToTop(firstVisibleIndex = 7, threshold = 6))
    }

    @Test
    fun normalizeQueryTrimsWhitespace() {
        assertEquals("hello", normalizeSearchQuery("  hello  "))
    }

    @Test
    fun normalizeQueryBlankReturnsNull() {
        assertNull(normalizeSearchQuery(""))
        assertNull(normalizeSearchQuery("   "))
        assertNull(normalizeSearchQuery("\t \n"))
    }

    @Test
    fun normalizeQueryKeepsPlainValue() {
        assertEquals("鹿野", normalizeSearchQuery("鹿野"))
    }
}
