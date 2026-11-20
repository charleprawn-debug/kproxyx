package com.proxyplatform.app

import android.content.Context
import org.json.JSONObject

/** Temporarily aligns the whole Android system locale with the proxy exit. */
internal object AdvancedProxyLanguageController {
    private const val PREFS = "advanced_proxy_language"
    private const val SNAPSHOT = "snapshot"
    private const val SNAPSHOT_SAVED = "snapshot_saved"
    private const val SYSTEM_LOCALES = "system_locales"

    fun hasPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SNAPSHOT_SAVED, false)

    fun apply(context: Context, language: ProxyLanguage): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(!prefs.getBoolean(SNAPSHOT_SAVED, false)) {
            tr("توجد لغة سابقة بانتظار الاستعادة؛ استعدها قبل بدء جلسة جديدة.", "A previous language is awaiting restoration. Restore it before starting a new session.")
        }
        val originalLocales = readSetting(context)
        check(originalLocales.isNotBlank() && originalLocales != "null") {
            tr("تعذر قراءة لغات Android الأصلية.", "Could not read the original Android languages.")
        }
        val snapshot = JSONObject()
            .put("system_locales", originalLocales)
            .put("managed_locale", language.localeTag)
        check(prefs.edit().putString(SNAPSHOT, snapshot.toString()).putBoolean(SNAPSHOT_SAVED, true).commit()) {
            tr("تعذر حفظ نسخة استعادة اللغة.", "Could not save the language restoration snapshot.")
        }
        AdvancedOperationLog.info(context, "تطبيق لغة الهاتف تلقائيًا حسب بلد البروكسي: ${language.localeTag}.")
        try {
            setLocale(context, language.localeTag).getOrThrow()
            check(readSetting(context).contains(language.localeTag, ignoreCase = true)) {
                tr("لم يؤكد Android تطبيق لغة الهاتف ${language.localeTag}.", "Android did not confirm that the phone language ${language.localeTag} was applied.")
            }
            AdvancedOperationLog.output(context, "تم تطبيق لغة الهاتف ${language.localeTag} والتحقق منها عبر ADB.")
        } catch (failure: Throwable) {
            val restore = restore(context)
            if (restore.isFailure) throw IllegalStateException(
                tr(
                    "${failure.message ?: "فشل تطبيق لغة الهاتف"}؛ وتعذرت الاستعادة التلقائية: ${restore.exceptionOrNull()?.message ?: "أعد الاتصال عبر ADB."}",
                    "${failure.message ?: "Failed to apply the phone language"}; automatic restoration also failed: ${restore.exceptionOrNull()?.message ?: "Reconnect via ADB."}",
                ), failure
            )
            throw failure
        }
    }.onFailure { AdvancedOperationLog.exception(context, "فشل ضبط لغة الهاتف للبروكسي", it) }

    fun restore(context: Context): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SNAPSHOT_SAVED, false)) return@runCatching
        val snapshot = JSONObject(prefs.getString(SNAPSHOT, "{}") ?: "{}")
        val managed = snapshot.optString("managed_locale")
        val current = runCatching { readSetting(context) }.getOrElse { "" }
        if (current.contains(managed, ignoreCase = true)) {
            setLocale(context, snapshot.optString("system_locales")).getOrThrow()
            check(readSetting(context).containsAnyLocale(snapshot.optString("system_locales"))) {
                tr("لم يؤكد Android استعادة لغات الهاتف الأصلية.", "Android did not confirm restoration of the original phone languages.")
            }
            AdvancedOperationLog.output(context, "تمت استعادة لغات الهاتف الأصلية.")
        } else {
            AdvancedOperationLog.info(context, "تغيّرت لغة الهاتف خارج جلسة البروكسي؛ أُبقي التغيير الخارجي.")
        }
        check(prefs.edit().remove(SNAPSHOT).remove(SNAPSHOT_SAVED).commit()) { tr("تعذر إنهاء استعادة اللغة المحفوظة.", "Could not complete restoration of the saved language.") }
    }.onFailure { AdvancedOperationLog.exception(context, "فشلت استعادة لغة الهاتف", it) }

    private fun setLocale(context: Context, localeList: String): Result<String> =
        WirelessDebuggingManager.executeCommandResult(context, "cmd locale set --user 0 ${shellQuote(localeList)}")
            .recoverCatching { WirelessDebuggingManager.executeCommandResult(context, "settings put system $SYSTEM_LOCALES ${shellQuote(localeList)}").getOrThrow() }

    private fun readSetting(context: Context): String =
        WirelessDebuggingManager.executeCommandResult(context, "settings get system $SYSTEM_LOCALES").getOrThrow().trim()

    private fun String.containsAnyLocale(expected: String): Boolean =
        expected.split(',').map { it.trim() }.filter { it.isNotBlank() }.all { contains(it, ignoreCase = true) }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
