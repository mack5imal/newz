package app.newz.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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
