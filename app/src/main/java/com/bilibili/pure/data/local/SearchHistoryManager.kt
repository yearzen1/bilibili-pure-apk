package com.bilibili.pure.data.local

import android.content.SharedPreferences
import org.json.JSONArray

class SearchHistoryManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY = "search_history"
        private const val MAX_ITEMS = 10
    }

    fun load(): List<String> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(history: List<String>) {
        val arr = JSONArray(history)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun add(query: String): List<String> {
        val history = load().toMutableList()
        history.remove(query)
        history.add(0, query)
        val trimmed = history.take(MAX_ITEMS)
        save(trimmed)
        return trimmed
    }

    fun remove(query: String): List<String> {
        val history = load().toMutableList()
        history.remove(query)
        save(history)
        return history
    }

    fun clear(): List<String> {
        prefs.edit().remove(KEY).apply()
        return emptyList()
    }
}
