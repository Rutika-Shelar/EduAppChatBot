package com.example.eduappchatbot.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NetworkUtils {

    /**
     * Check if device is currently connected to internet
     */
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        // Get active network eg. WiFi, Cellular
        val network = cm.activeNetwork ?: return false
        // Get network capabilities of the active network
        val caps = cm.getNetworkCapabilities(network) ?: return false
        //Checks if the network has INTERNET capability (can access internet)
        //Checks if the network is VALIDATED (actually has connectivity, not just connected)
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Get network type (WiFi, Cellular, etc.)
     */
    fun getNetworkType(context: Context): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.NONE

        val network = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /**
     * Observe network connectivity changes in real-time
     */
    fun connectivityFlow(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            private val networks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                networks.add(network)
                trySend(true)
                DebugLogger.debugLog("NetworkUtils", "Network available: $network")
            }

            override fun onLost(network: Network) {
                networks.remove(network)
                trySend(networks.isNotEmpty())
                DebugLogger.debugLog("NetworkUtils", "Network lost: $network, remaining: ${networks.size}")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                if (hasInternet && isValidated) {
                    networks.add(network)
                } else {
                    networks.remove(network)
                }
                //trySend the current connectivity status
                trySend(networks.isNotEmpty())
                DebugLogger.debugLog("NetworkUtils", "Capabilities changed: hasInternet=$hasInternet, validated=$isValidated")
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        cm.registerNetworkCallback(request, callback)

        // Send initial state
        trySend(isConnected(context))

        awaitClose {
            cm.unregisterNetworkCallback(callback)
            DebugLogger.debugLog("NetworkUtils", "Network callback unregistered")
        }
    }.distinctUntilChanged()

    /**
     * Show no internet toast
     */
    fun showNoInternetToast(context: Context) {
        Toast.makeText(
            context,
            "No internet connection. Please check your network.",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Show slow network toast
     */
    fun showSlowNetworkToast(context: Context) {
        val networkType = getNetworkType(context)
        val message = when (networkType) {
            NetworkType.CELLULAR -> "Slow network detected. Consider switching to WiFi for better performance."
            NetworkType.WIFI -> "Slow network detected. Please check your WiFi connection."
            else -> "Network is slow. Please wait or try again later."
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Check if connected or show toast
     */
    fun ensureConnectedOrShowToast(context: Context): Boolean {
        return if (!isConnected(context)) {
            showNoInternetToast(context)
            false
        } else {
            true
        }
    }

    enum class NetworkType {
        WIFI, CELLULAR, ETHERNET, OTHER, NONE
    }
}