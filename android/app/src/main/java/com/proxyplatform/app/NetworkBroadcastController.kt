package com.proxyplatform.app

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/**
 * Keeps hotspot lifecycle behind Shizuku's shell identity. The UI never starts
 * a downstream before Shizuku is ready, so a normal app-UID hotspot cannot be
 * mistaken for the protected broadcast path.
 */
internal object NetworkBroadcastController {
    private const val COMMAND_TIMEOUT_SECONDS = 12L

    fun start(): Result<String> = runCommand(
        listOf(
            arrayOf("cmd", "tethering", "start", "wifi"),
            arrayOf("cmd", "connectivity", "tether", "start", "wifi"),
        ),
    )

    fun stop(): Result<String> = runCommand(
        listOf(
            arrayOf("cmd", "tethering", "stop", "wifi"),
            arrayOf("cmd", "connectivity", "tether", "stop", "wifi"),
        ),
    )

    fun status(): Result<String> = runCommand(
        listOf(arrayOf("cmd", "tethering", "get-tethered-ifaces")),
    )

    private fun runCommand(candidates: List<Array<String>>): Result<String> {
        val failures = mutableListOf<String>()
        for (command in candidates) {
            val result = runCatching {
                check(ShizukuGate.isReady()) { "Shizuku is not ready" }
                val process = Shizuku.newProcess(command, null, null)
                val output = ByteArrayOutputStream()
                process.inputStream.use { input -> input.copyTo(output) }
                process.errorStream.use { input -> input.copyTo(output) }
                check(process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "command timed out: ${command.joinToString(" ")}" 
                }
                val text = output.toString(StandardCharsets.UTF_8.name()).trim()
                check(process.exitValue() == 0) {
                    text.ifBlank { "shell command exited with ${process.exitValue()}" }
                }
                text
            }
            if (result.isSuccess) return result
            failures += result.exceptionOrNull()?.message ?: "unknown shell failure"
        }
        return Result.failure(IllegalStateException(failures.joinToString("; ")))
    }
}
