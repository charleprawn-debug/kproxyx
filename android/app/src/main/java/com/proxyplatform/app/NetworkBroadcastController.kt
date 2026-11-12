package com.proxyplatform.app

import dev.shizzi.TetherClient
import dev.shizzi.UsbTethering
import dev.shizzi.VpnMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/** Controls the embedded shizzi session instead of issuing a plain hotspot command. */
internal object NetworkBroadcastController {
    private val client = TetherClient()
    suspend fun start(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            client.start(
                logging = true,
                vpnMode = VpnMode.AUTO,
                usbTethering = UsbTethering.NEVER,
            )
        }
    }

    suspend fun stop(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { client.stop() }
    }

    suspend fun status(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { client.status() }
    }
}
