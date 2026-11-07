package com.proxyplatform.app

import java.time.ZoneId
import org.json.JSONObject

/** Validated timezone selected from the proxy exit's GeoIP response. */
internal data class ProxyTimezone(val id: String)

internal object ProxyTimezoneParser {
    fun parse(json: JSONObject): ProxyTimezone = parseId(
        json.optJSONObject("timezone")?.optString("id").orEmpty()
    )

    internal fun parseId(value: String): ProxyTimezone {
        val id = value.trim()
        require(id.isNotEmpty() && id in ZoneId.getAvailableZoneIds()) {
            "لم يحدد مزود الموقع منطقة زمنية صالحة للبروكسي."
        }
        return ProxyTimezone(id)
    }
}
