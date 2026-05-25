package com.bilibili.pure.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.data.api.BilibiliApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bvid: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(bvid) {
        Log.d(BilibiliApp.TAG, "PlayerScreen: entering bvid=$bvid")
        viewModel.load(bvid)
    }

    val dataSourceFactory = remember {
        OkHttpDataSource.Factory(BilibiliApi.httpClient)
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(BilibiliApp.TAG, "PlayerScreen: releasing player")
            player.run {
                playWhenReady = false
                stop()
                release()
            }
        }
    }

    LaunchedEffect(uiState.videoUrl) {
        uiState.videoUrl?.let { url ->
            Log.d(BilibiliApp.TAG, "PlayerScreen: preparing media url=${url.take(80)}...")
            val mediaItem = MediaItem.fromUri(url)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)
            player.prepare()
        }
    }

    fun toggleFullscreen() {
        val activity = context as? Activity ?: return
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.insetsController?.hide(WindowInsets.Type.systemBars())
                activity.window.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (isFullscreen) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            PlayerContent(
                uiState = uiState,
                player = player,
                onFullscreenToggle = { toggleFullscreen() },
                isFullscreen = true
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.pages.size > 1) "${uiState.title} (${uiState.currentPage?.page ?: 1}/${uiState.pages.size})"
                                   else if (uiState.title.isNotEmpty()) uiState.title
                                   else "播放",
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            player.stop()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerContent(
                        uiState = uiState,
                        player = player,
                        onFullscreenToggle = { toggleFullscreen() },
                        isFullscreen = false
                    )
                }

                if (uiState.pages.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.pages.forEach { page ->
                            val selected = page.cid == uiState.currentPage?.cid
                            FilterChip(
                                selected = selected,
                                onClick = { if (!selected) viewModel.selectPage(page) },
                                label = {
                                    Text(
                                        if (page.part.isNotEmpty()) page.part
                                        else "P${page.page}"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerContent(
    uiState: com.bilibili.pure.ui.player.PlayerUiState,
    player: Player,
    onFullscreenToggle: () -> Unit,
    isFullscreen: Boolean
) {
    when {
        uiState.isLoading -> {
            CircularProgressIndicator()
        }
        uiState.error != null -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("播放失败", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        uiState.videoUrl != null -> {
            val exoPlayer = player
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { view ->
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        view.player = exoPlayer
                        view.useController = true
                        view.setShowNextButton(false)
                        view.setShowPreviousButton(false)
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        view.setFullscreenButtonClickListener {
                            onFullscreenToggle()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
