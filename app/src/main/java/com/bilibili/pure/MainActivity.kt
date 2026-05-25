package com.bilibili.pure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.bilibili.pure.ui.detail.DetailScreen
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
                BilibiliApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliApp() {
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
                ProfileScreen()
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("bvid") { type = NavType.StringType })
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                DetailScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() },
                    onPlay = { b -> navController.navigate(Routes.player(b)) }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("bvid") { type = NavType.StringType })
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                PlayerScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
