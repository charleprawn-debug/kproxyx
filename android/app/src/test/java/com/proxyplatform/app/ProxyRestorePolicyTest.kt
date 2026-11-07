package com.proxyplatform.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRestorePolicyTest {
    @Test
    fun recognizesTheManagedLoopbackProxyAsSafeToRestore() {
        assertFalse(ProxyRestorePolicy.isExternalOverride("127.0.0.1:10808", "127.0.0.1:10808", null))
    }

    @Test
    fun recognizesAlreadyRestoredNullAndAndroidNoProxySentinel() {
        assertFalse(ProxyRestorePolicy.isExternalOverride(":0", "127.0.0.1:10808", null))
        assertFalse(ProxyRestorePolicy.isExternalOverride("null", "127.0.0.1:10808", ":0"))
    }

    @Test
    fun preservesProxyValueChangedByTheUserDuringTheSession() {
        assertTrue(ProxyRestorePolicy.isExternalOverride("10.0.0.9:3128", "127.0.0.1:10808", null))
    }

    @Test
    fun doesNotConfuseAProxyThatWasPresentBeforeConnectingWithAnOverride() {
        assertFalse(ProxyRestorePolicy.isExternalOverride("proxy.example:8080", "127.0.0.1:10808", "proxy.example:8080"))
    }
}
