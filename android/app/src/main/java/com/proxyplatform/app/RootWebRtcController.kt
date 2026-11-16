package com.proxyplatform.app

import android.content.Context
import android.os.Process
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Temporary device-wide OUTPUT firewall, not a browser WebRTC preference.
 * Only this app's TCP upstream, loopback, system DNS and DHCP may bypass it.
 * Arbitrary direct TCP is blocked too: TURN/TLS need not use a well-known port.
 */
internal object RootWebRtcController {
    private const val PREFS = "root_webrtc_firewall"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ENABLED = "enabled"
    private const val COMMAND_TIMEOUT_SECONDS = 45L

    /** Does not execute su or show a permission dialog. Hidden root may be undetectable. */
    fun isRootAvailable(): Boolean {
        val directories = System.getenv("PATH").orEmpty().split(File.pathSeparator) +
            listOf("/system/bin", "/system/xbin", "/sbin", "/su/bin", "/debug_ramdisk")
        return directories.any { File(it, "su").canExecute() }
    }

    fun requestRoot(): Result<Unit> = runCatching {
        check(runAsRoot("id -u").lineSequence().any { it.trim() == "0" }) {
            "لم يتم منح التطبيق صلاحية الروت."
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).commit()) { "تعذر حفظ خيار الحماية العالية." }
    }

    fun hasPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    @Synchronized
    fun apply(context: Context): Result<Unit> = runCatching {
        requestRoot().getOrThrow()
        val state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(state.edit().putBoolean(KEY_ACTIVE, true).commit()) {
            "تعذر حفظ حالة الاستعادة قبل تفعيل الحماية."
        }
        try {
            runAsRoot(RootWebRtcRules.apply(Process.myUid()))
        } catch (failure: Throwable) {
            val cleanupFailure = restore(context).exceptionOrNull()
            if (cleanupFailure != null) {
                throw IllegalStateException(
                    "فشل تفعيل الحماية وتعذرت إزالة بعض القواعد. أعد منح الروت ثم اضغط استعادة الإعدادات.",
                    failure,
                )
            }
            throw failure
        }
    }

    @Synchronized
    fun restore(context: Context): Result<Unit> = runCatching {
        if (!hasPendingRestore(context)) return@runCatching
        runAsRoot(RootWebRtcRules.restore)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVE, false).commit()) {
            "تمت إزالة القواعد لكن تعذر تحديث حالة الاستعادة المحفوظة."
        }
    }

    private fun runAsRoot(script: String): String {
        // Drain concurrently: waiting before reading can deadlock on a full pipe.
        val output = StringBuilder()
        val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < 8192) output.append(line.take(512)).append('\n')
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        try {
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("انتهت مهلة الروت؛ وافق على الطلب ثم حاول مجددًا.")
            }
            reader.join(1000)
            check(process.exitValue() == 0) {
                synchronized(output) { output.toString().trim() }.ifBlank {
                    "رفض su الأمر أو فشل تطبيق قواعد الحماية (exit ${process.exitValue()})."
                }
            }
            return synchronized(output) { output.toString().trim() }
        } finally {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
