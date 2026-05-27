package com.bilibili.pure.ui.player

import androidx.compose.runtime.*
import androidx.media3.common.Player

class SpeedController(private val player: Player) {
    var effectiveSpeed by mutableStateOf(1.0f)
        private set
    var isOverrideActive by mutableStateOf(false)
        private set

    private var base = 1.0f

    val baseSpeed: Float get() = base

    fun setBase(speed: Float) {
        base = speed
        if (!isOverrideActive) {
            effectiveSpeed = speed
            player.setPlaybackSpeed(speed)
        }
    }

    fun startOverride(speed: Float = 2.0f) {
        isOverrideActive = true
        effectiveSpeed = speed
        player.setPlaybackSpeed(speed)
    }

    fun stopOverride() {
        isOverrideActive = false
        effectiveSpeed = base
        player.setPlaybackSpeed(base)
    }
}
