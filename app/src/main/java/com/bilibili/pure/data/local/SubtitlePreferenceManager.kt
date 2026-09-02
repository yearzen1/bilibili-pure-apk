package com.bilibili.pure.data.local

import android.content.SharedPreferences
import org.json.JSONObject

data class SubtitlePreference(
    val lan: String,
    val enabled: Boolean,
    val offsetX: Float,
    val offsetY: Float
)

class SubtitlePreferenceManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_PREFIX = "subtitle_"
    }

    private fun pageKey(aid: Long, cid: Long) = "$KEY_PREFIX${aid}_$cid"

    fun load(aid: Long, cid: Long): SubtitlePreference? {
        val json = prefs.getString(pageKey(aid, cid), null) ?: return null
        return try {
            val obj = JSONObject(json)
            SubtitlePreference(
                lan = obj.optString("lan", ""),
                enabled = obj.optBoolean("enabled", false),
                offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                offsetY = obj.optDouble("offsetY", 0.0).toFloat()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(aid: Long, cid: Long, pref: SubtitlePreference) {
        val obj = JSONObject().apply {
            put("lan", pref.lan)
            put("enabled", pref.enabled)
            put("offsetX", pref.offsetX.toDouble())
            put("offsetY", pref.offsetY.toDouble())
        }
        prefs.edit()
            .putString(pageKey(aid, cid), obj.toString())
            .apply()
    }
}
