package com.bilibili.pure.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun followSystemModeUsesSystemDarkFlag() {
        assertTrue(resolveDarkTheme(THEME_FOLLOW_SYSTEM, systemDark = true))
        assertFalse(resolveDarkTheme(THEME_FOLLOW_SYSTEM, systemDark = false))
    }

    @Test
    fun lightModeIgnoresSystemDarkFlag() {
        assertFalse(resolveDarkTheme(THEME_LIGHT, systemDark = true))
        assertFalse(resolveDarkTheme(THEME_LIGHT, systemDark = false))
    }

    @Test
    fun darkModeIgnoresSystemDarkFlag() {
        assertTrue(resolveDarkTheme(THEME_DARK, systemDark = true))
        assertTrue(resolveDarkTheme(THEME_DARK, systemDark = false))
    }

    @Test
    fun invalidModeFallsBackToFollowSystem() {
        assertTrue(resolveDarkTheme(99, systemDark = true))
        assertFalse(resolveDarkTheme(-1, systemDark = false))
    }

    @Test
    fun labelsMatchThreeModes() {
        assertEquals(3, themeModeLabels.size)
        assertEquals("跟随系统", themeModeLabels[THEME_FOLLOW_SYSTEM])
        assertEquals("浅色（白天）", themeModeLabels[THEME_LIGHT])
        assertEquals("深色（黑夜）", themeModeLabels[THEME_DARK])
    }

    @Test
    fun rowTitleReflectsDarkState() {
        assertEquals("深色模式", themeRowTitle(dark = true))
        assertEquals("浅色模式", themeRowTitle(dark = false))
    }
}
