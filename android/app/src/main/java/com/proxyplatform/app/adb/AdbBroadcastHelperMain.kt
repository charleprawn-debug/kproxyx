package com.proxyplatform.app.adb

import android.net.LocalSocket
import dev.shizzi.TetherService
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Entry point for app_process; it is launched by Wireless ADB as UID shell. */
object AdbBroadcastHelperMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val socketName = args.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: error("broadcast helper socket name is missing")
        val server = android.net.LocalServerSocket(socketName)
        val service = TetherService()
        try {
            var keepRunning = true
            while (keepRunning) {
                val client = server.accept()
                keepRunning = handle(client, service)
            }
        } finally {
            runCatching { service.stop() }
            runCatching { server.close() }
        }
    }

    private fun handle(socket: LocalSocket, service: TetherService): Boolean {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.inputStream, StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(it.outputStream, StandardCharsets.UTF_8))
            val request = reader.readLine().orEmpty()
            val command = runCatching { JSONObject(request).optString("command") }.getOrDefault("")
            val response = runCatching {
                when (command) {
                    "status" -> service.getStatus()
                    "start" -> service.start(true, "AUTO", "NEVER")
                    "startNever" -> service.start(true, "NEVER", "NEVER")
                    "stop" -> service.stop()
                    "checkCompatibility" -> service.checkCompatibility()
                    "shutdown" -> service.stop()
                    else -> error("unsupported helper command")
                }
            }.fold(
                onSuccess = { JSONObject().put("ok", true).put("result", it) },
                onFailure = { failure ->
                    JSONObject().put("ok", false).put(
                        "error",
                        "${failure.javaClass.simpleName}: ${failure.message.orEmpty()}",
                    )
                },
            )
            writer.write(response.toString())
            writer.newLine()
            writer.flush()
            return command != "shutdown"
        }
    }
}
