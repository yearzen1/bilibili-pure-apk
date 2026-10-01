package com.bilibili.pure.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme()

private val DarkColorScheme = darkColorScheme()

const val THEME_FOLLOW_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

val themeModeLabels = mapOf(
    THEME_FOLLOW_SYSTEM to "跟随系统",
    THEME_LIGHT to "浅色（白天）",
    THEME_DARK to "深色（黑夜）"
)

fun resolveDarkTheme(themeMode: Int, systemDark: Boolean): Boolean = when (themeMode) {
    THEME_LIGHT -> false
    THEME_DARK -> true
    else -> systemDark
}

fun themeRowTitle(dark: Boolean): String = if (dark) "深色模式" else "浅色模式"

@Composable
fun BilibiliPureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
