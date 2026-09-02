package com.bilibili.pure.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilibili.pure.data.model.SubtitleCue
import com.bilibili.pure.util.SubtitleParser
import kotlin.math.roundToInt

@Composable
fun SubtitleOverlay(
    cues: List<SubtitleCue>,
    currentPositionMs: Long,
    offsetX: Float,
    offsetY: Float,
    onPositionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCue = remember(cues, currentPositionMs) {
        SubtitleParser.findCurrentCue(cues, currentPositionMs)
    }

    AnimatedVisibility(
        visible = currentCue != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val density = LocalDensity.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pw = with(density) { maxWidth.roundToPx() }.toFloat()
            val ph = with(density) { maxHeight.roundToPx() }.toFloat()
            val bottomPx = with(density) { 64.dp.roundToPx() }.toFloat()

            var boxSize by remember { mutableStateOf(IntSize.Zero) }

            val currentMinX by rememberUpdatedState(
                if (boxSize.width > 0) -(pw - boxSize.width) / (2f * pw) else -0.4f
            )
            val currentMaxX by rememberUpdatedState(
                if (boxSize.width > 0) (pw - boxSize.width) / (2f * pw) else 0.4f
            )
            val currentMinY by rememberUpdatedState(
                if (boxSize.height > 0) -bottomPx / ph else -0.1f
            )
            val currentMaxY by rememberUpdatedState(
                if (boxSize.height > 0) (ph - bottomPx - boxSize.height) / ph else 0.8f
            )
            val currentPw by rememberUpdatedState(pw)
            val currentPh by rememberUpdatedState(ph)

            var dragOffsetX by remember { mutableFloatStateOf(offsetX) }
            var dragOffsetY by remember { mutableFloatStateOf(offsetY) }

            LaunchedEffect(offsetX) { dragOffsetX = offsetX }
            LaunchedEffect(offsetY) { dragOffsetY = offsetY }

            LaunchedEffect(boxSize) {
                if (boxSize.width > 0 && boxSize.height > 0) {
                    if (currentMinX <= currentMaxX) {
                        dragOffsetX = dragOffsetX.coerceIn(currentMinX, currentMaxX)
                    } else {
                        dragOffsetX = 0f
                    }
                    if (currentMinY <= currentMaxY) {
                        dragOffsetY = dragOffsetY.coerceIn(currentMinY, currentMaxY)
                    } else {
                        dragOffsetY = 0f
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 64.dp)
                    .offset {
                        IntOffset(
                            (dragOffsetX * currentPw).roundToInt(),
                            -(dragOffsetY * currentPh).roundToInt()
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                onPositionChanged(dragOffsetX, dragOffsetY)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + dragAmount.x / currentPw).coerceIn(currentMinX, currentMaxX)
                            dragOffsetY = (dragOffsetY - dragAmount.y / currentPh).coerceIn(currentMinY, currentMaxY)
                        }
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .onGloballyPositioned { coordinates ->
                        boxSize = coordinates.size
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentCue?.content ?: "",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
