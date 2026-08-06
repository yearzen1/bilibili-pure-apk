package com.bilibili.pure.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object AppSettings {
    private const val PREFS_NAME = "bili_settings"
    private const val KEY_WIFI_ONLY_PLAYBACK = "wifi_only_playback"
    private const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var wifiOnlyPlayback: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY_PLAYBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY_PLAYBACK, value).apply()

    var wifiOnlyDownload: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, value).apply()

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
