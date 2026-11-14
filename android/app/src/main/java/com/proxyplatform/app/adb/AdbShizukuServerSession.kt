package com.proxyplatform.app.adb

import android.content.Context
import com.proxyplatform.app.BuildConfig
import com.proxyplatform.app.EmbeddedAdbManager
import kotlinx.coroutines.delay
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
        check(adb.isConnected()) { "Wireless ADB غير متصل" }
        stopRemoteProcess()
        val packageName = BuildConfig.APPLICATION_ID
        val command = """
            set -eu
            APK=${'$'}(pm path '$packageName' | sed -n 's/^package://p' | head -n 1)
            test -n "${'$'}APK"
            LOG=/data/local/tmp/$LOG_BASENAME
            PIDFILE=/data/local/tmp/$PID_BASENAME
            rm -f "${'$'}PIDFILE"
            nohup sh -c "CLASSPATH=${'$'}APK app_process -Dkproxyx.manager.package=$packageName /system/bin --nice-name=$packageName:shizuku rikka.shizuku.server.ShizukuService" >"${'$'}LOG" 2>&1 &
            echo ${'$'}! >"${'$'}PIDFILE"
            sleep 1
            kill -0 ${'$'}(cat "${'$'}PIDFILE")
        """.trimIndent()
        val output = adb.execute(command).getOrThrow()
        check(waitForBinder()) {
            val serverLog = readRemoteLog().getOrDefault("<تعذر قراءة سجل server>")
            "Shizuku server لم يسلّم Binder خلال المهلة. output=$output\n$serverLog"
        }
    }

    suspend fun stop(): Result<Unit> = runCatching {
        stopRemoteProcess()
        check(waitForBinder(expected = false)) { "Binder ما زال ظاهراً بعد إيقاف server" }
    }

    fun isReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun readRemoteLog(): Result<String> = adb.execute(
        "cat /data/local/tmp/$LOG_BASENAME 2>/dev/null || true"
    )

    private suspend fun stopRemoteProcess() {
        adb.execute(
            "if [ -f /data/local/tmp/$PID_BASENAME ]; then kill ${'$'}(cat /data/local/tmp/$PID_BASENAME) 2>/dev/null || true; fi; rm -f /data/local/tmp/$PID_BASENAME"
        )
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
