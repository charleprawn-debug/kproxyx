package com.proxyplatform.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

class ProxyFailureMessagesLocalizationTest {
    @Test
    fun arabicAndEnglishMessagesKeepTheSameAuthenticationAdvice() {
        val previous = AppLanguage.isArabic
        try {
            AppLanguage.isArabic = true
            val arabicDetail = ProxyEndpointProbe.check("example.com", 8080, "http", "user", "", 100)
                .exceptionOrNull()?.message
            val arabic = ProxyFailureMessages.connection(false, arabicDetail)
            assertTrue(arabic.contains("اسم المستخدم وكلمة المرور"))
            assertTrue(arabic.contains("السبب:"))

            AppLanguage.isArabic = false
            val englishDetail = ProxyEndpointProbe.check("example.com", 8080, "http", "user", "", 100)
                .exceptionOrNull()?.message
            val english = ProxyFailureMessages.connection(false, englishDetail)
            assertTrue(english.contains("username and password"))
            assertTrue(english.contains("Reason:"))
            assertTrue(english.contains("Action:"))
            assertFalse(english.contains("السبب:"))
        } finally {
            AppLanguage.isArabic = previous
        }
    }

    @Test
    fun formattedErrorsAreNotReformattedInEitherLanguage() {
        val previous = AppLanguage.isArabic
        try {
            for (arabic in listOf(true, false)) {
                AppLanguage.isArabic = arabic
                val message = ProxyFailureMessages.connection(false, "authentication failed")
                assertEquals(message, ProxyFailureMessages.connection(false, message))
            }
        } finally {
            AppLanguage.isArabic = previous
        }
    }

    @Test
    fun dynamicPageNamesArePreserved() {
        val previous = AppLanguage.isArabic
        try {
            AppLanguage.isArabic = false
            val pageName = "Custom user-supplied page"
            assertTrue(ProxyFailureMessages.settingsPage(pageName).contains(pageName))
        } finally {
            AppLanguage.isArabic = previous
        }
    }

    @Test
    fun englishHttp403RetainsTheAccessPolicyClassification() {
        val previous = AppLanguage.isArabic
        try {
            AppLanguage.isArabic = false
            ServerSocket(0).use { server ->
                val worker = Thread {
                    server.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                        while (reader.readLine()?.isNotEmpty() == true) Unit
                        client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                        client.getOutputStream().flush()
                    }
                }.apply { isDaemon = true; start() }
                val failure = ProxyEndpointProbe.check("127.0.0.1", server.localPort, "http", "", "", 1_000)
                    .exceptionOrNull()
                worker.join(1_000)
                assertFalse(worker.isAlive)
                assertTrue(ProxyFailureMessages.connection(false, failure?.message).contains("access policy"))
            }
        } finally {
            AppLanguage.isArabic = previous
        }
    }
}
