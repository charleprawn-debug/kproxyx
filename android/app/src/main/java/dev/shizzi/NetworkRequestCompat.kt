package dev.shizzi

import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Clears the default request capabilities on every supported Android version.
 *
 * NetworkRequest.Builder.clearCapabilities() was added after the app's minSdk.
 * Reflection keeps the newer implementation when available, while the
 * explicit fallback preserves the same test-network request semantics on API 26.
 */
internal fun NetworkRequest.Builder.clearCapabilitiesCompat(): NetworkRequest.Builder {
    val clearedByPlatform = runCatching {
        NetworkRequest.Builder::class.java
            .getMethod("clearCapabilities")
            .invoke(this)
    }.isSuccess

    if (clearedByPlatform) return this

    return removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
