package com.proxyplatform.app

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Applies a temporary device-wide firewall policy for advanced-mode WebRTC
 * protection. All commands are static; no user-controlled values enter a shell.
 */
internal object RootWebRtcController {
    private const val PREFS = "root_webrtc_firewall"
    private const val KEY_ACTIVE = "active"
    private const val CHAIN = "KPX_WEBRTC_BLOCK"
    private const val COMMAND_TIMEOUT_SECONDS = 20L

    fun hasRoot(): Boolean = runCatching {
        val output = runAsRoot("id -u")
        output.lineSequence().any { it.trim() == "0" }
    }.getOrDefault(false)

    fun hasPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun apply(context: Context): Result<Unit> = runCatching {
        check(hasRoot()) { "لم يتم منح التطبيق صلاحية الروت." }
        val state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(state.edit().putBoolean(KEY_ACTIVE, true).commit()) {
            "تعذر حفظ حالة الاستعادة قبل تفعيل حجب WebRTC."
        }
        try {
            runAsRoot(APPLY_SCRIPT)
        } catch (failure: Throwable) {
            val cleanupFailure = runCatching { runAsRoot(RESTORE_SCRIPT) }.exceptionOrNull()
            if (cleanupFailure != null) {
                throw IllegalStateException(
                    "فشل تفعيل حجب WebRTC، كما تعذرت استعادة قواعد الجدار الناري: ${cleanupFailure.message}",
                    failure,
                )
            }
            state.edit().putBoolean(KEY_ACTIVE, false).commit()
            throw failure
        }
    }

    fun restore(context: Context): Result<Unit> = runCatching {
        if (!hasPendingRestore(context)) return@runCatching
        check(hasRoot()) { "تعذرت استعادة الشبكة: صلاحية الروت غير متاحة." }
        runAsRoot(RESTORE_SCRIPT)
        check(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACTIVE, false).commit()
        ) { "تمت إزالة القواعد لكن تعذر تحديث حالة الاستعادة المحفوظة." }
    }

    private fun runAsRoot(script: String): String {
        val process = ProcessBuilder("su", "-c", script)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("انتهت مهلة تنفيذ أمر الروت.")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.exitValue() != 0) {
            throw IllegalStateException(
                output.takeIf { it.isNotBlank() }
                    ?: "رفض su الأمر أو فشل تنفيذ قواعد الجدار الناري (exit ${process.exitValue()})."
            )
        }
        return output
    }

    private val APPLY_SCRIPT = """
        set -e
        command -v iptables >/dev/null 2>&1 || { echo 'iptables غير متاح'; exit 41; }
        command -v ip6tables >/dev/null 2>&1 || { echo 'ip6tables غير متاح'; exit 42; }
        for BIN in iptables ip6tables; do
          ${'$'}BIN -N $CHAIN 2>/dev/null || ${'$'}BIN -F $CHAIN
          ${'$'}BIN -F $CHAIN
          ${'$'}BIN -A $CHAIN -o lo -j RETURN
          ${'$'}BIN -A $CHAIN -p udp --dport 53 -j RETURN
          ${'$'}BIN -A $CHAIN -p udp -j REJECT
          ${'$'}BIN -A $CHAIN -p tcp --dport 3478 -j REJECT
          ${'$'}BIN -A $CHAIN -p tcp --dport 5349 -j REJECT
          ${'$'}BIN -A $CHAIN -p tcp --dport 19302 -j REJECT
          ${'$'}BIN -C OUTPUT -j $CHAIN 2>/dev/null || ${'$'}BIN -I OUTPUT 1 -j $CHAIN
        done
    """.trimIndent()

    private val RESTORE_SCRIPT = """
        set -e
        for BIN in iptables ip6tables; do
          if command -v "${'$'}BIN" >/dev/null 2>&1; then
            while ${'$'}BIN -C OUTPUT -j $CHAIN 2>/dev/null; do
              ${'$'}BIN -D OUTPUT -j $CHAIN || break
            done
            ${'$'}BIN -F $CHAIN 2>/dev/null || true
            ${'$'}BIN -X $CHAIN 2>/dev/null || true
          fi
        done
    """.trimIndent()
}
