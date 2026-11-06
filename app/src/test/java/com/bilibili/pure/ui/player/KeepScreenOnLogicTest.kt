package com.bilibili.pure.ui.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenOnLogicTest {

    @Test
    fun disabledSwitchNeverKeepsScreenOn() {
        assertFalse(
            shouldKeepScreenOn(enabled = false, playWhenReady = true, playbackState = Player.STATE_READY)
        )
        assertFalse(
            shouldKeepScreenOn(enabled = false, playWhenReady = true, playbackState = Player.STATE_BUFFERING)
        )
    }

    @Test
    fun playingVideoKeepsScreenOnWhenEnabled() {
        assertTrue(
            shouldKeepScreenOn(enabled = true, playWhenReady = true, playbackState = Player.STATE_READY)
        )
    }

    @Test
    fun bufferingVideoKeepsScreenOnWhenEnabled() {
        assertTrue(
            shouldKeepScreenOn(enabled = true, playWhenReady = true, playbackState = Player.STATE_BUFFERING)
        )
    }

    @Test
    fun pausedVideoAllowsScreenOff() {
        assertFalse(
            shouldKeepScreenOn(enabled = true, playWhenReady = false, playbackState = Player.STATE_READY)
        )
    }

    @Test
    fun endedVideoAllowsScreenOff() {
        assertFalse(
            shouldKeepScreenOn(enabled = true, playWhenReady = true, playbackState = Player.STATE_ENDED)
        )
    }

    @Test
    fun idlePlayerAllowsScreenOff() {
        assertFalse(
            shouldKeepScreenOn(enabled = true, playWhenReady = true, playbackState = Player.STATE_IDLE)
        )
    }
}
