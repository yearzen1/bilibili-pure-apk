package com.bilibili.pure.ui.player

import androidx.media3.common.Player

/**
 * 播放页屏幕常亮判断。
 *
 * 契约：开关开启 + 有播放意图 + 未结束/未空闲时常亮；暂停、播完、空闲一律交给系统息屏。
 */
fun shouldKeepScreenOn(
    enabled: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    if (!enabled || !playWhenReady) return false
    return playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
}
