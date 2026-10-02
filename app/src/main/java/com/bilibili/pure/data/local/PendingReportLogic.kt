package com.bilibili.pure.data.local

data class PendingReportEntry(
    val aid: Long,
    val cid: Long,
    val progress: Long,
    val ts: Long
)

enum class QueueAction { NONE, ADD, REMOVE }

/**
 * Pure logic for the offline-report retry queue: dedup per (aid, cid),
 * bounded size, simple line-based serialization, and stop-on-first-failure flush.
 * No Android dependencies so it stays unit-testable.
 */
object PendingReportLogic {

    const val MAX_ENTRIES = 20

    fun add(
        entries: List<PendingReportEntry>,
        aid: Long,
        cid: Long,
        progress: Long,
        ts: Long
    ): List<PendingReportEntry> {
        val key = entryKey(aid, cid)
        val filtered = entries.filterNot { entryKey(it.aid, it.cid) == key }
        val result = filtered + PendingReportEntry(aid, cid, progress, ts)
        return if (result.size > MAX_ENTRIES) {
            result.sortedBy { it.ts }.takeLast(MAX_ENTRIES)
        } else {
            result
        }
    }

    fun remove(entries: List<PendingReportEntry>, aid: Long, cid: Long): List<PendingReportEntry> {
        val key = entryKey(aid, cid)
        return entries.filterNot { entryKey(it.aid, it.cid) == key }
    }

    fun serialize(entries: List<PendingReportEntry>): String =
        entries.joinToString("\n") { "${it.aid}|${it.cid}|${it.progress}|${it.ts}" }

    fun deserialize(raw: String?): List<PendingReportEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 4) return@mapNotNull null
                val aid = parts[0].toLongOrNull() ?: return@mapNotNull null
                val cid = parts[1].toLongOrNull() ?: return@mapNotNull null
                val progress = parts[2].toLongOrNull() ?: return@mapNotNull null
                val ts = parts[3].toLongOrNull() ?: return@mapNotNull null
                PendingReportEntry(aid, cid, progress, ts)
            }
            .toList()
    }

    fun newestFirst(entries: List<PendingReportEntry>): List<PendingReportEntry> =
        entries.sortedByDescending { it.ts }

    /**
     * Decides queue handling after an optional server report attempt.
     * [reportSuccess] is null when no report was attempted (e.g. not logged in, aid == 0).
     */
    fun queueAction(loggedIn: Boolean, reportSuccess: Boolean?): QueueAction = when {
        !loggedIn -> QueueAction.NONE
        reportSuccess == null -> QueueAction.NONE
        reportSuccess -> QueueAction.REMOVE
        else -> QueueAction.ADD
    }

    /**
     * Sends entries newest-first; stops at the first failure (likely offline) so a dead
     * network does not burn rate limit on the rest. Returns how many were sent.
     */
    suspend fun flush(
        entries: List<PendingReportEntry>,
        report: suspend (PendingReportEntry) -> Result<Unit>
    ): Int {
        var sent = 0
        for (entry in newestFirst(entries)) {
            val result = report(entry)
            if (result.isFailure) break
            sent++
        }
        return sent
    }

    private fun entryKey(aid: Long, cid: Long) = "${aid}_$cid"
}
