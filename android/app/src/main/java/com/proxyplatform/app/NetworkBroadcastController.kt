package com.proxyplatform.app

import dev.shizzi.TetherClient
import dev.shizzi.UsbTethering
import dev.shizzi.VpnMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/** Controls the embedded shizzi session instead of issuing a plain hotspot command. */
internal object NetworkBroadcastController {
    private var client = TetherClient()
    suspend fun start(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            client.unbindAndStopDaemon()
            client = TetherClient()
            client.start(
                logging = true,
                vpnMode = VpnMode.AUTO,
                usbTethering = UsbTethering.NEVER,
            )
        }
    }

    suspend fun stop(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = runCatching { client.stop() }
            client.unbindAndStopDaemon()
            result.getOrThrow()
        }
    }

    suspend fun status(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { client.status() }
    }
}
