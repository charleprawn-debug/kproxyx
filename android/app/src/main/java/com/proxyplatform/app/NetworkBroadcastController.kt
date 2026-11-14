package com.proxyplatform.app

import android.content.Context
import com.proxyplatform.app.adb.AdbPersistentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/** Controls the broadcast helper launched by the shared Wireless ADB layer. */
internal object NetworkBroadcastController {
    private const val PREFS = "network_broadcast_diagnostics"
    private const val LOG_KEY = "events"
    private const val MAX_LOG_CHARS = 60_000

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private fun session(context: Context) = AdbPersistentSession.forBroadcast(context)

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
            require(WirelessDebuggingManager.checkState(context) == WirelessDebuggingManager.DebuggingState.READY) {
                "اتصال Wireless ADB غير جاهز. افتح صفحة ADB وأعد الاتصال."
            }
            val helper = session(context)
            appendLog(context, "START requested; launching broadcast helper through Wireless ADB")
            helper.start().getOrThrow()
            val startCommand = if (ProxyVpnService.isRunning(context)) "startNever" else "start"
            appendLog(context, "starting helper session with vpnMode=$startCommand")
            val raw = helper.request(startCommand).getOrThrow()
            appendResult(context, "START", raw)
            raw
        }.onFailure { appendFailure(context, "START", it) }
    }

    suspend fun stop(context: Context): Result<String> = execute {
        runCatching {
            appendLog(context, "STOP requested")
            val helper = session(context)
            val result = helper.request("stop")
            result.onSuccess { appendResult(context, "STOP", it) }
                .onFailure { appendFailure(context, "STOP helper call", it) }
            helper.stop()
            result.getOrThrow()
        }.onFailure { appendFailure(context, "STOP", it) }
    }

    suspend fun status(context: Context): Result<String> = execute {
        runCatching {
            val raw = session(context).request("status").getOrThrow()
            appendResult(context, "STATUS", raw)
            raw
        }.onFailure { appendFailure(context, "STATUS", it) }
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
        appendLog(context, "$operation FAILED: $chain\n${failure.stackTraceToString().take(8_000)}")
    }

    private fun appendLog(context: Context, line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(LOG_KEY, "").orEmpty()
        val next = (current + "[$stamp] $line\n").takeLast(MAX_LOG_CHARS)
        prefs.edit().putString(LOG_KEY, next).apply()
    }
}
