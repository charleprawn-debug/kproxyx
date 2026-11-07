package com.proxyplatform.app.adb

/** Normalizes a six-digit Android Wireless Debugging pairing code without storing or logging it. */
internal object PairingCodeInput {
    fun digitsOnly(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                in '0'..'9' -> append(character)
                in '\u0660'..'\u0669' -> append(('0'.code + (character.code - '\u0660'.code)).toChar())
                in '\u06F0'..'\u06F9' -> append(('0'.code + (character.code - '\u06F0'.code)).toChar())
            }
        }
    }

    fun normalize(value: String): String? = digitsOnly(value).takeIf { it.length == 6 }
}
