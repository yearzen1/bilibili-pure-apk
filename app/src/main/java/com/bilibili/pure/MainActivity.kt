package com.bilibili.pure

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.bilibili.pure.ui.channel.ChannelScreen
import com.bilibili.pure.ui.channel.ChannelSearchScreen
import com.bilibili.pure.ui.detail.DetailScreen
import com.bilibili.pure.ui.downloads.DownloadsScreen
import com.bilibili.pure.ui.favorites.FavoritesScreen
import com.bilibili.pure.ui.following.FollowingListScreen
import com.bilibili.pure.ui.history.HistoryScreen
import com.bilibili.pure.ui.login.LoginScreen
import com.bilibili.pure.ui.navigation.Routes
import com.bilibili.pure.ui.navigation.Screen
import com.bilibili.pure.ui.navigation.bottomNavItems
import com.bilibili.pure.ui.player.PlayerScreen
import com.bilibili.pure.ui.player.PlaybackSource
import com.bilibili.pure.ui.profile.ProfileScreen
import com.bilibili.pure.ui.search.SearchScreen
import com.bilibili.pure.ui.settings.SettingsScreen
import com.bilibili.pure.ui.theme.BilibiliPureTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val openDownloadsRequest = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        handleOpenDownloadsIntent(intent)
        setContent {
            BilibiliPureTheme {
                MainScreen(openDownloadsRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenDownloadsIntent(intent)
    }

    private fun handleOpenDownloadsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            openDownloadsRequest.value++
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    openDownloadsRequest: StateFlow<Int> = MutableStateFlow(0)
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val openDownloadsTrigger by openDownloadsRequest.collectAsState()
    var handledOpenDownloads by remember { mutableStateOf(0) }
    LaunchedEffect(openDownloadsTrigger) {
        if (openDownloadsTrigger != handledOpenDownloads) {
            handledOpenDownloads = openDownloadsTrigger
            navController.navigate(Routes.DOWNLOADS) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                    onFollowingClick = { navController.navigate(Routes.FOLLOWING) },
                    onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) }
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
                    onBack = { navController.popBackStack() },
                    source = PlaybackSource.ONLINE
                )
            }

            composable(
                route = Routes.PLAYER_CID,
                arguments = listOf(
                    navArgument("bvid") { type = NavType.StringType },
                    navArgument("cid") { type = NavType.LongType }
                ),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                val cid = backStackEntry.arguments?.getLong("cid") ?: 0L
                PlayerScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() },
                    startCid = if (cid != 0L) cid else null,
                    source = PlaybackSource.ONLINE
                )
            }

            composable(
                route = Routes.DOWNLOAD_PLAY,
                arguments = listOf(
                    navArgument("bvid") { type = NavType.StringType },
                    navArgument("cid") { type = NavType.LongType }
                ),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                val cid = backStackEntry.arguments?.getLong("cid") ?: 0L
                PlayerScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() },
                    startCid = if (cid != 0L) cid else null,
                    source = PlaybackSource.LOCAL
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

            composable(route = Routes.DOWNLOADS) {
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { bvid, cid ->
                        navController.navigate(Routes.downloadPlay(bvid, cid))
                    },
                    onDetail = { bvid ->
                        navController.navigate(Routes.detail(bvid))
                    }
                )
            }

            composable(route = Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
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
