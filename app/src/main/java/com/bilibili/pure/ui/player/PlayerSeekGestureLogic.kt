package com.bilibili.pure.ui.player

/** 双击落点所在区域，决定双击行为。 */
enum class DoubleTapZone { BACKWARD, PAUSE, FORWARD }

/** 双击右侧快进时长。 */
const val DOUBLE_TAP_FORWARD_MS = 15_000L

/** 双击左侧快退时长。 */
const val DOUBLE_TAP_BACKWARD_MS = 10_000L

/** 水平拖动整屏宽度对应的 seek 时长。 */
const val FULL_WIDTH_SEEK_MS = 90_000L

/** 左右边缘竖滑（亮度/音量）区域占比。 */
const val EDGE_ZONE_RATIO = 0.3f

/**
 * 水平拖动位移换算为 seek 偏移：整屏宽度 = [FULL_WIDTH_SEEK_MS]。
 * 视图宽度未知（<=0）时返回 0，避免除零。
 */
fun seekDeltaMs(dx: Float, viewWidth: Int): Long {
    if (viewWidth <= 0) return 0L
    return (dx / viewWidth * FULL_WIDTH_SEEK_MS).toLong()
}

/** 按触摸点 x 判定双击区域：左 30% 快退、中间暂停、右 30% 快进。 */
fun doubleTapZone(x: Float, viewWidth: Int): DoubleTapZone {
    if (viewWidth <= 0) return DoubleTapZone.PAUSE
    val ratio = x / viewWidth
    return when {
        ratio < EDGE_ZONE_RATIO -> DoubleTapZone.BACKWARD
        ratio > 1f - EDGE_ZONE_RATIO -> DoubleTapZone.FORWARD
        else -> DoubleTapZone.PAUSE
    }
}

/** 把 seek 目标限制在 [0, durationMs]；时长未知（<=0）时只保证非负。 */
fun clampSeekMs(targetMs: Long, durationMs: Long): Long {
    if (durationMs <= 0L) return targetMs.coerceAtLeast(0L)
    return targetMs.coerceIn(0L, durationMs)
}

/** 双击后的目标进度：快退/快进对应秒数，暂停区域保持原位。 */
fun doubleTapTargetMs(positionMs: Long, zone: DoubleTapZone, durationMs: Long): Long {
    val offset = when (zone) {
        DoubleTapZone.BACKWARD -> -DOUBLE_TAP_BACKWARD_MS
        DoubleTapZone.FORWARD -> DOUBLE_TAP_FORWARD_MS
        DoubleTapZone.PAUSE -> 0L
    }
    return clampSeekMs(positionMs + offset, durationMs)
}

/**
 * 单击视频空白区域后控制栏的目标可见性：隐藏则显示，显示则隐藏。
 * 双击不走这里，因此双击快进不会切换控制栏。
 */
fun controllerVisibilityAfterTap(currentlyVisible: Boolean): Boolean = !currentlyVisible

/** 毫秒格式化为 mm:ss，超过 1 小时用 h:mm:ss。 */
fun formatSeekTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
