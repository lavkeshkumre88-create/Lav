package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val connected: Boolean = true,
    val unmeteredWifi: Boolean = true
)

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(readCurrentStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = readCurrentStatus()
        }

        override fun onLost(network: Network) {
            _status.value = readCurrentStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _status.value = readCurrentStatus()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            _status.value = NetworkStatus(connected = true, unmeteredWifi = true)
        }
    }

    private fun readCurrentStatus(): NetworkStatus {
        return try {
            val active = connectivityManager.activeNetwork ?: return NetworkStatus(false, false)
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return NetworkStatus(false, false)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            NetworkStatus(connected = hasInternet, unmeteredWifi = hasInternet && isUnmetered)
        } catch (_: Exception) {
            NetworkStatus(connected = true, unmeteredWifi = true)
        }
    }

    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}
