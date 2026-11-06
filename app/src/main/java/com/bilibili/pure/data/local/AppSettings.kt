package com.bilibili.pure.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppSettings {
    private const val PREFS_NAME = "bili_settings"
    private const val KEY_WIFI_ONLY_PLAYBACK = "wifi_only_playback"
    private const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_UPDATE_PROMPTED_TAG = "update_prompted_tag"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_while_playing"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_WIFI_ONLY_PLAYBACK)) {
            prefs.edit().putBoolean(KEY_WIFI_ONLY_PLAYBACK, true).apply()
        }
        if (!prefs.contains(KEY_WIFI_ONLY_DOWNLOAD)) {
            prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, true).apply()
        }
        themeModeState = prefs.getInt(KEY_THEME_MODE, 0)
        keepScreenOnState = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
    }

    private var themeModeState by mutableIntStateOf(0)

    private var keepScreenOnState by mutableStateOf(false)

    /** 播放视频时是否保持屏幕常亮，默认关闭。 */
    var keepScreenOnWhilePlaying: Boolean
        get() = keepScreenOnState
        set(value) {
            keepScreenOnState = value
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }

    var themeMode: Int
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.edit().putInt(KEY_THEME_MODE, value).apply()
        }

    var wifiOnlyPlayback: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY_PLAYBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY_PLAYBACK, value).apply()

    var wifiOnlyDownload: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, value).apply()

    /** Release tag of the update prompt the user last dismissed with "稍后". */
    var updatePromptedTag: String
        get() = prefs.getString(KEY_UPDATE_PROMPTED_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPDATE_PROMPTED_TAG, value).apply()

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
