package com.bilibili.pure.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowButtonLogicTest {

    // --- unfollowNeedsConfirm ---

    @Test
    fun unfollowNeedsConfirmWhenFollowed() {
        assertTrue(unfollowNeedsConfirm(isFollowed = true))
    }

    @Test
    fun followDoesNotNeedConfirmWhenNotFollowed() {
        assertFalse(unfollowNeedsConfirm(isFollowed = false))
    }

    // --- followButtonLabel ---

    @Test
    fun labelShowsFollowedWhenFollowed() {
        assertEquals("已关注", followButtonLabel(isFollowed = true))
    }

    @Test
    fun labelShowsFollowWhenNotFollowed() {
        assertEquals("关注", followButtonLabel(isFollowed = false))
    }

    // --- followActionKind: what a click should trigger ---

    @Test
    fun clickWhenFollowedTriggersConfirm() {
        assertEquals(FollowAction.ConfirmUnfollow, followActionForClick(isFollowed = true))
    }

    @Test
    fun clickWhenNotFollowedTriggersDirectFollow() {
        assertEquals(FollowAction.Follow, followActionForClick(isFollowed = false))
    }
}
