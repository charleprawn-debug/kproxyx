package com.proxyplatform.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFailureMessagesTest {
    @Test
    fun explainsProxyAuthenticationFailureWithoutShowingTechnicalCode() {
        val message = ProxyFailureMessages.connection(false, "SOCKS authentication failed (407)")
        assertTrue(message.contains("السبب:"))
        assertTrue(message.contains("اسم المستخدم وكلمة المرور"))
        assertTrue(message.contains("الحل:"))
        assertFalse(message.contains("SOCKS authentication failed"))
    }

    @Test
    fun explainsServerTimeoutInUserTerms() {
        val message = ProxyFailureMessages.connection(true, "java.net.SocketTimeoutException: failed to connect after 5000ms")
        assertTrue(message.contains("الوضع المتقدم"))
        assertTrue(message.contains("لم يصل إلى خادم البروكسي"))
        assertTrue(message.contains("راجع عنوان الخادم والمنفذ"))
        assertFalse(message.contains("SocketTimeoutException"))
    }

    @Test
    fun explainsUnsupportedSocksUdpRelay() {
        val message = ProxyFailureMessages.connection(false, "SOCKS5 UDP ASSOCIATE was rejected")
        assertTrue(message.contains("مرحّل UDP"))
        assertTrue(message.contains("يدعم UDP"))
    }

    @Test
    fun explainsAdvancedAdbFailureAndKeepsRecoveryAction() {
        val message = ProxyFailureMessages.connection(true, "Remote shell command failed: wireless debugging disconnected")
        assertTrue(message.contains("اتصال ADB انقطع"))
        assertTrue(message.contains("إعادة الاتصال عبر ADB"))
    }

    @Test
    fun explainsMockLocationPermissionRequirement() {
        val message = ProxyFailureMessages.mockLocation("ACCESS_COARSE_LOCATION permission denied")
        assertTrue(message.contains("الموقع التقريبي"))
        assertTrue(message.contains("لا يقرأ إحداثيات موقعك الحقيقي"))
    }

    @Test
    fun explainsWirelessPairingCodeFailure() {
        val message = ProxyFailureMessages.pairing("invalid pairing code")
        assertTrue(message.contains("رمز الاقتران غير صحيح"))
        assertTrue(message.contains("الرمز الأحدث"))
    }

    @Test
    fun explainsAdvancedRestoreFailureWithoutExposingShellError() {
        val message = ProxyFailureMessages.advancedRestore("cmd alarm set-timezone exited with code 1")
        assertTrue(message.contains("لم تكتمل استعادة إعدادات الهاتف"))
        assertTrue(message.contains("المنطقة الزمنية"))
        assertTrue(message.contains("استعادة إعدادات الجلسة السابقة"))
    }

    @Test
    fun givesActionableFallbackForUnknownTechnicalFailures() {
        val message = ProxyFailureMessages.connection(false, "com.example.UnknownNativeError")
        assertTrue(message.contains("تعذّر إعداد نفق VPN"))
        assertTrue(message.contains("صدّر سجل التشخيص"))
        assertFalse(message.contains("UnknownNativeError"))
    }
}
