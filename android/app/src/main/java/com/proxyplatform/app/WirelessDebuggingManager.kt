package com.proxyplatform.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.proxyplatform.app.adb.AdbPairingNotifier

/** Compatibility facade for the UI; all ADB work is performed by EmbeddedAdbManager. */
object WirelessDebuggingManager {
    private const val TAG = "WirelessDebuggingManager"

    enum class DebuggingState {
        UNSUPPORTED_ANDROID_VERSION,
        DEVELOPER_DISABLED,
        WIRELESS_DISABLED,
        NOT_PAIRED,
        PAIRED_NOT_CONNECTED,
        READY
    }

    fun checkState(context: Context): DebuggingState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return DebuggingState.UNSUPPORTED_ANDROID_VERSION
        }
        if (!areDeveloperOptionsEnabled(context)) return DebuggingState.DEVELOPER_DISABLED
        if (!isWirelessDebuggingEnabled(context)) {
            return DebuggingState.WIRELESS_DISABLED
        }
        return runCatching {
            val adb = EmbeddedAdbManager.get(context)
            when {
                adb.isConnected() -> DebuggingState.READY
                adb.wasPaired() -> DebuggingState.PAIRED_NOT_CONNECTED
                else -> DebuggingState.NOT_PAIRED
            }
        }.onFailure {
            // ADB is optional. A broken provider, stale Keystore entry, or a
            // device-side pairing reset must never crash the Compose screen.
            Log.e(TAG, "Wireless ADB state check failed", it)
        }.getOrDefault(DebuggingState.NOT_PAIRED)
    }

    @SuppressLint("HardwareIds")
    fun areDeveloperOptionsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }.getOrDefault(false)

    @SuppressLint("HardwareIds")
    fun isWirelessDebuggingEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0
        }.getOrDefault(false)

    fun pair(context: Context, pairingCode: String): Result<Unit> {
        AdvancedOperationLog.info(context, "بدء اقتران Wireless ADB؛ رمز الاقتران مستثنى من السجل.")
        val result = runCatching {
            EmbeddedAdbManager.get(context).pair(
                EmbeddedAdbManager.DEFAULT_TIMEOUT_MS,
                pairingCode
            ).getOrThrow()
            AdvancedOperationLog.info(context, "قبل Android الاقتران؛ جارٍ إنشاء اتصال TLS.")
            EmbeddedAdbManager.get(context).connect(
                EmbeddedAdbManager.DEFAULT_TIMEOUT_MS
            ).getOrThrow()
        }
        result.onSuccess {
            AdvancedOperationLog.output(context, "اتصال Wireless ADB جاهز.")
        }.onFailure {
            AdvancedOperationLog.exception(context, "فشل اقتران/اتصال Wireless ADB", it)
        }
        return result
    }

    /** The connection port is discovered via mDNS; the old integer is ignored. */
    fun connect(context: Context, ignoredPort: Int): Boolean {
        AdvancedOperationLog.info(context, "بدء إعادة اتصال Wireless ADB عبر mDNS/TLS.")
        val result = runCatching {
            EmbeddedAdbManager.get(context).connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
        }
        result.onSuccess {
            AdvancedOperationLog.output(context, "تمت إعادة اتصال Wireless ADB.")
        }.onFailure {
            Log.w(TAG, "ADB auto-connect failed", it)
            AdvancedOperationLog.exception(context, "فشلت إعادة اتصال Wireless ADB", it)
        }
        return result.isSuccess
    }

    fun executeCommandResult(context: Context, command: String): Result<String> {
        val adb = EmbeddedAdbManager.get(context)
        val first = runCatching {
            if (!adb.isConnected()) {
                AdvancedOperationLog.info(context, "جلسة ADB غير متصلة؛ بدء اتصال TLS قبل الأمر.")
                adb.connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
            }
            executeShellCommand(context, adb, command)
        }
        if (first.isSuccess) {
            return first
        }
        if (first.exceptionOrNull() is RemoteShellCommandException) {
            first.exceptionOrNull()?.let { AdvancedOperationLog.exception(context, "رفض Android أمر shell برمز خروج غير صفري؛ لن تتم إعادة إرساله", it) }
            return first
        }

        // Wireless ADB can be dropped by Android after pairing or after the
        // app has been backgrounded. Reconnect once before reporting failure.
        first.exceptionOrNull()?.let { AdvancedOperationLog.exception(context, "فشل تنفيذ أمر ADB قبل إعادة المحاولة", it) }
        val retry = runCatching {
            Log.w(TAG, "ADB command failed; reconnecting once", first.exceptionOrNull())
            AdvancedOperationLog.info(context, "إعادة المحاولة مرة واحدة بعد إعادة اتصال ADB.")
            adb.close()
            adb.connect(EmbeddedAdbManager.DEFAULT_TIMEOUT_MS).getOrThrow()
            executeShellCommand(context, adb, command)
        }
        retry.onFailure {
            Log.e(TAG, "ADB shell command failed: $command", it)
            AdvancedOperationLog.exception(context, "فشل أمر ADB بعد إعادة المحاولة", it)
        }
        return retry
    }

    private fun executeShellCommand(
        context: Context,
        adb: EmbeddedAdbManager,
        command: String,
    ): String {
        val wrappedCommand = AdbShellCommand.wrap(command)
        AdvancedOperationLog.command(context, wrappedCommand)
        val rawResponse = adb.execute(wrappedCommand).getOrThrow()
        val response = AdbShellCommand.parse(rawResponse)
        AdvancedOperationLog.output(
            context,
            response.output.ifBlank { "<مخرجات فارغة؛ وصل رد انتهاء الأمر>" }
        )
        AdvancedOperationLog.info(context, "رمز خروج أمر ADB: ${response.exitCode}.")
        if (response.exitCode != 0) {
            throw RemoteShellCommandException(
                response.exitCode,
                response.output.ifBlank { "<بلا تفاصيل>" }
            )
        }
        return response.output
    }

    private class RemoteShellCommandException(exitCode: Int, output: String) :
        IllegalStateException("انتهى أمر ADB برمز $exitCode: $output")

    fun executeCommand(context: Context, command: String): Boolean =
        executeCommandResult(context, command).isSuccess

    fun resetState(context: Context) {
        EmbeddedAdbManager.get(context).close()
    }

    fun showPairingNotification(context: Context): Boolean = AdbPairingNotifier.showPairing(context)

    fun getDebuggingSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("package:${context.packageName}"))
        }
}
