package com.bilibili.pure.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentScrollAnchorTest {

    private val rpid = 100L
    private val thread = listOf(11L, 12L, 13L)

    // --- collapseScrollTargetIndex: normal comment ---

    @Test
    fun returnsNullWhenParentCommentIsFirstVisible() {
        // 父评论仍在视口 → key 存活，LazyList rebind 正确，无需补偿
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = rpid, firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun collapseButtonFirstVisibleAnchorsToParent() {
        // 首可见 = 收起按钮（thread.size=3 条回复 + 1 按钮在父评论之后）
        assertEquals(
            40 - (3 + 1),
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = "collapse_$rpid", firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun replyRowFirstVisibleAnchorsToParent() {
        // 首可见 = thread 中第 2 条回复（pos=1）→ 父评论在 40 - (1+1)
        assertEquals(
            38,
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = "r_$rpid:12", firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun firstReplyRowAnchorsToImmediatelyPrecedingParent() {
        assertEquals(
            39,
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = "r_$rpid:11", firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun returnsNullWhenChildNotInThread() {
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = "r_$rpid:999", firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun returnsNullWhenReplyKeyUnparseable() {
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = "r_$rpid:abc", firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun returnsNullForUnrelatedVisibleKey() {
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = 555L, firstVisibleIndex = 40
            )
        )
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = "bottom", firstVisibleIndex = 40
            )
        )
    }

    @Test
    fun returnsNullWhenNoVisibleItem() {
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = thread,
                firstVisibleKey = null, firstVisibleIndex = 0
            )
        )
    }

    @Test
    fun returnsNullWhenThreadEmptyAndCollapseVisible() {
        // 理论上收起按钮可见时 thread 必非空，兜底不崩
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = false, threadChildRpids = emptyList(),
                firstVisibleKey = "collapse_$rpid", firstVisibleIndex = 0
            )
        )
    }

    // --- pinned comment key variants ---

    @Test
    fun pinnedParentVisibleReturnsNull() {
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = true, threadChildRpids = thread,
                firstVisibleKey = "pinned_$rpid", firstVisibleIndex = 10
            )
        )
    }

    @Test
    fun pinnedCollapseButtonAnchorsToPinnedParent() {
        assertEquals(
            10 - (3 + 1),
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = true, threadChildRpids = thread,
                firstVisibleKey = "pinned_collapse_$rpid", firstVisibleIndex = 10
            )
        )
    }

    @Test
    fun pinnedReplyRowAnchorsToPinnedParent() {
        assertEquals(
            7,
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = true, threadChildRpids = thread,
                firstVisibleKey = "pinned_r_$rpid:13", firstVisibleIndex = 10
            )
        )
    }

    @Test
    fun pinnedHandlerIgnoresNormalKeys() {
        // isPinned=true 时不匹配普通 key
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = true, threadChildRpids = thread,
                firstVisibleKey = "collapse_$rpid", firstVisibleIndex = 40
            )
        )
        assertNull(
            collapseScrollTargetIndex(
                rpid = rpid, isPinned = true, threadChildRpids = thread,
                firstVisibleKey = rpid, firstVisibleIndex = 40
            )
        )
    }

    // --- commentsHeaderIndex ---

    @Test
    fun headerIndexWithoutUgcSeason() {
        assertEquals(7, commentsHeaderIndex(hasUgcSeason = false))
    }

    @Test
    fun headerIndexWithUgcSeason() {
        assertEquals(8, commentsHeaderIndex(hasUgcSeason = true))
    }
}
