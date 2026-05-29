package com.bilibili.pure.data.local

import android.content.SharedPreferences
import org.json.JSONObject

data class PlaybackProgress(
    val cid: Long,
    val progress: Long,
    val duration: Long
)

class PlaybackProgressManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_PREFIX = "progress_"
        private const val MIN_PROGRESS_SEC = 5L
    }

    fun load(aid: Long): PlaybackProgress? {
        val json = prefs.getString("$KEY_PREFIX$aid", null) ?: return null
        return try {
            val obj = JSONObject(json)
            val cid = obj.getLong("cid")
            val progress = obj.getLong("progress")
            val duration = obj.getLong("duration")

            if (progress < MIN_PROGRESS_SEC) return null
            if (duration > 0 && progress >= duration * 0.95) return null

            PlaybackProgress(cid, progress, duration)
        } catch (_: Exception) {
            null
        }
    }

    fun save(aid: Long, cid: Long, progress: Long, duration: Long) {
        if (progress < MIN_PROGRESS_SEC) return
        if (duration > 0 && progress >= duration * 0.95) {
            prefs.edit().remove("$KEY_PREFIX$aid").apply()
            return
        }
        val obj = JSONObject().apply {
            put("cid", cid)
            put("progress", progress)
            put("duration", duration)
        }
        prefs.edit().putString("$KEY_PREFIX$aid", obj.toString()).apply()
    }
}
