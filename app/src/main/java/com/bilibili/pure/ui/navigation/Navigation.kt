package com.bilibili.pure.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Search : Screen("search", "搜索", Icons.Default.Search)
    data object Profile : Screen("profile", "个人", Icons.Default.Person)
}

object Routes {
    const val DETAIL = "detail/{bvid}"
    const val PLAYER = "player/{bvid}"
    const val LOGIN = "login"

    fun detail(bvid: String) = "detail/$bvid"
    fun player(bvid: String) = "player/$bvid"
}

val bottomNavItems = listOf(Screen.Search, Screen.Profile)
