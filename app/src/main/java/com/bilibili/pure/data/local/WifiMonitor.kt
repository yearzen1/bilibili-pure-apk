package com.bilibili.pure.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.bilibili.pure.BilibiliApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WifiMonitor {

    private val _wifiLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wifiLost: SharedFlow<Unit> = _wifiLost.asSharedFlow()

    private val _wifiRestored = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wifiRestored: SharedFlow<Unit> = _wifiRestored.asSharedFlow()

    private var started = false
    private var wasOnWifi = true

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wasOnWifi = AppSettings.isWifiConnected(context)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyWifiState(cm)
            }

            override fun onLost(network: Network) {
                notifyWifiState(cm)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                notifyWifiState(cm)
            }
        })
    }

    private fun notifyWifiState(cm: ConnectivityManager) {
        val isOnWifi = try {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e(BilibiliApp.TAG, "WifiMonitor: capabilities check failed", e)
            return
        }

        if (wasOnWifi && !isOnWifi) {
            Log.d(BilibiliApp.TAG, "WifiMonitor: wifi lost")
            _wifiLost.tryEmit(Unit)
        } else if (!wasOnWifi && isOnWifi) {
            Log.d(BilibiliApp.TAG, "WifiMonitor: wifi restored")
            _wifiRestored.tryEmit(Unit)
        }
        wasOnWifi = isOnWifi
    }
}