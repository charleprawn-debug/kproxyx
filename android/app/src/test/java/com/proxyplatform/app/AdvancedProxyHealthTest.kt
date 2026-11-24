package com.proxyplatform.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedProxyHealthTest {
    @Test
    fun confirmsConnectionOnlyWhenServiceListenerAndSystemProxyAreVerified() {
        assertTrue(
            AdvancedSystemProxy.isOperational(
                serviceRunning = true,
                listenerReady = true,
                systemProxyMatches = true,
                protectionRestored = true,
            )
        )
    }

    @Test
    fun keepsFailureVisibleWhenAnyConnectionOrProtectionCheckFails() {
        assertFalse(AdvancedSystemProxy.isOperational(false, true, true, true))
        assertFalse(AdvancedSystemProxy.isOperational(true, false, true, true))
        assertFalse(AdvancedSystemProxy.isOperational(true, true, false, true))
        assertFalse(AdvancedSystemProxy.isOperational(true, true, true, false))
    }
}
