package com.proxyplatform.app

import dev.shizzi.TetherClient
import dev.shizzi.UsbTethering
import dev.shizzi.VpnMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Controls the embedded shizzi session instead of issuing a plain hotspot command. */
internal object NetworkBroadcastController {
    private val client = TetherClient()

    suspend fun start(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = client.start(
                logging = true,
                vpnMode = VpnMode.ALWAYS,
                usbTethering = UsbTethering.NEVER,
            )
            val state = runCatching { JSONObject(raw).optString("state") }.getOrDefault("")
            if (state != "ACTIVE") {
                val detail = runCatching { JSONObject(raw).optString("detail") }
                    .getOrDefault(raw)
                    .ifBlank { raw }
                error("لم تبدأ جلسة البث المحمية: $detail")
            }
            val vpnBound = runCatching { JSONObject(raw).optBoolean("isVpnBound") }.getOrDefault(false)
            if (!vpnBound) {
                runCatching { client.stop() }
                error(
                    "تم رفض البث لأن جلسة TUN ليست مربوطة بـVPN البروكسي؛ " +
                        "تم إيقاف الجلسة لمنع التسريب.",
                )
            }
            raw
        }
    }

    suspend fun stop(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { client.stop() }
    }

    suspend fun status(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { client.status() }
    }
}
