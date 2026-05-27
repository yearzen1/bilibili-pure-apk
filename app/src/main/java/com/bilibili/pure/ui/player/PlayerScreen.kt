package com.bilibili.pure.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.media.AudioManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilibili.pure.BilibiliApp
import com.bilibili.pure.BuildConfig
import com.bilibili.pure.R
import com.bilibili.pure.data.api.BilibiliApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bvid: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(bvid) {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "PlayerScreen: entering bvid=$bvid")
        vm.load(bvid)
    }

    val dataSourceFactory = remember {
        OkHttpDataSource.Factory(BilibiliApi.httpClient)
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    val s = when (state) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    Log.d(BilibiliApp.TAG, "Player: state=$s")
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(BilibiliApp.TAG, "Player: error=${error.message}", error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(BilibiliApp.TAG, "Player: isPlaying=$isPlaying")
                    playing = isPlaying
                }
            })
        }
    }

    val speedCtl = remember { SpeedController(player) }

    DisposableEffect(Unit) {
        onDispose {
            val aid = uiState.videoInfo?.aid
            val cid = uiState.currentPage?.cid
            if (aid != null && cid != null && player.currentPosition > 0) {
                vm.reportProgress(aid, cid, (player.currentPosition / 1000).coerceAtLeast(1))
            }
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "PlayerScreen: releasing player")
            player.run {
                playWhenReady = false
                stop()
                release()
            }
            if (isFullscreen) {
                val activity = context as? Activity
                if (activity != null) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        activity.window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
                    } else {
                        @Suppress("DEPRECATION")
                        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.videoUrl) {
        uiState.videoUrl?.let { url ->
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "PlayerScreen: preparing media url=${url.take(80)}...")
            val mediaItem = MediaItem.fromUri(url)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)
            player.prepare()
        }
    }

    LaunchedEffect(playing, uiState.videoInfo?.aid, uiState.currentPage?.cid) {
        val aid = uiState.videoInfo?.aid ?: return@LaunchedEffect
        val cid = uiState.currentPage?.cid ?: return@LaunchedEffect
        if (playing) {
            delay(30_000)
            while (true) {
                val progress = (player.currentPosition / 1000).coerceAtLeast(1)
                vm.reportProgress(aid, cid, progress)
                delay(30_000)
            }
        } else if (player.currentPosition > 0) {
            vm.reportProgress(aid, cid, (player.currentPosition / 1000).coerceAtLeast(1))
        }
    }

    fun toggleFullscreen() {
        val activity = context as? Activity ?: return
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
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
                activity.window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                speedCtl = speedCtl,
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
                        speedCtl = speedCtl,
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
                                onClick = {
                                    if (!selected) {
                                        val aid = uiState.videoInfo?.aid
                                        val cid = uiState.currentPage?.cid
                                        if (aid != null && cid != null && player.currentPosition > 0) {
                                            vm.reportProgress(aid, cid, (player.currentPosition / 1000).coerceAtLeast(1))
                                        }
                                        vm.selectPage(page)
                                    }
                                },
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
    speedCtl: SpeedController,
    onFullscreenToggle: () -> Unit,
    isFullscreen: Boolean
) {
    var showSpeedToast by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var brightnessOverlayValue by remember { mutableStateOf(0.5f) }
    var volumeOverlayValue by remember { mutableStateOf(0.5f) }
    var overlayDismissKey by remember { mutableStateOf(0) }

    LaunchedEffect(speedCtl.effectiveSpeed) {
        if (speedCtl.effectiveSpeed == 1.0f && !showSpeedToast) return@LaunchedEffect
        showSpeedToast = true
        delay(if (speedCtl.effectiveSpeed == 1.0f) 600 else 1500)
        showSpeedToast = false
    }

    LaunchedEffect(overlayDismissKey) {
        if (overlayDismissKey > 0) {
            delay(800)
            showBrightnessOverlay = false
            showVolumeOverlay = false
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

                            // 在控制器栏添加倍速按钮
                            val settingsId = ctx.resources.getIdentifier("exo_settings", "id", ctx.packageName)
                            if (settingsId != 0) {
                                val settingsView = view.findViewById<View>(settingsId)
                                val controlsRow = settingsView?.parent as? ViewGroup
                                if (controlsRow != null && controlsRow.findViewWithTag<View>("speed_button") == null) {
                                    val density = ctx.resources.displayMetrics.density
                                    val btnSize = (48 * density).toInt()
                                    val btnMargin = (2 * density).toInt()
                                    val speedBtn = TextView(ctx).apply {
                                        tag = "speed_button"
                                        text = "1.0x"
                                        setTextColor(android.graphics.Color.WHITE)
                                        textSize = 13f
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        gravity = android.view.Gravity.CENTER
                                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).also {
                                            it.leftMargin = btnMargin
                                            it.rightMargin = btnMargin
                                        }
                                        setOnClickListener { anchor ->
                                            val darkCtx = ContextThemeWrapper(ctx, R.style.ThemeOverlay_Bilibili_DarkPopup)
                                            val popup = PopupMenu(darkCtx, anchor, android.view.Gravity.TOP)
                                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                            speeds.forEachIndexed { i, speed ->
                                                popup.menu.add(0, i, 0, "%.2gx".format(speed)).apply {
                                                    if (speed == speedCtl.baseSpeed) isChecked = true
                                                 }
                                             }
                                             popup.menu.setGroupCheckable(0, true, true)
                                             popup.setOnMenuItemClickListener { item ->
                                                 val speed = speeds.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
                                                 speedCtl.setBase(speed)
                                                 showSpeedToast = true
                                                 true
                                             }
                                            popup.show()
                                        }
                                    }
                                    controlsRow.addView(speedBtn, 0)
                                }
                            }

                            var touchStartX = 0f
                            var touchStartY = 0f
                            var isOnLeftEdge = false
                            var isOnRightEdge = false
                            var isVerticalSwipe = false

                            val gestureDetector = GestureDetector(
                                ctx,
                                object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onLongPress(e: MotionEvent) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        speedCtl.startOverride()
                                    }

                                    override fun onDoubleTap(e: MotionEvent): Boolean {
                                        val p = exoPlayer as? ExoPlayer ?: return true
                                        if (p.isPlaying) p.pause() else p.play()
                                        return true
                                    }
                                }
                            )

                            view.setOnTouchListener { _, event ->
                                gestureDetector.onTouchEvent(event)

                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        touchStartX = event.x
                                        touchStartY = event.y
                                        val w = view.width
                                        isOnLeftEdge = w > 0 && event.x < w * 0.3f
                                        isOnRightEdge = w > 0 && event.x > w * 0.7f
                                        isVerticalSwipe = isOnLeftEdge || isOnRightEdge
                                        false
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        if (speedCtl.isOverrideActive) return@setOnTouchListener true
                                        if (isVerticalSwipe) {
                                            val dy = kotlin.math.abs(event.y - touchStartY)
                                            val dx = kotlin.math.abs(event.x - touchStartX)
                                            if (dy > dx && dy > 5f) {
                                                if (isOnLeftEdge && !showBrightnessOverlay) {
                                                    showBrightnessOverlay = true
                                                    val activity = ctx as? Activity
                                                    if (activity != null) {
                                                        val b = activity.window.attributes.screenBrightness
                                                        brightnessOverlayValue = if (b < 0f) 0.5f else b
                                                    }
                                                }
                                                if (isOnRightEdge && !showVolumeOverlay) {
                                                    showVolumeOverlay = true
                                                    val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                                    if (audioManager != null) {
                                                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                        volumeOverlayValue = if (max > 0) cur.toFloat() / max else 0f
                                                    }
                                                }
                                                val delta = (touchStartY - event.y) / view.height
                                                if (isOnLeftEdge) {
                                                    val activity = ctx as? Activity
                                                    if (activity != null) {
                                                        val lp = activity.window.attributes
                                                        val current = lp.screenBrightness
                                                        val newB = if (current < 0f) 0.5f + delta else current + delta
                                                        lp.screenBrightness = newB.coerceIn(0.01f, 1.0f)
                                                        activity.window.attributes = lp
                                                        brightnessOverlayValue = newB.coerceIn(0.01f, 1.0f)
                                                    }
                                                } else {
                                                    val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                                    if (audioManager != null) {
                                                        val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                        val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                        val newV = (curV + (delta * maxV).toInt()).coerceIn(0, maxV)
                                                        if (newV != curV) {
                                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                                        }
                                                        volumeOverlayValue = newV.toFloat() / maxV
                                                    }
                                                }
                                                touchStartY = event.y
                                                return@setOnTouchListener true
                                            }
                                        }
                                        false
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        if (speedCtl.isOverrideActive) {
                                            speedCtl.stopOverride()
                                            showSpeedToast = true
                                        }
                                        if (showBrightnessOverlay || showVolumeOverlay) {
                                            overlayDismissKey++
                                        }
                                        isVerticalSwipe = false
                                        false
                                    }
                                    else -> false
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.findViewWithTag<TextView>("speed_button")?.text = "%.1fx".format(speedCtl.baseSpeed)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showSpeedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "%.1fx".format(speedCtl.effectiveSpeed),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.BrightnessHigh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(brightnessOverlayValue * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(brightnessOverlayValue)
                                .align(Alignment.BottomCenter)
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(volumeOverlayValue * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(volumeOverlayValue)
                                .align(Alignment.BottomCenter)
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}
