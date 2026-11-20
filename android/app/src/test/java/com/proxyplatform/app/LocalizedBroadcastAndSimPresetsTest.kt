package com.proxyplatform.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalizedBroadcastAndSimPresetsTest {
    @Before
    fun useDefaultLanguage() {
        AppLanguage.isArabic = true
    }

    @After
    fun restoreDefaultLanguage() {
        AppLanguage.isArabic = true
    }

    @Test
    fun broadcastMessagesSwitchLanguageWithoutChangingStateRules() {
        assertEquals("نجح تشغيل البث.", BroadcastUiMessages.operationResult("ACTIVE", false))
        assertEquals("جارٍ تجهيز البث.", BroadcastUiMessages.operationResult("STARTING", false))
        assertEquals("نجح إيقاف البث.", BroadcastUiMessages.operationResult("READY", true))

        AppLanguage.isArabic = false
        assertEquals("Broadcast started successfully.", BroadcastUiMessages.operationResult("ACTIVE", false))
        assertEquals("Preparing the broadcast.", BroadcastUiMessages.operationResult("STARTING", false))
        assertEquals("Broadcast stopped successfully.", BroadcastUiMessages.operationResult("READY", true))
        assertEquals("Broadcast stopped successfully.", BroadcastUiMessages.operationResult("IDLE", true))
        assertEquals("Could not stop the broadcast. Try again.", BroadcastUiMessages.operationResult("ACTIVE", true))
        assertEquals(
            "Could not start the broadcast. Check that the connection is ready, then try again.",
            BroadcastUiMessages.operationResult("ERROR", false),
        )

        AppLanguage.isArabic = true
        assertEquals("نجح تشغيل البث.", BroadcastUiMessages.operationResult("ACTIVE", false))
    }

    @Test
    fun adbRecoveryIsShownOnlyWhenBothServicesAreUnavailableInBothLanguages() {
        for (arabic in listOf(true, false)) {
            AppLanguage.isArabic = arabic
            assertTrue(BroadcastUiMessages.failure(false, false).contains("ADB"))
            assertFalse(BroadcastUiMessages.failure(true, false).contains("ADB"))
            assertFalse(BroadcastUiMessages.failure(false, true).contains("ADB"))
            assertFalse(BroadcastUiMessages.failure(true, true).contains("ADB"))
        }
        assertEquals(
            "The broadcast service is unavailable. Open the ADB page, reconnect, and try again.",
            BroadcastUiMessages.failure(false, false),
        )
        assertEquals("Could not run the broadcast command. Try again.", BroadcastUiMessages.failure(true, false))
    }

    @Test
    fun broadcastNeverEchoesUntrustedStateInEitherLanguage() {
        val payload = "secret-value /data/local/tmp/internal.log 192.0.2.10 java.lang.Exception\nstack"
        for (arabic in listOf(true, false)) {
            AppLanguage.isArabic = arabic
            for (stopping in listOf(true, false)) {
                val fallback = BroadcastUiMessages.operationResult("ERROR", stopping)
                for (state in listOf(payload, "", null)) {
                    val message = BroadcastUiMessages.operationResult(state, stopping)
                    assertEquals(fallback, message)
                    for (secret in listOf("secret-value", "/data", "192.0.2.10", "Exception", "\n")) {
                        assertFalse(message.contains(secret))
                    }
                }
            }
        }
    }

    @Test
    fun countryLabelsAndOrderFollowTheCurrentLanguageWithoutChangingCodes() {
        val arabic = SimNrfrPresets.countries
        assertEquals("الصين", arabic.first { it.code == "CN" }.label)
        assertEquals("الولايات المتحدة", arabic.first { it.code == "US" }.label)
        assertEquals(arabic.map { it.label }.sorted(), arabic.map { it.label })

        AppLanguage.isArabic = false
        val english = SimNrfrPresets.countries
        val expected = mapOf(
            "CN" to "China", "HK" to "Hong Kong", "MO" to "Macau", "TW" to "Taiwan",
            "JP" to "Japan", "KR" to "South Korea", "US" to "United States", "GB" to "United Kingdom",
            "DE" to "Germany", "FR" to "France", "IT" to "Italy", "ES" to "Spain",
            "PT" to "Portugal", "RU" to "Russia", "IN" to "India", "AU" to "Australia",
            "NZ" to "New Zealand", "SG" to "Singapore", "MY" to "Malaysia", "TH" to "Thailand",
            "VN" to "Vietnam", "ID" to "Indonesia", "PH" to "Philippines", "CA" to "Canada",
            "MX" to "Mexico", "BR" to "Brazil", "AR" to "Argentina", "ZA" to "South Africa",
        )
        assertEquals(expected, english.associate { it.code to it.label })
        assertEquals(expected.size, english.size)
        assertEquals(arabic.map { it.code }.toSet(), english.map { it.code }.toSet())
        assertEquals(english.map { it.label }.sorted(), english.map { it.label })

        AppLanguage.isArabic = true
        assertEquals(arabic, SimNrfrPresets.countries)
    }

    @Test
    fun carrierTechnicalValuesDoNotChangeWithLanguage() {
        val arabic = SimNrfrPresets.carriers
        val arabicCustom = arabic.single { it.custom }
        assertEquals(SimCarrierPreset("custom", "مخصص", "", "", custom = true), arabicCustom)
        assertEquals(
            SimCarrierPreset("us_tmobile", "T-Mobile", "T-Mobile USA", "US"),
            arabic.first { it.id == "us_tmobile" },
        )

        AppLanguage.isArabic = false
        val english = SimNrfrPresets.carriers
        assertEquals(arabic.filterNot { it.custom }, english.filterNot { it.custom })
        assertEquals(SimCarrierPreset("custom", "Custom", "", "", custom = true), english.single { it.custom })
        assertEquals(arabic.map { it.id }, english.map { it.id })

        AppLanguage.isArabic = true
        assertEquals(arabic, SimNrfrPresets.carriers)
    }
}
