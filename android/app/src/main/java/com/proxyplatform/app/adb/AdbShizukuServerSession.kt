package com.proxyplatform.app.adb

import android.content.Context
import android.annotation.SuppressLint
import com.proxyplatform.app.BuildConfig
import com.proxyplatform.app.EmbeddedAdbManager
import com.proxyplatform.embeddedshizuku.EmbeddedServerCommand
import kotlinx.coroutines.delay
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
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

    suspend fun start(): Result<Unit> = runCatching {
        // Reuse a live server; restarting it here would destroy an active
        // TetherService and its hotspot when the user presses Start again.
        if (isReady()) return@runCatching
        check(adb.isConnected()) { "Wireless ADB غير متصل" }
        stopRemoteProcess()
        val packageName = BuildConfig.APPLICATION_ID
        val command = EmbeddedServerCommand.start(packageName)
        val launch = adb.execute(command).getOrThrow()
        check(launch.contains("Embedded server detached; pid=")) {
            "تعذر فصل السيرفر عن جلسة ADB:\n$launch"
        }
        check(waitForBinder()) {
            val serverLog = readRemoteLog().getOrDefault("<تعذر قراءة سجل server>")
            "انتهت مهلة انتظار Binder\n$serverLog"
        }
    }

    suspend fun stop(): Result<Unit> = runCatching {
        stopRemoteProcess()
        check(waitForBinder(expected = false)) { "Binder ما زال ظاهراً بعد إيقاف server" }
    }

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun readRemoteLog(): Result<String> {
        val command = """
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
        // Binder stays available after Wireless ADB disappears. Use the
        // embedded server's existing, permission-checked process API to read
        // diagnostics without reconnecting Wi-Fi or requiring external apps.
        val result = if (isReady()) runCatching {
            val process = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                .newProcess(arrayOf("/system/bin/sh", "-c", command), null, null)
            try {
                ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                    .bufferedReader().use { it.readText() }
            } finally {
                runCatching { process.destroy() }
            }
        } else adb.execute(command)
        val prefs = context.getSharedPreferences("embedded_server_diagnostics", Context.MODE_PRIVATE)
        result.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            prefs.edit().putString("last_log", it).apply()
        }
        return result.recoverCatching {
            val cached = prefs.getString("last_log", "").orEmpty()
            check(cached.isNotBlank()) { "السيرفر وADB غير متاحين لقراءة السجل" }
            "--- آخر سجل محفوظ؛ تعذر الاتصال بالسيرفر وADB حالياً ---\n$cached"
        }
    }

    // This application intentionally implements Shizuku's manager role.
    @SuppressLint("RestrictedApi")
    private suspend fun stopRemoteProcess() {
        if (isReady()) {
            runCatching { Shizuku.exit() }
            // exit() can close the Binder before replying. Its death, rather
            // than the RPC result or a stale ADB connection flag, proves stop.
            if (waitForBinder(expected = false)) return
        }
        if (adb.isConnected()) adb.execute(EmbeddedServerCommand.stop(BuildConfig.APPLICATION_ID)).getOrThrow()
        waitForBinder(expected = false)
    }

    private suspend fun waitForBinder(expected: Boolean = true): Boolean {
        repeat(BINDER_WAIT_ATTEMPTS) {
            if (isReady() == expected) return true
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
