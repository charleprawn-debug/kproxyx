package com.proxyplatform.app

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager as AndroidCarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SimCardInfo(
    val slot: Int,
    val subscriptionId: Int,
    val carrierName: String,
    val currentConfig: Map<String, String> = emptyMap(),
)

internal object SimCarrierPrefs {
    const val PREFS = "sim_nrfr_state"
    const val SIM_CARDS_JSON = "sim_cards_json"
    const val SIM_CACHE_TIME = "sim_cache_time"
    const val OPERATION_PENDING = "operation_pending"
    const val OPERATION_KIND = "operation_kind"
    const val OPERATION_STARTED_AT = "operation_started_at"
    const val OUTCOME_MESSAGE = "outcome_message"
    const val OUTCOME_SUCCESS = "outcome_success"
    const val OUTCOME_TIME = "outcome_time"
}

internal object SimOperatorCommands {
    private const val INSTRUMENTATION_CLASS = "com.proxyplatform.app.SimCarrierConfigInstrumentation"

    fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun buildInstrumentationCommand(
        packageName: String,
        operation: String,
        arguments: Map<String, String>,
    ): String {
        // Instrumentation targets the owning APK because Android rejects a
        // third-party APK that tries to instrument the signed system package.
        // --no-restart prevents ActivityManager from restarting the UI while
        // the shell-launched CarrierConfig operation is running.
        val args = mutableListOf("am", "instrument", "-w", "-r", "--no-restart")
        arguments.forEach { (key, value) -> args += listOf("-e", key, value) }
        args += listOf("-e", "operation", operation)
        args += "$packageName/$INSTRUMENTATION_CLASS"
        // Keep am instrument attached to the ADB stream.  Detaching it with
        // nohup/background makes the shell report success before Instrumentation
        // has written the SIM cache, and Android may kill the orphaned process.
        // The caller needs the synchronous result to distinguish a real refresh
        // from merely submitting a command.
        return args.joinToString(" ") { quote(it) }
    }
}

