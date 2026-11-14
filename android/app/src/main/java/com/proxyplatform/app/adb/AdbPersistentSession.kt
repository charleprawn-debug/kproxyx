package com.proxyplatform.app.adb

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.proxyplatform.app.BuildConfig
import com.proxyplatform.app.EmbeddedAdbManager
import com.proxyplatform.app.AdvancedOperationLog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Runs a long-lived helper started by the already-authenticated Wireless ADB
 * shell. Existing one-shot ADB commands do not use this class.
 */
internal class AdbPersistentSession private constructor(
    private val context: Context,
    private val socketName: String,
    private val processCommand: String,
    private val helperLogPath: String,
) {
    @Volatile
    private var processId: Long? = null

    @Synchronized
    fun start(timeoutMs: Long = START_TIMEOUT_MS): Result<Unit> = runCatching {
        if (isAlive()) return@runCatching
        val output = executeAdb(processCommand)
        processId = output.lineSequence().firstNotNullOfOrNull { it.trim().toLongOrNull() }
        waitForSocket(timeoutMs)
        AdvancedOperationLog.info(context, "تم تشغيل جلسة ADB المستمرة.")
    }.onFailure {
        processId = null
        helperLog().takeIf { it.isNotBlank() }?.let { log ->
            AdvancedOperationLog.error(context, "broadcast helper stderr:\n$log")
        }
        AdvancedOperationLog.exception(context, "فشل تشغيل جلسة ADB المستمرة", it)
    }

    @Synchronized
    fun request(command: String, timeoutMs: Long = REQUEST_TIMEOUT_MS): Result<String> = runCatching {
        require(command.matches(REQUEST_PATTERN)) { "أمر جلسة ADB غير مدعوم." }
        check(isAlive()) { "جلسة ADB المستمرة غير متصلة." }
        withSocket(timeoutMs) { reader, writer ->
            writer.write(JSONObject().put("command", command).toString())
            writer.newLine()
            writer.flush()
            val line = reader.readLine() ?: error("انقطعت جلسة ADB المستمرة قبل الرد.")
            val response = JSONObject(line)
            check(response.optBoolean("ok", false)) {
                response.optString("error").ifBlank { "فشل تنفيذ عملية الجلسة." }
            }
            response.optString("result").also { result ->
                AdvancedOperationLog.output(context, "broadcast helper $command -> $result")
            }
        }
    }

    @Synchronized
    fun stop(): Result<Unit> = runCatching {
        if (!isAlive()) return@runCatching
        request("shutdown").getOrThrow()
        processId = null
        AdvancedOperationLog.info(context, "تم إيقاف جلسة ADB المستمرة.")
    }.onFailure {
        AdvancedOperationLog.exception(context, "فشل إيقاف جلسة ADB المستمرة", it)
    }

    fun isAlive(): Boolean = runCatching {
        withSocket(STATUS_TIMEOUT_MS) { reader, writer ->
            writer.write(JSONObject().put("command", "status").toString())
            writer.newLine()
            writer.flush()
            val line = reader.readLine() ?: return@withSocket false
            val response = JSONObject(line)
            response.optBoolean("ok", false)
        }
    }.getOrDefault(false)

    fun helperLog(): String = runCatching {
        executeAdb("cat '$helperLogPath' 2>/dev/null || true")
    }.getOrDefault("")

    private fun waitForSocket(timeoutMs: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var lastFailure: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                if (isAlive()) return
            } catch (failure: Throwable) {
                lastFailure = failure
            }
            Thread.sleep(SOCKET_POLL_MS)
        }
        error("لم تستجب جلسة ADB خلال ${timeoutMs}ms${lastFailure?.let { ": ${it.message}"}.orEmpty()}")
    }

    private fun <T> withSocket(timeoutMs: Long, block: (BufferedReader, BufferedWriter) -> T): T {
        val socket = LocalSocket()
        socket.soTimeout = timeoutMs.toInt().coerceAtLeast(1)
        socket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.inputStream, StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(it.outputStream, StandardCharsets.UTF_8))
            return block(reader, writer)
        }
    }

    private fun executeAdb(command: String): String {
        val adb = EmbeddedAdbManager.get(context)
        if (!adb.isConnected()) {
            adb.connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
        }
        return adb.execute(command).getOrThrow().also { output ->
            AdvancedOperationLog.command(context, command)
            AdvancedOperationLog.output(context, output)
        }
    }

    companion object {
        private const val START_TIMEOUT_MS = 15_000L
        private const val REQUEST_TIMEOUT_MS = 45_000L
        private const val STATUS_TIMEOUT_MS = 2_000L
        private const val SOCKET_POLL_MS = 150L
        private val REQUEST_PATTERN = Regex("(start|startNever|stop|status|checkCompatibility|shutdown)")

        fun forBroadcast(context: Context): AdbPersistentSession {
            val packageName = BuildConfig.APPLICATION_ID
            val safeId = packageName.replace(Regex("[^A-Za-z0-9_]"), "_")
            val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            val token = prefs.getString(SESSION_TOKEN, null) ?: ByteArray(18).also {
                SecureRandom().nextBytes(it)
            }.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }.also {
                prefs.edit().putString(SESSION_TOKEN, it).apply()
            }
            val socket = "kuncloak_broadcast_${safeId}_$token"
            val logPath = "/data/local/tmp/kuncloak_broadcast_$safeId.log"
            val helper = "com.proxyplatform.app.adb.AdbBroadcastHelperMain"
            val dollar = '$'
            val command =
                "apk=${dollar}(pm path '$packageName' | sed -n 's/^package://p' | head -n 1); export apk; " +
                    "test -n \"${dollar}apk\" || { echo ADB_HELPER_APK_NOT_FOUND >&2; exit 1; }; " +
                    "setsid sh -c 'trap \"\" HUP; echo HELPER_SHELL_STARTED >&2; " +
                    "exec env CLASSPATH=\"${dollar}apk\" app_process /system/bin $helper $socket' " +
                    ">$logPath 2>&1 </dev/null & echo ${dollar}!"
            return AdbPersistentSession(context.applicationContext, socket, command, logPath)
        }

        private const val SESSION_PREFS = "adb_persistent_sessions"
        private const val SESSION_TOKEN = "broadcast_socket_token"
    }
}
