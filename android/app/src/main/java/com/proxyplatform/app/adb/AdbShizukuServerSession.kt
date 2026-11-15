package com.proxyplatform.app.adb

import android.content.Context
import com.proxyplatform.app.BuildConfig
import com.proxyplatform.app.EmbeddedAdbManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * Starts the embedded Shizuku-compatible server as a separate app_process under
 * UID shell. The application process never pretends to be privileged; it only
 * receives the server Binder through its exported ShizukuProvider.
 */
internal class AdbShizukuServerSession private constructor(
    private val context: Context,
) {
    private val adb get() = EmbeddedAdbManager.get(context)
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var serverStream: AdbStream? = null
    @Volatile private var serverReaderJob: Job? = null

    suspend fun start(): Result<Unit> = runCatching {
        check(adb.isConnected()) { "Wireless ADB غير متصل" }
        stopRemoteProcess()
        val packageName = BuildConfig.APPLICATION_ID
        val command = """
            set -eu
            APK=${'$'}(pm path '$packageName' | sed -n 's/^package://p' | head -n 1)
            test -n "${'$'}APK"
            ABI=${'$'}(getprop ro.product.cpu.abilist | cut -d, -f1)
            case "${'$'}ABI" in
                arm64-v8a) ABI_DIR=arm64 ;;
                armeabi-v7a) ABI_DIR=arm ;;
                x86_64) ABI_DIR=x86_64 ;;
                x86) ABI_DIR=x86 ;;
                *) ABI_DIR=arm64 ;;
            esac
            LIB_DIR=${'$'}(dirname "${'$'}APK")/lib/${'$'}ABI_DIR
            LOG=/data/local/tmp/$LOG_BASENAME
            PIDFILE=/data/local/tmp/$PID_BASENAME
            rm -f "${'$'}PIDFILE"
            exec sh -c 'echo ${'$'}${'$'} > "${'$'}2"; /system/bin/app_process -Djava.class.path="${'$'}1" -Dshizuku.library.path="${'$'}3" -Dkproxyx.manager.package=$packageName /system/bin --nice-name=shizuku_server rikka.shizuku.server.ShizukuService; RC=${'$'}?; echo "app_process exit=${'$'}RC" >&2; exit ${'$'}RC' sh "${'$'}APK" "${'$'}PIDFILE" "${'$'}LIB_DIR" >"${'$'}LOG" 2>&1
        """.trimIndent()
        closeServerStream()
        val stream = adb.openPersistentShell(command).getOrThrow()
        serverStream = stream
        serverReaderJob = serverScope.launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive && stream.read(buffer, 0, buffer.size) >= 0) Unit
            } catch (_: Throwable) {
                // The stream normally ends when the server is stopped.
            }
        }
        delay(1000)
        // The provider is the official Shizuku hand-off.  Calling this after
        // app_process has started also covers the case where the provider was
        // not instantiated when the server's first sendBinder call happened.
        runCatching { ShizukuProvider.requestBinderForNonProviderProcess(context) }
        check(waitForBinder()) {
            val serverLog = readRemoteLog().getOrDefault("<تعذر قراءة سجل server>")
            "Shizuku server لم يسلّم Binder خلال المهلة. persistent shell open\n$serverLog"
        }
    }

    suspend fun stop(): Result<Unit> = runCatching {
        stopRemoteProcess()
        check(waitForBinder(expected = false)) { "Binder ما زال ظاهراً بعد إيقاف server" }
    }

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun readRemoteLog(): Result<String> = adb.execute(
        """
        echo '--- stdout/stderr ---'
        cat /data/local/tmp/$LOG_BASENAME 2>/dev/null || true
        echo '--- process ---'
        if [ -f /data/local/tmp/$PID_BASENAME ]; then
            P=${'$'}(cat /data/local/tmp/$PID_BASENAME)
            ps -A -o PID,PPID,UID,NAME,ARGS 2>/dev/null | grep -E "(^|[[:space:]])${'$'}P([[:space:]]|$)|app_process|$LOG_BASENAME" || true
        fi
        echo '--- logcat ---'
        logcat -d -t 300 -v brief \
          -s ShizukuServer:V ShizukuProvider:V AndroidRuntime:E ActivityManager:E libc:F '*:S' \
          2>/dev/null || true
        """.trimIndent()
    )

    private suspend fun stopRemoteProcess() {
        closeServerStream()
        adb.execute(
            "if [ -f /data/local/tmp/$PID_BASENAME ]; then kill ${'$'}(cat /data/local/tmp/$PID_BASENAME) 2>/dev/null || true; fi; rm -f /data/local/tmp/$PID_BASENAME"
        )
        waitForBinder(expected = false)
    }

    private fun closeServerStream() {
        serverReaderJob?.cancel()
        serverReaderJob = null
        runCatching { serverStream?.close() }
        serverStream = null
    }

    private suspend fun waitForBinder(expected: Boolean = true): Boolean {
        repeat(BINDER_WAIT_ATTEMPTS) {
            if (isReady() == expected) return true
            delay(BINDER_WAIT_DELAY_MS)
        }
        return isReady() == expected
    }

    companion object {
        private const val LOG_BASENAME = "kproxyx-shizuku-server.log"
        private const val PID_BASENAME = "kproxyx-shizuku-server.pid"
        private const val BINDER_WAIT_ATTEMPTS = 50
        private const val BINDER_WAIT_DELAY_MS = 300L

        @Volatile private var instance: AdbShizukuServerSession? = null

        fun get(context: Context): AdbShizukuServerSession = instance ?: synchronized(this) {
            instance ?: AdbShizukuServerSession(context.applicationContext).also { instance = it }
        }
    }
}