/** Runs NRFR-equivalent CarrierConfig operations through same-APK Instrumentation + Wireless ADB. */
internal class SimOperatorController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(SimCarrierPrefs.PREFS, Context.MODE_PRIVATE)

    val operationPending: Boolean
        get() = prefs.getBoolean(SimCarrierPrefs.OPERATION_PENDING, false)

    val hasSimCache: Boolean
        get() = prefs.getLong(SimCarrierPrefs.SIM_CACHE_TIME, 0L) > 0L

    fun readSimCards(): List<SimCardInfo> = runCatching {
        val array = JSONArray(prefs.getString(SimCarrierPrefs.SIM_CARDS_JSON, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val configJson = item.optJSONObject("currentConfig") ?: JSONObject()
            val config = buildMap {
                configJson.keys().forEach { key -> put(key, configJson.optString(key)) }
            }
            SimCardInfo(
                slot = item.optInt("slot"),
                subscriptionId = item.optInt("subId", -1),
                carrierName = item.optString("carrierName"),
                currentConfig = config,
            ).takeIf { it.subscriptionId >= 0 }
        }
    }.getOrDefault(emptyList())

    fun refreshSimCards(): Result<Unit> = runCatching {
        queueInstrumentation("refresh", emptyMap())
    }.onFailure {
        logQueueFailure("تعذر تحديث بيانات SIM", it)
    }

    fun saveCarrierConfig(
        simCard: SimCardInfo,
        countryCode: String?,
        carrierName: String?,
    ): Result<Unit> = runCatching {
        val validCountry = countryCode?.trim()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        val validCarrier = carrierName?.trim()?.takeIf { it.isNotEmpty() }
        require(validCountry != null || validCarrier != null) { "اختر رمز بلد صحيحاً أو اسم مشغّل." }
        queueInstrumentation(
            "save",
            buildMap {
                put("subId", simCard.subscriptionId.toString())
                put("slot", simCard.slot.toString())
                validCountry?.let { put("countryCode", it.uppercase(Locale.ROOT)) }
                validCarrier?.let { put("carrierName", it) }
            },
        )
    }.onFailure {
        logQueueFailure("تعذر بدء حفظ CarrierConfig", it)
    }

    fun resetCarrierConfig(simCard: SimCardInfo): Result<Unit> = runCatching {
        queueInstrumentation(
            "reset",
            mapOf("subId" to simCard.subscriptionId.toString(), "slot" to simCard.slot.toString()),
        )
    }.onFailure {
        logQueueFailure("تعذر بدء إعادة تعيين CarrierConfig", it)
    }

    fun consumeOperationMessage(): String? {
        recoverStaleOperationIfNeeded()
        val message = prefs.getString(SimCarrierPrefs.OUTCOME_MESSAGE, null) ?: return null
        val success = prefs.getBoolean(SimCarrierPrefs.OUTCOME_SUCCESS, false)
        prefs.edit()
            .remove(SimCarrierPrefs.OUTCOME_MESSAGE)
            .remove(SimCarrierPrefs.OUTCOME_SUCCESS)
            .remove(SimCarrierPrefs.OUTCOME_TIME)
            .commit()
        if (success) return "نجح"
        val reason = when {
            message.contains("مهلة") || message.contains("لم يصل رد Instrumentation") -> "انتهت مهلة التنفيذ؛ لم يصل تأكيد من Android."
            else -> message
                .replace(Regex("^(?:فشلت عملية SIM عبر ADB|فشلت عملية CarrierConfig):\\s*"), "")
                .replace(Regex("^تعذر بدء [^:]+:\\s*"), "")
                .ifBlank { "تعذر إتمام العملية." }
                .take(220)
        }
        return "فشل: $reason"
    }

    private fun queueInstrumentation(operation: String, arguments: Map<String, String>) {
        // A previous app process may have died after the old detached command
        // was submitted.  Clear only operations that are genuinely stale so a
        // normal in-flight command is still protected from a duplicate request.
        recoverStaleOperationIfNeeded()
        check(!operationPending) { "هناك عملية SIM قيد التنفيذ؛ انتظر انتهاءها." }
        check(prefs.edit()
            .putBoolean(SimCarrierPrefs.OPERATION_PENDING, true)
            .putString(SimCarrierPrefs.OPERATION_KIND, operation)
            .putLong(SimCarrierPrefs.OPERATION_STARTED_AT, System.currentTimeMillis())
            .remove(SimCarrierPrefs.OUTCOME_MESSAGE)
            .remove(SimCarrierPrefs.OUTCOME_SUCCESS)
            .remove(SimCarrierPrefs.OUTCOME_TIME)
            .commit()) { "تعذر حفظ طلب العملية على الجهاز." }

        val command = SimOperatorCommands.buildInstrumentationCommand(
            packageName = appContext.packageName,
            operation = operation,
            arguments = arguments,
        )
        SimOperationLog.command(appContext, command)
        try {
            val output = WirelessDebuggingManager.executeCommandResult(appContext, command).getOrThrow()
            SimOperationLog.output(appContext, output.ifBlank { "اكتملت عملية Instrumentation دون مخرجات إضافية." })
            check(instrumentationSucceeded(output)) { instrumentationFailureReason(output) }
            SimOperationLog.info(appContext, "اكتملت عملية $operation بنجاح عبر Wireless ADB المضمّن.")
        } catch (failure: Throwable) {
            saveOperationResult(
                success = false,
                message = "فشلت عملية SIM عبر ADB: ${failure.message ?: failure.javaClass.simpleName}",
            )
            throw failure
        }
    }

    private fun instrumentationSucceeded(output: String): Boolean {
        // `am instrument -w` returns a shell success code even for some
        // instrumentation-level failures, so inspect the protocol result too.
        val statusCode = Regex("(?m)^INSTRUMENTATION_CODE:\\s*(-?\\d+)\\s*$")
            .find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val explicitFailure = Regex("(?m)^INSTRUMENTATION_STATUS: (?:Error|shortMsg|failure):\\s*(.+)$")
            .find(output)
        val resultCode = Regex("(?m)^INSTRUMENTATION_RESULT:.*(?:\\n|$)")
            .find(output)?.value.orEmpty()
        val failedResult = resultCode.contains("shortMsg=", ignoreCase = true) ||
            resultCode.contains("Error=", ignoreCase = true)
        val reportedSuccess = Regex("(?i)INSTRUMENTATION_RESULT:\\s*success=true")
            .containsMatchIn(output)
        // Android's Instrumentation.finish() uses Activity.RESULT_OK (-1),
        // not shell exit code 0. Treat -1 as success when the protocol also
        // reports success=true; otherwise successful refresh/save operations
        // are incorrectly shown as failures in the SIM screen.
        return explicitFailure == null && !failedResult &&
            (statusCode == null || statusCode == 0 || (statusCode == Activity.RESULT_OK && reportedSuccess))
    }

    private fun instrumentationFailureReason(output: String): String {
        val statusReason = Regex("(?m)^INSTRUMENTATION_STATUS: (?:Error|shortMsg|failure):\\s*(.+)$")
            .find(output)?.groupValues?.getOrNull(1)?.trim()
        if (!statusReason.isNullOrBlank()) return statusReason
        val resultReason = Regex("(?m)^INSTRUMENTATION_RESULT:.*(?:shortMsg|Error)=(.+)$")
            .find(output)?.groupValues?.getOrNull(1)?.trim()
        if (!resultReason.isNullOrBlank()) return resultReason
        return "رفض Android تنفيذ أمر SIM."
    }

    private fun recoverStaleOperationIfNeeded() {
        if (!operationPending) return
        val startedAt = prefs.getLong(SimCarrierPrefs.OPERATION_STARTED_AT, 0L)
        if (startedAt <= 0L || System.currentTimeMillis() - startedAt < STALE_OPERATION_MS) return
        saveOperationResult(
            success = false,
            message = "لم يصل رد Instrumentation خلال المهلة؛ لم يُؤكَّد نجاح العملية. أعد الاتصال بـADB ثم حدّث بيانات SIM.",
        )
        SimOperationLog.error(appContext, "انتهت مهلة انتظار رد Instrumentation؛ نتيجة العملية غير مؤكدة.")
    }

    private fun saveOperationResult(success: Boolean, message: String) {
        prefs.edit()
            .putBoolean(SimCarrierPrefs.OPERATION_PENDING, false)
            .remove(SimCarrierPrefs.OPERATION_KIND)
            .putString(SimCarrierPrefs.OUTCOME_MESSAGE, message)
            .putBoolean(SimCarrierPrefs.OUTCOME_SUCCESS, success)
            .putLong(SimCarrierPrefs.OUTCOME_TIME, System.currentTimeMillis())
            .commit()
    }

    private fun logQueueFailure(prefix: String, failure: Throwable) {
        SimOperationLog.error(appContext, "$prefix: ${failure.message ?: failure.javaClass.simpleName}")
        AdvancedOperationLog.exception(appContext, prefix, failure)
    }

    private companion object {
        const val STALE_OPERATION_MS = 120_000L
    }
}

