package com.proxyplatform.app

import java.util.Locale

/** Locale selected from the proxy exit country; callers can keep the user's language list on restore. */
internal data class ProxyLanguage(val localeTag: String, val countryCode: String)

internal object ProxyLanguageResolver {
    private val countryLocales = mapOf(
        "US" to "en-US", "CA" to "en-CA", "GB" to "en-GB", "AU" to "en-AU", "NZ" to "en-NZ",
        "DE" to "de-DE", "AT" to "de-AT", "CH" to "de-CH",
        "FR" to "fr-FR", "BE" to "fr-BE", "LU" to "fr-LU",
        "ES" to "es-ES", "MX" to "es-MX", "AR" to "es-AR", "CL" to "es-CL", "CO" to "es-CO",
        "IT" to "it-IT", "PT" to "pt-PT", "BR" to "pt-BR", "NL" to "nl-NL",
        "SE" to "sv-SE", "NO" to "nb-NO", "DK" to "da-DK", "FI" to "fi-FI",
        "PL" to "pl-PL", "CZ" to "cs-CZ", "SK" to "sk-SK", "HU" to "hu-HU",
        "RO" to "ro-RO", "BG" to "bg-BG", "GR" to "el-GR", "TR" to "tr-TR",
        "RU" to "ru-RU", "UA" to "uk-UA", "IL" to "he-IL", "IR" to "fa-IR",
        "SA" to "ar-SA", "AE" to "ar-AE", "EG" to "ar-EG", "MA" to "ar-MA",
        "IN" to "en-IN", "SG" to "en-SG", "PH" to "en-PH", "JP" to "ja-JP", "KR" to "ko-KR",
        "CN" to "zh-CN", "TW" to "zh-TW", "HK" to "zh-HK", "TH" to "th-TH", "VN" to "vi-VN",
        "ID" to "id-ID", "MY" to "ms-MY"
    )

    fun fromCountryCode(value: String): ProxyLanguage {
        val code = value.trim().uppercase(Locale.ROOT)
        return ProxyLanguage(countryLocales[code] ?: "en-US", code)
    }
}
