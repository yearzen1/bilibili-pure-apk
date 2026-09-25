package com.bilibili.pure.ui.detail

import com.bilibili.pure.data.model.CommentContent
import com.bilibili.pure.data.model.CommentCursor
import com.bilibili.pure.data.model.CommentItem
import com.bilibili.pure.data.model.CommentMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentPagingTest {

    @Test
    fun duplicateRpidAcrossPagesIsKeptOnce() {
        val firstPage = listOf(comment(318477871888), comment(2))
        val nextPage = listOf(comment(318477871888), comment(3))

        val merged = appendUniqueComments(firstPage, nextPage)

        assertEquals(listOf(318477871888L, 2L, 3L), merged.map { it.rpid })
    }

    @Test
    fun duplicateRpidWithinPageIsKeptOnce() {
        val firstPage = listOf(comment(1))
        val nextPage = listOf(comment(1), comment(1), comment(2))

        val merged = appendUniqueComments(firstPage, nextPage)

        assertEquals(listOf(1L, 2L), merged.map { it.rpid })
    }

    @Test
    fun nullCursorStopsPagination() {
        val state = resolveCommentPage(cursor = null, requestedCursor = 10)

        assertEquals(CommentPageState(nextCursor = 0, hasMore = false), state)
    }

    @Test
    fun zeroCursorStopsPagination() {
        val state = resolveCommentPage(
            cursor = CommentCursor(next = 0, isEnd = false),
            requestedCursor = 0
        )

        assertEquals(CommentPageState(nextCursor = 0, hasMore = false), state)
    }

    @Test
    fun repeatedCursorStopsPagination() {
        val state = resolveCommentPage(
            cursor = CommentCursor(next = 10, isEnd = false),
            requestedCursor = 10
        )

        assertEquals(CommentPageState(nextCursor = 10, hasMore = false), state)
    }

    @Test
    fun backwardCursorStopsPagination() {
        val state = resolveCommentPage(
            cursor = CommentCursor(next = 5, isEnd = false),
            requestedCursor = 10
        )

        assertEquals(CommentPageState(nextCursor = 5, hasMore = false), state)
    }

    @Test
    fun advancingCursorKeepsPagination() {
        val state = resolveCommentPage(
            cursor = CommentCursor(next = 20, isEnd = false),
            requestedCursor = 10
        )

        assertEquals(CommentPageState(nextCursor = 20, hasMore = true), state)
    }

    @Test
    fun staleCommentRequestIsRejected() {
        assertFalse(isCurrentCommentRequest(requestGeneration = 1, currentGeneration = 2))
        assertTrue(isCurrentCommentRequest(requestGeneration = 2, currentGeneration = 2))
    }

    private fun comment(rpid: Long): CommentItem = CommentItem(
        rpid = rpid,
        content = CommentContent(message = "rpid=$rpid"),
        member = CommentMember(uname = "tester", avatar = "")
    )
}
