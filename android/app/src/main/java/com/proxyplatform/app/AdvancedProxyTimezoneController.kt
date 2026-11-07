package com.proxyplatform.app

import android.content.Context
import org.json.JSONObject

/** Temporarily aligns the device timezone with the selected proxy exit. */
internal object AdvancedProxyTimezoneController {
    private const val PREFS = "advanced_proxy_timezone"
    private const val SNAPSHOT = "snapshot"
    private const val SNAPSHOT_SAVED = "snapshot_saved"
    private const val AUTO_TIME_ZONE = "auto_time_zone"
    private const val TIME_ZONE_PROPERTY = "persist.sys.timezone"

    fun hasPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(SNAPSHOT_SAVED, false)

    fun apply(context: Context, timezone: ProxyTimezone): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(!prefs.getBoolean(SNAPSHOT_SAVED, false)) {
            "توجد منطقة زمنية سابقة بانتظار الاستعادة؛ استعدها قبل بدء جلسة جديدة."
        }

        AdvancedOperationLog.info(context, "قراءة المنطقة الزمنية الحالية وحفظ نسخة استعادة.")
        val originalTimeZone = read(context, "getprop $TIME_ZONE_PROPERTY")
        val automaticTimeZone = readSetting(context, AUTO_TIME_ZONE)
        check(originalTimeZone.isNotBlank() && originalTimeZone != "null") {
            "تعذر قراءة المنطقة الزمنية الأصلية من Android."
        }

        val snapshot = JSONObject()
            .put("time_zone", originalTimeZone)
            .put("auto_time_zone", automaticTimeZone)
            .put("managed_time_zone", timezone.id)
        check(
            prefs.edit()
                .putString(SNAPSHOT, snapshot.toString())
                .putBoolean(SNAPSHOT_SAVED, true)
                .commit()
        ) { "تعذر حفظ نسخة استعادة المنطقة الزمنية." }

        AdvancedOperationLog.info(context, "تطبيق المنطقة الزمنية لخروج البروكسي: ${timezone.id}.")
        try {
            writeSetting(context, AUTO_TIME_ZONE, "0").getOrThrow()
            check(readSetting(context, AUTO_TIME_ZONE) == "0") {
                "تعذر إيقاف الضبط التلقائي للمنطقة الزمنية مؤقتًا."
            }

            command(context, "cmd alarm set-timezone ${shellQuote(timezone.id)}").getOrThrow()
            check(read(context, "getprop $TIME_ZONE_PROPERTY") == timezone.id) {
                "لم يؤكد Android تطبيق المنطقة الزمنية ${timezone.id}."
            }
            AdvancedOperationLog.output(context, "تم التحقق من تطبيق المنطقة الزمنية ${timezone.id}.")
        } catch (failure: Throwable) {
            AdvancedOperationLog.error(context, "تعذر تطبيق منطقة البروكسي؛ ستتم محاولة إعادة المنطقة الزمنية الأصلية.")
            val restore = restore(context)
            if (restore.isFailure) {
                throw IllegalStateException(
                    "${failure.message ?: "فشل تطبيق المنطقة الزمنية"}؛ وتعذرت الاستعادة التلقائية: " +
                        (restore.exceptionOrNull()?.message ?: "أعد الاتصال عبر ADB وحاول الاستعادة."),
                    failure,
                )
            }
            throw failure
        }
    }.onFailure {
        AdvancedOperationLog.exception(context, "فشل ضبط المنطقة الزمنية للبروكسي", it)
    }

    /** Restores only values that still match what this session set. */
    fun restore(context: Context): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SNAPSHOT_SAVED, false)) return@runCatching
        val snapshot = JSONObject(prefs.getString(SNAPSHOT, "{}") ?: "{}")
        val failures = mutableListOf<String>()
        AdvancedOperationLog.info(context, "بدء استعادة المنطقة الزمنية الأصلية.")

        val managedTimeZone = snapshot.optString("managed_time_zone")
        val currentTimeZone = runCatching { read(context, "getprop $TIME_ZONE_PROPERTY") }
            .getOrElse { failures += "قراءة المنطقة الزمنية: ${it.message}"; "" }
        if (currentTimeZone == managedTimeZone) {
            val originalTimeZone = snapshot.optString("time_zone")
            runCatching {
                command(context, "cmd alarm set-timezone ${shellQuote(originalTimeZone)}").getOrThrow()
                check(read(context, "getprop $TIME_ZONE_PROPERTY") == originalTimeZone) {
                    "لم يؤكد Android استعادة المنطقة الزمنية الأصلية."
                }
            }.onFailure { failures += "استعادة المنطقة الزمنية: ${it.message}" }
        } else {
            AdvancedOperationLog.info(context, "تغيّرت المنطقة الزمنية خارج الجلسة؛ أُبقي التغيير الخارجي كما هو.")
        }

        val currentAutoTimeZone = runCatching { readSetting(context, AUTO_TIME_ZONE) }
            .getOrElse { failures += "قراءة الضبط التلقائي للوقت: ${it.message}"; "" }
        if (currentAutoTimeZone == "0") {
            runCatching {
                restoreSetting(context, AUTO_TIME_ZONE, snapshot.optString("auto_time_zone", "null")).getOrThrow()
            }.onFailure { failures += "استعادة الضبط التلقائي للمنطقة الزمنية: ${it.message}" }
        } else {
            AdvancedOperationLog.info(context, "تغيّر خيار المنطقة الزمنية التلقائية خارج الجلسة؛ أُبقي التغيير الخارجي.")
        }

        check(failures.isEmpty()) { failures.joinToString("؛ ") }
        check(prefs.edit().remove(SNAPSHOT).remove(SNAPSHOT_SAVED).commit()) {
            "تعذر إنهاء استعادة المنطقة الزمنية المحفوظة."
        }
        AdvancedOperationLog.output(context, "تمت استعادة المنطقة الزمنية الأصلية أو الحفاظ على التغيير الخارجي والتحقق منه.")
    }.onFailure {
        AdvancedOperationLog.exception(context, "فشلت استعادة المنطقة الزمنية", it)
    }

    private fun readSetting(context: Context, key: String): String =
        read(context, "settings get global $key")

    private fun writeSetting(context: Context, key: String, value: String): Result<Unit> =
        command(context, "settings put global $key ${shellQuote(value)}").map { Unit }

    private fun restoreSetting(context: Context, key: String, value: String): Result<Unit> {
        val result = if (value == "null" || value.isBlank()) {
            command(context, "settings delete global $key").map { Unit }
        } else {
            writeSetting(context, key, value)
        }
        return result.mapCatching {
            val actual = readSetting(context, key)
            check(actual == value || (value == "null" && actual == "null")) {
                "لم تتم استعادة الإعداد $key."
            }
        }
    }

    private fun read(context: Context, command: String): String =
        WirelessDebuggingManager.executeCommandResult(context, command).getOrThrow().trim()

    private fun command(context: Context, command: String): Result<String> =
        WirelessDebuggingManager.executeCommandResult(context, command)

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
