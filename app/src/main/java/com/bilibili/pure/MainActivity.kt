package com.bilibili.pure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bilibili.pure.ui.channel.ChannelScreen
import com.bilibili.pure.ui.channel.ChannelSearchScreen
import com.bilibili.pure.ui.detail.DetailScreen
import com.bilibili.pure.ui.favorites.FavoritesScreen
import com.bilibili.pure.ui.following.FollowingListScreen
import com.bilibili.pure.ui.history.HistoryScreen
import com.bilibili.pure.ui.login.LoginScreen
import com.bilibili.pure.ui.navigation.Routes
import com.bilibili.pure.ui.navigation.Screen
import com.bilibili.pure.ui.navigation.bottomNavItems
import com.bilibili.pure.ui.player.PlayerScreen
import com.bilibili.pure.ui.profile.ProfileScreen
import com.bilibili.pure.ui.search.SearchScreen
import com.bilibili.pure.ui.theme.BilibiliPureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BilibiliPureTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf(
        Screen.Search.route,
        Screen.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Search.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Search.route) {
                SearchScreen(
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.detail(bvid))
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLoginClick = { navController.navigate(Routes.LOGIN) },
                    onHistoryClick = { navController.navigate(Routes.HISTORY) },
                    onFavoritesClick = { navController.navigate(Routes.FAVORITES) },
                    onFollowingClick = { navController.navigate(Routes.FOLLOWING) }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("bvid") { type = NavType.StringType })
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                DetailScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() },
                    onPlay = { b -> navController.navigate(Routes.player(b)) },
                    onUploaderClick = { mid -> navController.navigate(Routes.channel(mid)) },
                    onUserClick = { mid -> navController.navigate(Routes.channel(mid)) }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("bvid") { type = NavType.StringType }),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                PlayerScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = Routes.LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoginSuccess = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(route = Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.detail(bvid))
                    }
                )
            }

            composable(route = Routes.FAVORITES) {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.detail(bvid))
                    }
                )
            }

            composable(route = Routes.FOLLOWING) {
                FollowingListScreen(
                    onBack = { navController.popBackStack() },
                    onUploaderClick = { mid ->
                        navController.navigate(Routes.channel(mid))
                    }
                )
            }

            composable(
                route = Routes.CHANNEL,
                arguments = listOf(navArgument("mid") { type = NavType.LongType })
            ) { backStackEntry ->
                val mid = backStackEntry.arguments?.getLong("mid") ?: return@composable
                ChannelScreen(
                    mid = mid,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.detail(bvid))
                    },
                    onChannelSearch = { _mid, keyword ->
                        navController.navigate(Routes.channelSearch(_mid, keyword))
                    }
                )
            }

            composable(
                route = Routes.CHANNEL_SEARCH,
                arguments = listOf(
                    navArgument("mid") { type = NavType.LongType },
                    navArgument("keyword") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val mid = backStackEntry.arguments?.getLong("mid") ?: return@composable
                val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
                ChannelSearchScreen(
                    mid = mid,
                    keyword = keyword,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.detail(bvid))
                    }
                )
            }
        }
    }
}
