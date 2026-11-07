package com.proxyplatform.app

import com.proxyplatform.app.adb.PairingCodeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingCodeInputTest {
    @Test
    fun acceptsSixAsciiDigitsAndIgnoresFormatting() {
        assertEquals("123456", PairingCodeInput.normalize("123 456"))
        assertEquals("123456", PairingCodeInput.normalize("12-34-56"))
    }

    @Test
    fun convertsArabicAndPersianDigitsToAsciiForWirelessAdb() {
        assertEquals("123456", PairingCodeInput.normalize("١٢٣٤٥٦"))
        assertEquals("123456", PairingCodeInput.normalize("۱۲۳۴۵۶"))
    }

    @Test
    fun rejectsIncompleteOrOverlongPairingCodes() {
        assertNull(PairingCodeInput.normalize("12345"))
        assertNull(PairingCodeInput.normalize("1234567"))
        assertNull(PairingCodeInput.normalize(""))
    }
}
