package com.vibesync.music.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface Connectivity {
    fun isOnline(): Boolean
    fun isUnmeteredWifi(): Boolean
}

class NetworkMonitor(private val context: Context) : Connectivity {
    private fun caps(): NetworkCapabilities? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(net)
    }

    override fun isOnline(): Boolean = caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    override fun isUnmeteredWifi(): Boolean {
        val c = caps() ?: return false
        return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