/**
 * NRFR-equivalent calls run under shell identity, transported by this app's
 * Wireless ADB. The instrumentation targets this APK and uses no-restart so
 * the shell-launched operation does not throw the user out of the UI.
 */
class SimCarrierConfigInstrumentation : Instrumentation() {
    @TargetApi(Build.VERSION_CODES.R)
    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        start()
        val result = Bundle()
        var resultCode = Activity.RESULT_OK
        var adoptedShellIdentity = false
        val automation = uiAutomation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            result.putString("success", "false")
            result.putString("error", "ميزة SIM عبر Wireless ADB تتطلب Android 11 أو أحدث.")
            finish(Activity.RESULT_CANCELED, result)
            return
        }

        try {
            checkNotNull(automation) { "UiAutomation غير متاح داخل Instrumentation." }
            HiddenApiBypass.addHiddenApiExemptions("L")
            automation.adoptShellPermissionIdentity(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.MODIFY_PHONE_STATE,
            )
            adoptedShellIdentity = true

            val operation = arguments.getString("operation") ?: error("نوع العملية غير محدد.")
            val service = CarrierConfigService.connect()
            val subscriptions = readSimCards(service)
            when (operation) {
                "refresh" -> {
                    saveSimCards(subscriptions)
                    finishOperation(success = true, message = "تم تحديث قائمة الشرائح وقراءة CarrierConfig الحالي.")
                }
                "save" -> {
                    val subId = arguments.getString("subId")?.toIntOrNull() ?: error("معرّف SIM غير صالح.")
                    val slot = arguments.getString("slot")?.toIntOrNull() ?: error("رقم منفذ SIM غير صالح.")
                    check(subscriptions.any { it.subscriptionId == subId }) { "الشريحة المحددة لم تعد نشطة؛ حدّث القائمة." }
                    val countryCode = arguments.getString("countryCode")
                    val carrierName = arguments.getString("carrierName")
                    val bundle = buildNrfrOverride(countryCode, carrierName)
                    service.overrideConfig(subId, bundle, true)
                    SimOperationLog.info(
                        context,
                        "تم استدعاء ICarrierConfigLoader.overrideConfig(subId=$subId, persistent=true) لــSIM $slot.",
                    )
                    saveSimCards(readSimCards(service))
                    finishOperation(success = true, message = "تم حفظ CarrierConfig للشريحة SIM $slot كما في NRFR.")
                }
                "reset" -> {
                    val subId = arguments.getString("subId")?.toIntOrNull() ?: error("معرّف SIM غير صالح.")
                    val slot = arguments.getString("slot")?.toIntOrNull() ?: error("رقم منفذ SIM غير صالح.")
                    service.overrideConfig(subId, null, true)
                    SimOperationLog.info(
                        context,
                        "تم استدعاء ICarrierConfigLoader.overrideConfig(subId=$subId, null, persistent=true) لــSIM $slot.",
                    )
                    saveSimCards(readSimCards(service))
                    finishOperation(success = true, message = "تم حذف CarrierConfig override للشريحة SIM $slot.")
                }
                else -> error("عملية غير مدعومة: $operation")
            }
            result.putString("success", "true")
        } catch (failure: Throwable) {
            resultCode = Activity.RESULT_CANCELED
            result.putString("success", "false")
            result.putString("error", failure.rootCause().message ?: failure.rootCause().javaClass.simpleName)
            Log.e(TAG, "NRFR-compatible CarrierConfig operation failed", failure)
            SimOperationLog.error(
                context,
                "فشل CarrierConfig: ${failure.rootCause().message ?: failure.rootCause().javaClass.simpleName}",
            )
            finishOperation(
                success = false,
                message = "فشلت عملية CarrierConfig: ${failure.rootCause().message ?: failure.rootCause().javaClass.simpleName}",
            )
        } finally {
            if (adoptedShellIdentity) runCatching { automation?.dropShellPermissionIdentity() }
        }
        finish(resultCode, result)
    }

    // Every call is made inside onCreate after UiAutomation adopts READ_PHONE_STATE
    // as shell identity; the app itself does not use or request this permission.
    @SuppressLint("MissingPermission")
    private fun readSimCards(service: CarrierConfigService): List<SimCardInfo> {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            ?: error("خدمة إدارة الشرائح غير متاحة.")
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            ?: error("خدمة الهاتف غير متاحة.")
        val result = mutableListOf<SimCardInfo>()
        val activeSubscriptions = runCatching {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
                .filter { it.subscriptionId >= 0 && it.simSlotIndex >= 0 }
                .map { it.simSlotIndex to it.subscriptionId }
                .distinctBy { it.second }
        }.getOrDefault(emptyList())
        val slotsToRead = if (activeSubscriptions.isNotEmpty()) {
            activeSubscriptions
        } else {
            // Some OEMs return null here even under shell identity; retain the
            // slot-based fallback used by older Android telephony services.
            (0..1).mapNotNull { slotIndex ->
                subscriptionIdForSlot(subscriptionManager, slotIndex)?.let { slotIndex to it }
            }
        }
        for ((slotIndex, subId) in slotsToRead) {
            // NRFR treats an unreadable CarrierConfig as an empty override and
            // still exposes the SIM.  Keep the same per-SIM fault isolation so
            // one OEM-specific config failure cannot hide every active SIM.
            val config = runCatching {
                service.getConfigForSubId(subId, context.packageName)
            }.getOrNull()
            val current = linkedMapOf<String, String>()
            config?.getString(AndroidCarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)
                ?.takeIf { it.isNotEmpty() }?.let { current["رمز البلد"] = it }
            if (config?.getBoolean(AndroidCarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false) == true) {
                config.getString(AndroidCarrierConfigManager.KEY_CARRIER_NAME_STRING)
                    ?.takeIf { it.isNotEmpty() }?.let { current["اسم المشغّل"] = it }
            }
            val carrierName = runCatching {
                telephonyManager.createForSubscriptionId(subId).networkOperatorName
            }.getOrDefault(telephonyManager.networkOperatorName)
            result += SimCardInfo(slot = slotIndex + 1, subscriptionId = subId, carrierName = carrierName, currentConfig = current)
        }
        return result
    }

    // Called only by readSimCards while the instrumentation's shell identity
    // is active; the app itself does not request READ_PHONE_STATE.
    @SuppressLint("MissingPermission")
    private fun subscriptionIdForSlot(manager: SubscriptionManager, slotIndex: Int): Int? {
        val hiddenResult = runCatching {
            val method = SubscriptionManager::class.java.getDeclaredMethod("getSubId", Int::class.javaPrimitiveType)
            method.isAccessible = true
            (method.invoke(null, slotIndex) as? IntArray)?.firstOrNull()
        }.getOrNull()?.takeIf { it >= 0 }
        if (hiddenResult != null) return hiddenResult
        return runCatching {
            manager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)?.subscriptionId
        }.getOrNull()?.takeIf { it >= 0 }
    }

    private fun buildNrfrOverride(countryCode: String?, carrierName: String?): PersistableBundle {
        val bundle = PersistableBundle()
        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                AndroidCarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase(Locale.ROOT),
            )
        }
        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(AndroidCarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(AndroidCarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
        }
        return bundle
    }

    private fun saveSimCards(simCards: List<SimCardInfo>) {
        val array = JSONArray()
        simCards.forEach { sim ->
            val config = JSONObject()
            sim.currentConfig.forEach { (key, value) -> config.put(key, value) }
            array.put(
                JSONObject()
                    .put("slot", sim.slot)
                    .put("subId", sim.subscriptionId)
                    .put("carrierName", sim.carrierName)
                    .put("currentConfig", config),
            )
        }
        val prefs = context.getSharedPreferences(SimCarrierPrefs.PREFS, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putString(SimCarrierPrefs.SIM_CARDS_JSON, array.toString())
            .putLong(SimCarrierPrefs.SIM_CACHE_TIME, System.currentTimeMillis())
            .commit()) { "تعذر حفظ بيانات الشرائح محلياً." }
    }

    private fun finishOperation(success: Boolean, message: String) {
        val prefs = context.getSharedPreferences(SimCarrierPrefs.PREFS, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putBoolean(SimCarrierPrefs.OPERATION_PENDING, false)
            .remove(SimCarrierPrefs.OPERATION_KIND)
            .putString(SimCarrierPrefs.OUTCOME_MESSAGE, message)
            .putBoolean(SimCarrierPrefs.OUTCOME_SUCCESS, success)
            .putLong(SimCarrierPrefs.OUTCOME_TIME, System.currentTimeMillis())
            .commit()) { "تعذر حفظ نتيجة العملية." }
    }

    private fun Throwable.rootCause(): Throwable {
        var current = this
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current
    }

    private data class CarrierConfigService(
        val loader: Any,
        val interfaceClass: Class<*>,
    ) {
        fun getConfigForSubId(subId: Int, packageName: String): PersistableBundle? = invoke(
            "getConfigForSubId",
            arrayOf(Int::class.javaPrimitiveType!!, String::class.java),
            arrayOf(subId, packageName),
        ) as? PersistableBundle

        fun overrideConfig(subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
            invoke(
                "overrideConfig",
                arrayOf(Int::class.javaPrimitiveType!!, PersistableBundle::class.java, Boolean::class.javaPrimitiveType!!),
                arrayOf(subId, bundle, persistent),
            )
        }

        private fun invoke(name: String, parameterTypes: Array<Class<*>>, args: Array<Any?>): Any? {
            val method = interfaceClass.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
            return try {
                method.invoke(loader, *args)
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                throw failure.targetException
            }
        }

        companion object {
            @SuppressLint("BlockedPrivateApi", "SoonBlockedPrivateApi", "PrivateApi")
            fun connect(): CarrierConfigService {
                val initializer = Class.forName("android.telephony.TelephonyFrameworkInitializer")
                val serviceManager = initializer.getDeclaredMethod("getTelephonyServiceManager")
                    .apply { isAccessible = true }
                    .invoke(null)
                    ?: error("TelephonyServiceManager غير متاح.")
                val registerer = serviceManager.javaClass.getDeclaredMethod("getCarrierConfigServiceRegisterer")
                    .apply { isAccessible = true }
                    .invoke(serviceManager)
                    ?: error("CarrierConfig service registerer غير متاح.")
                val binder = registerer.javaClass.getDeclaredMethod("get")
                    .apply { isAccessible = true }
                    .invoke(registerer) as? IBinder
                    ?: error("Binder خدمة CarrierConfig غير متاح.")
                val interfaceClass = Class.forName("com.android.internal.telephony.ICarrierConfigLoader")
                val stubClass = Class.forName("com.android.internal.telephony.ICarrierConfigLoader\$Stub")
                val loader = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
                    .apply { isAccessible = true }
                    .invoke(null, binder)
                    ?: error("تعذر إنشاء واجهة ICarrierConfigLoader.")
                return CarrierConfigService(loader, interfaceClass)
            }
        }
    }

    private companion object {
        const val TAG = "SimCarrierConfigInstrumentation"
    }
}

