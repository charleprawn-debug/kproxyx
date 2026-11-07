package com.proxyplatform.app

/** Helpers for safely distinguishing our proxy from a user's independent change. */
internal object ProxyRestorePolicy {
    fun isExternalOverride(current: String?, managedProxy: String, originalProxy: String?): Boolean =
        !sameProxy(current, managedProxy) && !sameProxy(current, originalProxy)

    fun isNoProxy(value: String?): Boolean =
        value == null || value.trim().isEmpty() || value.trim().equals("null", ignoreCase = true) || value.trim() == ":0"

    private fun sameProxy(first: String?, second: String?): Boolean =
        if (isNoProxy(first) && isNoProxy(second)) true else first?.trim() == second?.trim()
}
