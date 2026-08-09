package com.bilibili.pure.data.local

import android.content.SharedPreferences
import org.json.JSONObject

data class PlaybackProgress(
    val cid: Long,
    val progress: Long,
    val duration: Long
)

/**
 * Stores playback progress per (aid, cid) so each 分P page keeps its own resume position.
 * Also tracks the last-watched page per aid via `last_page_{aid}`.
 * Legacy single-record format (`progress_{aid}`) is read as a migration fallback.
 */
class PlaybackProgressManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_PAGE_PREFIX = "progress_"
        private const val KEY_LAST_PAGE = "last_page_"
        private const val MIN_PROGRESS_SEC = 5L
    }

    private fun pageKey(aid: Long, cid: Long) = "$KEY_PAGE_PREFIX${aid}_$cid"

    private fun legacyKey(aid: Long) = "$KEY_PAGE_PREFIX$aid"

    /** Returns saved progress (seconds) for a specific page, or null if none/should-start-fresh. */
    fun load(aid: Long, cid: Long): Long? {
        val json = prefs.getString(pageKey(aid, cid), null)
        if (json != null) {
            return parsePageEntry(json)
        }
        return migrateLegacy(aid, cid)
    }

    /** Last watched page cid for the aid, or null. */
    fun lastCid(aid: Long): Long? {
        val last = prefs.getString("$KEY_LAST_PAGE$aid", null)
        if (last != null) {
            return last.toLongOrNull()
        }
        // Migration: fall back to the legacy single-record format.
        val legacy = prefs.getString(legacyKey(aid), null)
        return legacy?.let { json ->
            try {
                JSONObject(json).optLong("cid", 0L).takeIf { it > 0 }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun save(aid: Long, cid: Long, progress: Long, duration: Long) {
        if (progress < MIN_PROGRESS_SEC) return
        val key = pageKey(aid, cid)
        if (duration > 0 && progress >= duration * 0.95) {
            prefs.edit().remove(key).apply()
            return
        }
        val obj = JSONObject().apply {
            put("progress", progress)
            put("duration", duration)
        }
        prefs.edit()
            .putString(key, obj.toString())
            .putString("$KEY_LAST_PAGE$aid", cid.toString())
            .apply()
        // Clean up legacy single-record key once migrated.
        prefs.edit().remove(legacyKey(aid)).apply()
    }

    private fun migrateLegacy(aid: Long, cid: Long): Long? {
        val json = prefs.getString(legacyKey(aid), null) ?: return null
        val entry = try {
            JSONObject(json).let {
                PlaybackProgress(
                    cid = it.optLong("cid", 0L),
                    progress = it.optLong("progress", 0L),
                    duration = it.optLong("duration", 0L)
                )
            }
        } catch (_: Exception) {
            return null
        }
        if (entry.cid != cid) return null
        // Re-save into per-page storage once, then drop the legacy key.
        if (entry.progress >= MIN_PROGRESS_SEC) {
            save(aid, entry.cid, entry.progress, entry.duration)
        }
        return normativeProgress(entry.progress, entry.duration)
    }

    private fun normativeProgress(progress: Long, duration: Long): Long? {
        if (progress < MIN_PROGRESS_SEC) return null
        if (duration > 0 && progress >= duration * 0.95) return null
        return progress
    }

    private fun parsePageEntry(json: String?): Long? {
        if (json == null) return null
        return try {
            val obj = JSONObject(json)
            val progress = obj.optLong("progress", 0L)
            val duration = obj.optLong("duration", 0L)
            normativeProgress(progress, duration)
        } catch (_: Exception) {
            null
        }
    }
}