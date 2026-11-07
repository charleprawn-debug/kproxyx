package com.proxyplatform.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyTimezoneParserTest {
    @Test
    fun acceptsAValidIanaTimezone() {
        assertEquals("Africa/Dakar", ProxyTimezoneParser.parseId(" Africa/Dakar ").id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownTimezoneIds() {
        ProxyTimezoneParser.parseId("Not/AZone")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyTimezoneIds() {
        ProxyTimezoneParser.parseId("")
    }
}
