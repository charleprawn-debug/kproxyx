package com.proxyplatform.app.adb

import android.content.Context
import com.proxyplatform.app.BuildConfig
import com.proxyplatform.app.EmbeddedAdbManager
import com.proxyplatform.embeddedshizuku.EmbeddedServerCommand
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

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
    @Volatile private var streamEnded = false

    suspend fun start(): Result<Unit> = runCatching {
        // Reuse a live server; restarting it here would destroy an active
        // TetherService and its hotspot when the user presses Start again.
        if (isReady()) return@runCatching
        check(adb.isConnected()) { "Wireless ADB غير متصل" }
        stopRemoteProcess()
        val packageName = BuildConfig.APPLICATION_ID
        val command = EmbeddedServerCommand.start(packageName)
        closeServerStream()
        streamEnded = false
        val stream = adb.openPersistentShell(command).getOrThrow()
        serverStream = stream
        serverReaderJob = serverScope.launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive && stream.read(buffer, 0, buffer.size) >= 0) Unit
            } catch (_: Throwable) {
                // The stream normally ends when the server is stopped.
            } finally {
                if (serverStream === stream) streamEnded = true
            }
        }
        check(waitForBinder(stopWhenStreamEnds = true)) {
            val serverLog = readRemoteLog().getOrDefault("<تعذر قراءة سجل server>")
            val reason = if (streamEnded) "انتهت عملية السيرفر قبل تسليم Binder" else "انتهت مهلة انتظار Binder"
            "$reason\n$serverLog"
        }
    }.onFailure {
        closeServerStream()
    }

    suspend fun stop(): Result<Unit> = runCatching {
        stopRemoteProcess()
        check(waitForBinder(expected = false)) { "Binder ما زال ظاهراً بعد إيقاف server" }
    }

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun readRemoteLog(): Result<String> = adb.execute(
        """
        echo '--- stdout/stderr ---'
        cat '${EmbeddedServerCommand.logPath(BuildConfig.APPLICATION_ID)}' 2>/dev/null || true
        echo '--- UserService ---'
        tail -n 100 '/data/local/tmp/kproxyx-${BuildConfig.APPLICATION_ID}-userservice.log' 2>/dev/null || true
        echo '--- process ---'
        ps -A -o PID,PPID,UID,NAME 2>/dev/null | grep -F '${EmbeddedServerCommand.processName(BuildConfig.APPLICATION_ID)}' || true
        echo '--- logcat ---'
        logcat -d -t 150 -v brief \
          -s KProxyXShizuku:V Service:V ShizukuServiceStarter:V UserServiceManager:V ShizukuProvider:V AndroidRuntime:E libc:F '*:S' \
          2>/dev/null || true
        """.trimIndent()
    )

    private suspend fun stopRemoteProcess() {
        if (isReady()) runCatching { Shizuku.exit() }
        if (adb.isConnected()) adb.execute(EmbeddedServerCommand.stop(BuildConfig.APPLICATION_ID)).getOrThrow()
        closeServerStream()
        waitForBinder(expected = false)
    }

    private fun closeServerStream() {
        serverReaderJob?.cancel()
        serverReaderJob = null
        runCatching { serverStream?.close() }
        serverStream = null
    }

    private suspend fun waitForBinder(expected: Boolean = true, stopWhenStreamEnds: Boolean = false): Boolean {
        repeat(BINDER_WAIT_ATTEMPTS) {
            if (isReady() == expected) return true
            if (expected && stopWhenStreamEnds && streamEnded) return false
            delay(BINDER_WAIT_DELAY_MS)
        }
        return isReady() == expected
    }

    companion object {
        private const val BINDER_WAIT_ATTEMPTS = 50
        private const val BINDER_WAIT_DELAY_MS = 300L

        @Volatile private var instance: AdbShizukuServerSession? = null

        fun get(context: Context): AdbShizukuServerSession = instance ?: synchronized(this) {
            instance ?: AdbShizukuServerSession(context.applicationContext).also { instance = it }
        }
    }
}
