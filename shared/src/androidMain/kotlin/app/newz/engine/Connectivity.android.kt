package app.newz.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * The connectivity check [FeedRepository] takes as `isOnline`: true when the active network
 * offers internet. Needs ACCESS_NETWORK_STATE, which the app manifest declares.
 */
fun androidConnectivity(context: Context): () -> Boolean {
    val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    return {
        val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}

/**
 * Emits whenever the default network comes, goes or changes, so a screen can re-check
 * [androidConnectivity] at once rather than at its next feed update (B56). The callback is
 * registered only while the flow is collected.
 */
fun androidConnectivityChanges(context: Context): Flow<Unit> =
    callbackFlow {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(Unit)
                }

                override fun onLost(network: Network) {
                    trySend(Unit)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    trySend(Unit)
                }
            }
        manager?.registerDefaultNetworkCallback(callback)
        awaitClose { manager?.unregisterNetworkCallback(callback) }
    }.conflate()
