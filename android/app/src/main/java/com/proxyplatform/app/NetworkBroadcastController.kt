package com.proxyplatform.app

import android.content.Context
import android.os.DeadObjectException
import dev.shizzi.TetherClient
import dev.shizzi.UsbTethering
import dev.shizzi.VpnMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/** Controls the embedded Shizzi session and keeps a diagnostic trace for the broadcast page. */
internal object NetworkBroadcastController {
    private const val PREFS = "network_broadcast_diagnostics"
    private const val LOG_KEY = "events"
    private const val MAX_LOG_CHARS = 60_000

    private val client = TetherClient()
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    /**
     * Run binder work outside the composable's coroutine scope. Leaving the
     * broadcast page must not cancel a Shizuku start/stop transaction midway.
     * The mutex also prevents a status poll from racing a start or stop call.
     */
    private suspend fun <T> execute(block: suspend () -> T): T =
        controllerScope.async {
            operationMutex.lock()
            try {
                block()
            } finally {
                operationMutex.unlock()
            }
        }.await()

    suspend fun start(context: Context): Result<String> = execute {
        runCatching {
            appendLog(context, "START requested; using Shizzi UserService lifecycle")
            appendLog(context, "START binding UserService (vpnMode=AUTO, usbTethering=NEVER)")
            val raw = client.start(
                logging = true,
                vpnMode = VpnMode.AUTO,
                usbTethering = UsbTethering.NEVER,
            )
            appendResult(context, "START", raw)
            raw
        }.onFailure {
            appendFailure(context, "START", it)
            client.unbindAndStopDaemon()
        }
    }

    suspend fun stop(context: Context): Result<String> = execute {
        runCatching {
            appendLog(context, "STOP requested")
            val result = runCatching { client.stop() }
            result.onSuccess { appendResult(context, "STOP", it) }
                .onFailure { appendFailure(context, "STOP service call", it) }
            client.unbindAndStopDaemon()
            appendLog(context, "STOP UserService daemon released")
            result.getOrThrow()
        }.onFailure { failure -> appendFailure(context, "STOP", failure) }
    }

    suspend fun status(context: Context): Result<String> = execute {
        runCatching {
            appendLog(context, "STATUS requested")
            val raw = client.status()
            appendResult(context, "STATUS", raw)
            raw
        }.onFailure { failure -> appendFailure(context, "STATUS", failure) }
    }

    fun logs(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LOG_KEY, "")
        .orEmpty()

    fun clearLogs(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(LOG_KEY).apply()
    }

    private fun appendResult(context: Context, operation: String, raw: String) {
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        val state = parsed?.optString("state").orEmpty()
        val detail = parsed?.optString("detail").orEmpty()
        appendLog(
            context,
            "$operation result: state=${state.ifBlank { "<none>" }}" +
                if (detail.isBlank()) " raw=$raw" else " detail=$detail",
        )
    }

    private fun appendFailure(context: Context, operation: String, failure: Throwable) {
        val chain = generateSequence(failure) { it.cause }
            .map { cause -> "${cause.javaClass.name}: ${cause.message.orEmpty()}" }
            .joinToString(" <- ")
        val deadBinderHint = if (failure.hasDeadBinder()) {
            "\nShizuku UserService binder died during $operation; inspect device logcat " +
                "(ShizukuServiceStarter, AndroidRuntime, libc, TetherService) for the remote-process exit."
        } else {
            ""
        }
        appendLog(
            context,
            "$operation FAILED: $chain$deadBinderHint\n${failure.stackTraceToString().take(8_000)}",
        )
    }

    private fun Throwable.hasDeadBinder(): Boolean =
        generateSequence(this) { it.cause }.any { it is DeadObjectException }

    private fun appendLog(context: Context, line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(LOG_KEY, "").orEmpty()
        val next = (current + "[$stamp] $line\n").takeLast(MAX_LOG_CHARS)
        prefs.edit().putString(LOG_KEY, next).apply()
    }
}
