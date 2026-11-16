package com.proxyplatform.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootWebRtcRulesTest {
    @Test fun protectsBothAddressFamiliesAndArbitraryPorts() {
        val script = RootWebRtcRules.apply(10123)
        assertTrue(script.contains("for BIN in iptables ip6tables"))
        assertTrue(script.contains("-p udp -j REJECT"))
        assertTrue(script.contains("-p tcp -j REJECT"))
        assertFalse(script.contains("--dport 3478"))
        assertFalse(script.contains("-F OUTPUT"))
        assertTrue(script.contains("-I OUTPUT 1 -j ${RootWebRtcRules.CHAIN}"))
    }

    @Test fun onlyTheProxyAppGetsUnrestrictedTcpNotUdp() {
        val script = RootWebRtcRules.apply(10123)
        assertTrue(script.contains("-p tcp -m owner --uid-owner 10123 -j RETURN"))
        assertFalse(script.contains("-p udp -m owner --uid-owner 10123 -j RETURN"))
        assertTrue(script.contains("-o lo -j RETURN"))
    }

    @Test fun dnsExceptionsAreRestrictedToSystemOwners() {
        val script = RootWebRtcRules.apply(10123)
        assertTrue(script.contains("for UID in 0 1000 1051"))
        assertTrue(script.contains("--dport 53 -m owner"))
        assertFalse(script.contains("--dport 53 -j RETURN"))
    }

    @Test fun buildsBothChainsBeforeAttachingEither() {
        val script = RootWebRtcRules.apply(10123)
        assertTrue(script.indexOf("-p tcp -j REJECT") < script.indexOf("-I OUTPUT"))
        assertFalse(script.contains("-F ${RootWebRtcRules.CHAIN}"))
    }

    @Test fun restoreChecksForLeftoversAndDoesNotFlushOtherRules() {
        val script = RootWebRtcRules.restore
        assertTrue(script.contains("-D OUTPUT -j ${RootWebRtcRules.CHAIN}"))
        assertTrue(script.contains("-X ${RootWebRtcRules.CHAIN}"))
        assertTrue(script.contains("FAILED=1"))
        assertTrue(script.contains("RULES="))
        assertFalse(script.contains("-F OUTPUT"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSystemUidAsUpstreamBypass() {
        RootWebRtcRules.apply(1000)
    }
}
