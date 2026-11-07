package com.proxyplatform.app

import android.content.Context
import org.json.JSONObject

/** Applies the advanced-mode system proxy and restores the user's prior settings on stop. */
internal object AdvancedSystemProxy {
    private const val PREFS = "advanced_system_proxy"
    private const val SNAPSHOT = "snapshot"
    private const val SNAPSHOT_SAVED = "snapshot_saved"
    private val managedSettings = listOf(
        "global_http_proxy_host",
        "global_http_proxy_port",
        "http_proxy",
    )

    fun hasPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(SNAPSHOT_SAVED, false)

    fun apply(context: Context, proxy: String): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SNAPSHOT_SAVED, false)) {
            AdvancedOperationLog.info(context, "قراءة إعدادات بروكسي Android الحالية لحفظ نسخة استعادة.")
            val snapshot = JSONObject()
            managedSettings.forEach { key ->
                snapshot.put(key, read(context, key).getOrThrow())
            }
            check(
                prefs.edit()
                    .putString(SNAPSHOT, snapshot.toString())
                    .putBoolean(SNAPSHOT_SAVED, true)
                    .commit()
            ) { "تعذر حفظ إعدادات البروكسي السابقة." }
            AdvancedOperationLog.output(context, "حُفظت نسخة الاستعادة محليًا داخل مساحة التطبيق الخاصة.")
        }

        AdvancedOperationLog.info(context, "تعيين بروكسي النظام إلى $proxy.")
        write(context, "http_proxy", proxy).getOrThrow()
        val actual = read(context, "http_proxy").getOrThrow()
        check(actual == proxy) { "إعداد النظام '$actual' لا يطابق '$proxy'." }
        AdvancedOperationLog.output(context, "تأكد ضبط بروكسي النظام والتحقق منه: $actual.")
    }.onFailure {
        AdvancedOperationLog.exception(context, "فشل ضبط بروكسي النظام", it)
        // If setting the new proxy partially succeeded, do not leave it behind.
        restore(context, proxy)
    }

    /**
     * Restores the saved system proxy settings without overwriting a newer
     * setting chosen by the user. Android's :0 sentinel explicitly disables
     * ProxyTracker and prompts apps to drop a stale loopback proxy. Restoration
     * can be safely retried after process death at any intermediate step.
     */
    fun restore(context: Context, expectedProxy: String? = null): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AdvancedOperationLog.info(context, "بدء استعادة إعدادات بروكسي Android السابقة.")
        if (!prefs.getBoolean(SNAPSHOT_SAVED, false)) {
            if (expectedProxy != null && read(context, "http_proxy").getOrThrow() == expectedProxy) {
                write(context, "global_http_proxy_host", null).getOrThrow()
                write(context, "global_http_proxy_port", null).getOrThrow()
                write(context, "http_proxy", ":0").getOrThrow()
                check(ProxyRestorePolicy.isNoProxy(read(context, "http_proxy").getOrThrow())) {
                    "تعذر تعطيل بروكسي النظام القديم."
                }
                AdvancedOperationLog.output(context, "أُزيل بروكسي loopback القديم وأُعيد اتصال Android المباشر.")
            } else {
                AdvancedOperationLog.info(context, "لا توجد لقطة محفوظة؛ لا توجد إعدادات سابقة تابعة لهذه الجلسة للاستعادة.")
            }
            return@runCatching
        }

        val snapshot = JSONObject(prefs.getString(SNAPSHOT, "{}") ?: "{}")
        val currentProxy = read(context, "http_proxy").getOrThrow()
        val originalProxy = snapshot.optString("http_proxy").takeUnless { it == "null" }
        if (expectedProxy != null &&
            ProxyRestorePolicy.isExternalOverride(currentProxy, expectedProxy, originalProxy)
        ) {
            // The user changed the system proxy while our session was active.
            // Keep that newer value and discard only our stale recovery snapshot.
            check(prefs.edit().remove(SNAPSHOT).remove(SNAPSHOT_SAVED).commit()) {
                "تعذر تحرير لقطة إعدادات البروكسي القديمة."
            }
            AdvancedOperationLog.output(context, "حُفظ تغيير بروكسي النظام الذي أجراه المستخدم أثناء الجلسة، وحُذفت لقطة الاستعادة القديمة.")
            return@runCatching
        }

        // Restore the host/port first, then force the framework proxy observer
        // to an explicit direct state when the original settings were empty.
        managedSettings.forEach { key ->
            val value = snapshot.optString(key, "null")
            val restoredValue = if (key == "http_proxy" && ProxyRestorePolicy.isNoProxy(value)) ":0" else value
            write(context, key, restoredValue.takeUnless { it == "null" }).getOrThrow()
            val restored = read(context, key).getOrThrow()
            val matches = if (key == "http_proxy" && ProxyRestorePolicy.isNoProxy(value)) {
                ProxyRestorePolicy.isNoProxy(restored)
            } else {
                restored == value
            }
            check(matches) { "تعذر استعادة إعداد النظام $key." }
        }
        check(prefs.edit().remove(SNAPSHOT).remove(SNAPSHOT_SAVED).commit()) {
            "تعذر إنهاء استعادة إعدادات البروكسي."
        }
        AdvancedOperationLog.output(context, "تمت استعادة إعدادات بروكسي Android والتحقق من وضع الاتصال المباشر.")
    }.onFailure {
        AdvancedOperationLog.exception(context, "فشلت استعادة بروكسي Android", it)
    }

    private fun read(context: Context, key: String): Result<String> =
        WirelessDebuggingManager.executeCommandResult(context, "settings get global $key")
            .map { it.trim() }

    private fun write(context: Context, key: String, value: String?): Result<Unit> {
        val command = if (value == null || value == "null") {
            "settings delete global $key"
        } else {
            "settings put global $key ${shellQuote(value)}"
        }
        return WirelessDebuggingManager.executeCommandResult(context, command).map { Unit }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
