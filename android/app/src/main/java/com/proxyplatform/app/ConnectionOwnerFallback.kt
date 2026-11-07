package com.proxyplatform.app

import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.StringIterator

/**
 * libbox's Android bridge dereferences the returned ConnectionOwner. Returning
 * null from a platform-only/proxy-only implementation can therefore panic in
 * native Go code when a local inbound connection arrives. The app does not
 * implement per-app routing, so an explicit unknown owner is sufficient.
 */
internal object ConnectionOwnerFallback {
    private val noPackageNames = object : StringIterator {
        override fun hasNext(): Boolean = false
        override fun len(): Int = 0
        override fun next(): String = error("No Android package names are available")
    }

    fun unknown(): ConnectionOwner = ConnectionOwner().apply {
        userId = -1
        userName = ""
        processPath = ""
        setAndroidPackageNames(noPackageNames)
    }
}
