package com.bilibili.pure.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingReportLogicTest {

    private fun entry(aid: Long, cid: Long, progress: Long, ts: Long) =
        PendingReportEntry(aid = aid, cid = cid, progress = progress, ts = ts)

    @Test
    fun add_creates_new_entry() {
        val result = PendingReportLogic.add(emptyList(), aid = 1L, cid = 2L, progress = 100L, ts = 10L)
        assertEquals(1, result.size)
        assertEquals(entry(1L, 2L, 100L, 10L), result[0])
    }

    @Test
    fun add_same_video_replaces_progress_keeps_single_entry() {
        var list = PendingReportLogic.add(emptyList(), 1L, 2L, 100L, 10L)
        list = PendingReportLogic.add(list, 1L, 2L, 200L, 20L)
        assertEquals(1, list.size)
        assertEquals(200L, list[0].progress)
        assertEquals(20L, list[0].ts)
    }

    @Test
    fun add_different_pages_are_separate_entries() {
        var list = PendingReportLogic.add(emptyList(), 1L, 2L, 100L, 10L)
        list = PendingReportLogic.add(list, 1L, 3L, 100L, 11L)
        assertEquals(2, list.size)
    }

    @Test
    fun add_caps_at_20_dropping_oldest() {
        var list: List<PendingReportEntry> = emptyList()
        for (i in 1..25) {
            list = PendingReportLogic.add(list, aid = i.toLong(), cid = 1L, progress = i.toLong(), ts = i.toLong())
        }
        assertEquals(PendingReportLogic.MAX_ENTRIES, list.size)
        assertTrue(list.none { it.aid == 1L })
        assertTrue(list.none { it.aid == 5L })
        assertTrue(list.any { it.aid == 6L })
        assertTrue(list.any { it.aid == 25L })
    }

    @Test
    fun remove_deletes_only_matching_entry() {
        var list = PendingReportLogic.add(emptyList(), 1L, 2L, 100L, 10L)
        list = PendingReportLogic.add(list, 1L, 3L, 100L, 11L)
        list = PendingReportLogic.remove(list, 1L, 2L)
        assertEquals(1, list.size)
        assertEquals(3L, list[0].cid)
    }

    @Test
    fun serialize_deserialize_roundtrip() {
        val list = listOf(entry(1L, 2L, 100L, 10L), entry(3L, 4L, 250L, 20L))
        val raw = PendingReportLogic.serialize(list)
        assertEquals(list, PendingReportLogic.deserialize(raw))
    }

    @Test
    fun deserialize_null_and_garbage_return_empty() {
        assertTrue(PendingReportLogic.deserialize(null).isEmpty())
        assertTrue(PendingReportLogic.deserialize("").isEmpty())
        assertTrue(PendingReportLogic.deserialize("not|valid").isEmpty())
    }

    @Test
    fun newestFirst_orders_descending_by_ts() {
        val list = listOf(entry(1L, 2L, 100L, 10L), entry(3L, 4L, 100L, 30L), entry(5L, 6L, 100L, 20L))
        val sorted = PendingReportLogic.newestFirst(list)
        assertEquals(listOf(30L, 20L, 10L), sorted.map { it.ts })
    }

    @Test
    fun queueAction_logged_out_is_none() {
        assertEquals(QueueAction.NONE, PendingReportLogic.queueAction(loggedIn = false, reportSuccess = null))
        assertEquals(QueueAction.NONE, PendingReportLogic.queueAction(loggedIn = false, reportSuccess = false))
        assertEquals(QueueAction.NONE, PendingReportLogic.queueAction(loggedIn = false, reportSuccess = true))
    }

    @Test
    fun queueAction_success_removes_pending() {
        assertEquals(QueueAction.REMOVE, PendingReportLogic.queueAction(loggedIn = true, reportSuccess = true))
    }

    @Test
    fun queueAction_failure_adds_pending() {
        assertEquals(QueueAction.ADD, PendingReportLogic.queueAction(loggedIn = true, reportSuccess = false))
    }

    @Test
    fun queueAction_no_attempt_is_none() {
        assertEquals(QueueAction.NONE, PendingReportLogic.queueAction(loggedIn = true, reportSuccess = null))
    }

    @Test
    fun flush_sends_newest_first_and_removes_sent() = runBlocking {
        val entries = listOf(entry(1L, 2L, 100L, 10L), entry(3L, 4L, 200L, 30L), entry(5L, 6L, 300L, 20L))
        val sent = mutableListOf<PendingReportEntry>()
        val reported = mutableListOf<Long>()
        val count = PendingReportLogic.flush(entries) { e ->
            reported.add(e.ts)
            sent.add(e)
            Result.success(Unit)
        }
        assertEquals(3, count)
        assertEquals(listOf(30L, 20L, 10L), reported)
        assertEquals(3, sent.size)
    }

    @Test
    fun flush_stops_at_first_failure() = runBlocking {
        val entries = listOf(entry(1L, 2L, 100L, 10L), entry(3L, 4L, 200L, 30L), entry(5L, 6L, 300L, 20L))
        val reported = mutableListOf<Long>()
        val count = PendingReportLogic.flush(entries) { e ->
            reported.add(e.ts)
            if (e.ts == 20L) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
        }
        assertEquals(1, count)
        assertEquals(listOf(30L, 20L), reported)
    }

    @Test
    fun flush_empty_makes_no_report() = runBlocking {
        var called = false
        val count = PendingReportLogic.flush(emptyList()) {
            called = true
            Result.success(Unit)
        }
        assertEquals(0, count)
        assertTrue(!called)
    }
}
