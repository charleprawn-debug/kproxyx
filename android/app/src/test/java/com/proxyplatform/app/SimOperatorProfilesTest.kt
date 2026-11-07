package com.proxyplatform.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimOperatorProfilesTest {
    @Test
    fun allNrfrCountryCodesArePresentOnce() {
        val expected = setOf(
            "CN", "HK", "MO", "TW", "JP", "KR", "US", "GB", "DE", "FR", "IT", "ES", "PT", "RU",
            "IN", "AU", "NZ", "SG", "MY", "TH", "VN", "ID", "PH", "CA", "MX", "BR", "AR", "ZA",
        )
        assertEquals(28, SimNrfrPresets.countries.size)
        assertEquals(expected, SimNrfrPresets.countries.map { it.code }.toSet())
    }

    @Test
    fun allNrfrCarrierChoicesAndCustomEntryArePresent() {
        assertEquals(75, SimNrfrPresets.carriers.size)
        assertEquals(1, SimNrfrPresets.carriers.count { it.custom })
        assertTrue(SimNrfrPresets.carriers.any { it.displayName == "T-Mobile USA" && it.region == "US" })
        assertTrue(SimNrfrPresets.carriers.any { it.displayName == "Rogers Wireless" && it.region == "CA" })
    }

    @Test
    fun shellQuotingEscapesSingleQuotes() {
        assertEquals("'a'\\''b'", SimOperatorCommands.quote("a'b"))
    }

    @Test
    fun commandCallsTheSameApkInstrumentationSynchronously() {
        val command = SimOperatorCommands.buildInstrumentationCommand(
            packageName = "com.proxyplatform.app.debug",
            operation = "save",
            arguments = mapOf("subId" to "42", "countryCode" to "US", "carrierName" to "T-Mobile USA"),
        )
        assertTrue(command.startsWith("'am' 'instrument' '-w' '-r' '--no-restart'"))
        assertTrue(command.contains("com.proxyplatform.app.debug/com.proxyplatform.app.SimCarrierConfigInstrumentation"))
        assertTrue(command.contains("subId"))
        assertTrue(command.contains("T-Mobile USA"))
        assertTrue(!command.contains("nohup"))
        assertTrue(!command.contains("monkey -p "))
        assertTrue(!command.contains("setprop"))
        assertTrue(!command.contains("shizuku"))
    }
}