internal data class SimLogEntry(val timestamp: String, val level: String, val message: String)

/** Page-local log: persists the command and result transcript on this device only. */
internal object SimOperationLog {
    private const val PREFS = "sim_operator_logs"
    private const val ENTRIES = "entries"
    private const val MAX_ENTRIES = 300
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    fun info(context: Context, message: String) { append(context, "INFO", message) }
    fun command(context: Context, command: String) { append(context, "CMD", "\$ $command") }
    fun output(context: Context, output: String) { append(context, "OUT", output) }
    fun error(context: Context, message: String) { append(context, "ERR", message) }

    fun read(context: Context): List<SimLogEntry> = synchronized(lock) {
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENTRIES, "[]")
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let {
                    SimLogEntry(it.optString("time"), it.optString("level"), it.optString("message"))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun append(context: Context, level: String, message: String) = synchronized(lock) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = JSONArray(prefs.getString(ENTRIES, "[]"))
            val entries = mutableListOf<JSONObject>()
            for (index in 0 until current.length()) current.optJSONObject(index)?.let(entries::add)
            val time = synchronized(timeFormat) { timeFormat.format(Date()) }
            entries.add(JSONObject()
                .put("time", time)
                .put("level", level)
                .put("message", message.replace("\r", "\\r").replace("\n", "\\n").take(4_000)))
            val kept = entries.takeLast(MAX_ENTRIES)
            val result = JSONArray().apply { kept.forEach { put(it) } }
            check(prefs.edit().putString(ENTRIES, result.toString()).commit()) { "تعذر حفظ سجل SIM محلياً." }
        }
    }
}
