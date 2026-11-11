package com.proxyplatform.app

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Runs the tethering command from the Shizuku shell-UID UserService. */
class BroadcastShellService : IBroadcastShellService.Stub() {
    override fun startBroadcast(): String = runCommand(arrayOf("cmd", "tethering", "start", "wifi"))
    override fun stopBroadcast(): String = runCommand(arrayOf("cmd", "tethering", "stop", "wifi"))
    override fun getStatus(): String = runCommand(arrayOf("cmd", "tethering", "get-tethered-ifaces"))

    private fun runCommand(command: Array<String>): String {
        val process = Runtime.getRuntime().exec(command)
        val output = ByteArrayOutputStream()
        process.inputStream.use { it.copyTo(output) }
        process.errorStream.use { it.copyTo(output) }
        check(process.waitFor(12, TimeUnit.SECONDS)) { "command timed out" }
        val text = output.toString(StandardCharsets.UTF_8.name()).trim()
        check(process.exitValue() == 0) { text.ifBlank { "shell command failed" } }
        return text
    }
}
