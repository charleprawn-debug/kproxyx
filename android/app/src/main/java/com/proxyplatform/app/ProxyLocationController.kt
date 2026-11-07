package com.proxyplatform.app

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Optional user-controlled mock provider. Android requires the user to select
 * this app as the mock-location app in Developer options; the app never bypasses that gate.
 */
class ProxyLocationController(private val context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val thread = HandlerThread("proxy-location").apply { start() }
    private val handler = Handler(thread.looper)
    private val providerLock = Any()
    private val installedProviders = linkedSetOf<String>()
    private val mockModePrefs = context.getSharedPreferences(MOCK_STATE_PREFS, Context.MODE_PRIVATE)
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    @Volatile private var running = false
    @Volatile private var fusedMockModeEnabled = false
    private var lastPoint: GeoPoint? = null
    private var lastSourceRefreshElapsed = 0L

    @Suppress("DEPRECATION")
    fun checkMockLocationAccess(): Result<Unit> {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName,
                )
            }
            if (mode != AppOpsManager.MODE_ALLOWED) {
                throw SecurityException("لم يفعّل Android OP_MOCK_LOCATION لهذا التطبيق (AppOp mode=$mode).")
            }
        }
    }

    /** Reuse the authenticated sing-box outbound instead of asking OkHttp to authenticate SOCKS itself. */
    fun startAutoThroughLocalProxy(
        localPort: Int,
        onStatus: (Result<Pair<Double, Double>>) -> Unit = {},
    ) {
        start(
            stage = "تحديد الموقع عبر منفذ sing-box المحلي",
            sourceRefreshIntervalMs = GEO_IP_REFRESH_MS,
            source = {
                val json = fetchProxyJsonThroughLocal(localPort, "موقع وهمي")
                GeoPoint(json.getDouble("latitude"), json.getDouble("longitude"))
            },
            onStatus = onStatus,
        )
    }

    fun startManual(
        latitude: Double,
        longitude: Double,
        onStatus: (Result<Pair<Double, Double>>) -> Unit = {},
    ) {
        start(
            stage = "تحديد الموقع اليدوي",
            sourceRefreshIntervalMs = Long.MAX_VALUE,
            source = { GeoPoint(latitude, longitude) },
            onStatus = onStatus,
        )
    }

    /**
     * Requests the exit's timezone through the local mixed inbound. sing-box
     * performs upstream protocol/authentication, including authenticated SOCKS5.
     */
    internal fun fetchProxyTimezone(localPort: Int): ProxyTimezone {
        val json = fetchProxyJsonThroughLocal(localPort, "مطابقة المنطقة الزمنية")
        val timezone = ProxyTimezoneParser.parse(json)
        AdvancedOperationLog.output(
            context,
            "GeoIP: البلد=${json.optString("country_code", "غير معروف")}, المنطقة الزمنية=${timezone.id}."
        )
        return timezone
    }

    internal fun fetchProxyLanguage(localPort: Int): ProxyLanguage {
        val json = fetchProxyJsonThroughLocal(localPort, "مطابقة لغة الهاتف")
        val country = json.optString("country_code", "")
        val language = ProxyLanguageResolver.fromCountryCode(country)
        AdvancedOperationLog.output(context, "GeoIP: البلد=$country، لغة الهاتف المقترحة=${language.localeTag}.")
        return language
    }

    private fun fetchProxyJsonThroughLocal(localPort: Int, purpose: String): JSONObject {
        require(localPort in 1..65535) { "منفذ sing-box المحلي غير صالح: $localPort." }
        waitForLoopbackListener(localPort)
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localPort))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
        AdvancedOperationLog.info(
            context,
            "$purpose: GET $GEO_IP_URL عبر SOCKS loopback 127.0.0.1:$localPort؛ يتولى sing-box اتصال الخروج والمصادقة مع البروكسي المحدد."
        )
        return executeGeoIpRequest(client, "sing-box loopback 127.0.0.1:$localPort")
    }

    private fun waitForLoopbackListener(port: Int) {
        val deadline = SystemClock.elapsedRealtime() + LOOPBACK_READY_TIMEOUT_MS
        var lastFailure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), LOOPBACK_CONNECT_TIMEOUT_MS)
                }
                AdvancedOperationLog.info(context, "sing-box loopback listener 127.0.0.1:$port جاهز لطلب GeoIP.")
                return
            } catch (failure: Exception) {
                lastFailure = failure
                Thread.sleep(LOOPBACK_RETRY_MS)
            }
        }
        throw IllegalStateException(
            "لم يصبح منفذ sing-box المحلي 127.0.0.1:$port جاهزًا ضمن المهلة.",
            lastFailure,
        )
    }

    private fun executeGeoIpRequest(client: OkHttpClient, routeDescription: String): JSONObject {
        val request = Request.Builder()
            .url(GEO_IP_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "ProxyPlatform-Android")
            .build()
        val startedAt = SystemClock.elapsedRealtime()
        AdvancedOperationLog.command(context, "GET $GEO_IP_URL via $routeDescription (credentials redacted)")
        try {
            client.newCall(request).execute().use { response ->
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                AdvancedOperationLog.info(
                    context,
                    "GeoIP response: HTTP ${response.code}, successful=${response.isSuccessful}, latency=${elapsed}ms."
                )
                val body = response.body?.string().orEmpty()
                AdvancedOperationLog.info(context, "GeoIP response body size=${body.length} characters; raw body omitted for privacy.")
                if (!response.isSuccessful) {
                    error("فشل تحديد إعدادات بلد البروكسي عبر $routeDescription (HTTP ${response.code}).")
                }
                val json = JSONObject(body)
                if (!json.optBoolean("success", true)) {
                    error("تعذر تحديد موقع البروكسي عبر $routeDescription: ${json.optString("message", "استجابة غير صالحة")}")
                }
                return json
            }
        } catch (failure: Throwable) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            AdvancedOperationLog.exception(context, "GeoIP request failed via $routeDescription after ${elapsed}ms", failure)
            throw failure
        }
    }

    private fun start(
        stage: String,
        sourceRefreshIntervalMs: Long,
        source: () -> GeoPoint,
        onStatus: (Result<Pair<Double, Double>>) -> Unit,
    ) {
        stop()
        running = true
        lastPoint = null
        lastSourceRefreshElapsed = 0L
        AdvancedOperationLog.info(context, "$stage: بدء مزود الموقع الوهمي.")

        handler.post {
            val initialResult = runCatching {
                installProviders()
                source().also { point ->
                    lastPoint = point
                    lastSourceRefreshElapsed = SystemClock.elapsedRealtime()
                    publish(point)
                }
            }
            initialResult.onFailure { failure ->
                running = false
                AdvancedOperationLog.exception(context, "$stage: فشل تشغيل مزود الموقع الوهمي", failure)
                cleanupMockLocation()
                onStatus(Result.failure(failure))
                return@post
            }

            val firstPoint = initialResult.getOrThrow()
            AdvancedOperationLog.output(
                context,
                "$stage: نُشر الموقع الأول (${formatCoordinate(firstPoint.latitude)}, ${formatCoordinate(firstPoint.longitude)}) إلى مزوّدات ${installedProviderNames()}.",
            )
            onStatus(Result.success(firstPoint.asPair()))

            var reportedFailure = false
            var awaitingGeoIpRecovery = false
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (!running) return

                    var refreshDue = false
                    var refreshedSource = false
                    val updateResult = runCatching {
                        val now = SystemClock.elapsedRealtime()
                        refreshDue = now - lastSourceRefreshElapsed >= sourceRefreshIntervalMs
                        val point = if (refreshDue) {
                            // Set the attempt time first, so a temporary GeoIP failure is retried
                            // on the normal refresh cadence rather than every publish tick.
                            lastSourceRefreshElapsed = now
                            source().also {
                                lastSourceRefreshElapsed = SystemClock.elapsedRealtime()
                                lastPoint = it
                                refreshedSource = true
                            }
                        } else {
                            lastPoint ?: error("لا توجد إحداثيات محفوظة لإعادة نشرها.")
                        }
                        publish(point)
                        point
                    }

                    updateResult.onFailure { failure ->
                        if (refreshDue && !refreshedSource) awaitingGeoIpRecovery = true
                        reportedFailure = true
                        AdvancedOperationLog.exception(context, "$stage: فشل تحديث مزوّدات الموقع الوهمي", failure)
                        onStatus(Result.failure(failure))
                    }.onSuccess { point ->
                        if (point != firstPoint && refreshedSource) {
                            AdvancedOperationLog.output(
                                context,
                                "$stage: تم تحديث إحداثيات خروج البروكسي إلى (${formatCoordinate(point.latitude)}, ${formatCoordinate(point.longitude)}).",
                            )
                        }
                        if (reportedFailure && (!awaitingGeoIpRecovery || refreshedSource)) {
                            onStatus(Result.success(point.asPair()))
                            reportedFailure = false
                            awaitingGeoIpRecovery = false
                        }
                    }

                    handler.postDelayed(this, LOCATION_PUBLISH_INTERVAL_MS)
                }
            }, LOCATION_PUBLISH_INTERVAL_MS)
        }
    }

    @SuppressLint("InlinedApi")
    private fun installProviders() {
        checkMockLocationAccess().getOrThrow()
        for (spec in PROVIDER_SPECS) {
            try {
                runCatching { locationManager.removeTestProvider(spec.name) }
                locationManager.addTestProvider(
                    spec.name,
                    spec.requiresNetwork,
                    spec.requiresSatellite,
                    spec.requiresCell,
                    false,
                    spec.supportsAltitude,
                    spec.supportsSpeed,
                    spec.supportsBearing,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE,
                )
                synchronized(providerLock) { installedProviders.add(spec.name) }
                locationManager.setTestProviderEnabled(spec.name, true)
                checkMockLocationAccess().getOrThrow()
                AdvancedOperationLog.info(context, "تم تفعيل test provider: ${spec.name}.")
            } catch (failure: Throwable) {
                runCatching { locationManager.removeTestProvider(spec.name) }
                synchronized(providerLock) { installedProviders.remove(spec.name) }
                if (spec.required) throw failure
                AdvancedOperationLog.exception(context, "تعذر تفعيل مزوّد ${spec.name} الاختياري؛ سيستمر GPS وحده.", failure)
            }
        }
        check(LocationManager.GPS_PROVIDER in synchronized(providerLock) { installedProviders.toSet() }) {
            "لم يتمكن Android من تثبيت مزوّد GPS الوهمي."
        }
    }

    @SuppressLint("MissingPermission") // A coarse-location runtime grant is checked before these mock-only calls.
    private fun publish(point: GeoPoint) {
        checkMockLocationAccess().getOrThrow()
        requireCoarsePermissionForFusedMock()
        ensureFusedMockMode()
        val providers = synchronized(providerLock) { installedProviders.toList() }
        check(providers.isNotEmpty()) { "لا توجد مزوّدات موقع وهمي مفعّلة." }
        val wallTime = System.currentTimeMillis()
        val elapsedTime = SystemClock.elapsedRealtimeNanos()
        val fusedLocation = createLocation(LocationManager.GPS_PROVIDER, point, 12f, wallTime, elapsedTime)
        awaitGoogleTask(
            fusedLocationClient.setMockLocation(fusedLocation),
            "تعذر إرسال الإحداثيات إلى Google Play Services Fused Location Provider",
        )
        providers.forEach { provider ->
            val accuracy = if (provider == LocationManager.GPS_PROVIDER) 12f else 80f
            val location = createLocation(provider, point, accuracy, wallTime, elapsedTime)
            locationManager.setTestProviderLocation(provider, location)
        }
    }

    private fun createLocation(
        provider: String,
        point: GeoPoint,
        accuracy: Float,
        wallTime: Long,
        elapsedTime: Long,
    ) = Location(provider).apply {
        latitude = point.latitude
        longitude = point.longitude
        this.accuracy = accuracy
        time = wallTime
        elapsedRealtimeNanos = elapsedTime
        verticalAccuracyMeters = 50f
    }

    @SuppressLint("MissingPermission", "ApplySharedPref") // Runtime permission is checked; commit persists crash recovery before global change.
    private fun ensureFusedMockMode() {
        if (fusedMockModeEnabled) return
        requireCoarsePermissionForFusedMock()
        check(mockModePrefs.edit().putBoolean(KEY_FUSED_MOCK_MODE_PENDING, true).commit()) {
            "تعذر حفظ حالة استعادة وضع الموقع المدمج."
        }
        try {
            awaitGoogleTask(
                fusedLocationClient.setMockMode(true),
                "تعذر تفعيل وضع الموقع الوهمي في Google Play Services",
            )
            fusedMockModeEnabled = true
            AdvancedOperationLog.output(context, "فعّل Google Play Services Fused mock mode ومسح موقعه المخزّن.")
        } catch (failure: Exception) {
            val disabled = runCatching {
                awaitGoogleTask(
                    fusedLocationClient.setMockMode(false),
                    "تعذر إلغاء تفعيل Fused mock mode بعد فشل البدء",
                )
                true
            }.getOrDefault(false)
            if (disabled) {
                fusedMockModeEnabled = false
                mockModePrefs.edit().remove(KEY_FUSED_MOCK_MODE_PENDING).commit()
            }
            throw IllegalStateException(
                "لم يقبل Google Play Services الموقع الوهمي. تأكد أن Proxy Platform هو تطبيق الموقع الوهمي المحدد وأن خدمات Google Play مفعّلة ومحدّثة. ${failure.message ?: ""}",
                failure,
            )
        }
    }

    @SuppressLint("MissingPermission") // Cleanup must still attempt to turn FLP mock mode off after permission revocation.
    private fun disableFusedMockMode() {
        if (!fusedMockModeEnabled && !mockModePrefs.getBoolean(KEY_FUSED_MOCK_MODE_PENDING, false)) return
        awaitGoogleTask(
            fusedLocationClient.setMockMode(false),
            "تعذر إيقاف وضع الموقع الوهمي في Google Play Services",
        )
        fusedMockModeEnabled = false
        check(mockModePrefs.edit().remove(KEY_FUSED_MOCK_MODE_PENDING).commit()) {
            "تم إيقاف Fused mock mode لكن تعذر مسح علامة الاستعادة."
        }
        AdvancedOperationLog.info(context, "أوقف Google Play Services Fused mock mode ومسح الإحداثيات الوهمية المخزنة.")
    }

    private fun awaitGoogleTask(task: Task<Void>, operation: String) {
        try {
            Tasks.await(task, FUSED_TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (failure: Exception) {
            val cause = (failure as? ExecutionException)?.cause ?: failure
            throw IllegalStateException("$operation: ${cause.message ?: cause.javaClass.simpleName}", cause)
        }
    }

    private fun requireCoarsePermissionForFusedMock() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("يرجى منح إذن الموقع التقريبي لتطبيقات Google كي تستقبل إحداثيات الموقع الوهمي.")
        }
    }

    private fun cleanupMockLocation() {
        runCatching { disableFusedMockMode() }
            .onFailure { AdvancedOperationLog.exception(context, "تعذرت استعادة Google Play Services إلى الموقع الحقيقي", it) }
        removeInstalledProviders()
    }

    private fun removeInstalledProviders() {
        val providers = synchronized(providerLock) {
            installedProviders.toList().also { installedProviders.clear() }
        }
        providers.forEach { provider ->
            runCatching { locationManager.removeTestProvider(provider) }
                .onFailure { AdvancedOperationLog.exception(context, "تعذر إزالة test provider $provider", it) }
        }
        if (providers.isNotEmpty()) {
            AdvancedOperationLog.info(context, "إزالة مزوّدات الموقع الوهمي: ${providers.joinToString()}.")
        }
    }

    private fun installedProviderNames(): String =
        synchronized(providerLock) { installedProviders.joinToString() }

    private fun formatCoordinate(value: Double): String =
        String.format(java.util.Locale.US, "%.5f", value)

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        lastPoint = null
        lastSourceRefreshElapsed = 0L
        handler.post { cleanupMockLocation() }
    }

    fun close() {
        stop()
        thread.quitSafely()
    }

    private data class ProviderSpec(
        val name: String,
        val requiresNetwork: Boolean,
        val requiresSatellite: Boolean,
        val requiresCell: Boolean,
        val supportsAltitude: Boolean,
        val supportsSpeed: Boolean,
        val supportsBearing: Boolean,
        val required: Boolean,
    )

    private data class GeoPoint(val latitude: Double, val longitude: Double) {
        init {
            require(latitude.isFinite() && latitude in -90.0..90.0) { "خط العرض يجب أن يكون بين -90 و90." }
            require(longitude.isFinite() && longitude in -180.0..180.0) { "خط الطول يجب أن يكون بين -180 و180." }
        }

        fun asPair(): Pair<Double, Double> = latitude to longitude
    }

    companion object {
        @SuppressLint("MissingPermission") // Recovery attempts to clear a previously enabled global mock mode.
        fun recoverStaleFusedMockMode(context: Context) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(MOCK_STATE_PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_FUSED_MOCK_MODE_PENDING, false)) return

            AdvancedOperationLog.info(appContext, "اكتشاف Fused mock mode معلّق من جلسة سابقة؛ محاولة إعادته للوضع الطبيعي.")
            val client = try {
                LocationServices.getFusedLocationProviderClient(appContext)
            } catch (failure: Exception) {
                AdvancedOperationLog.exception(appContext, "تعذر إنشاء عميل استعادة Fused mock mode", failure)
                return
            }
            try {
                client.setMockMode(false)
                    .addOnSuccessListener {
                        if (prefs.edit().remove(KEY_FUSED_MOCK_MODE_PENDING).commit()) {
                            AdvancedOperationLog.info(appContext, "اكتملت استعادة Google Play Services إلى الموقع الحقيقي.")
                        } else {
                            AdvancedOperationLog.error(appContext, "أُوقف Fused mock mode لكن بقيت علامة الاستعادة محفوظة.")
                        }
                    }
                    .addOnFailureListener { failure ->
                        AdvancedOperationLog.exception(appContext, "تعذرت استعادة Google Play Services إلى الموقع الحقيقي", failure)
                    }
            } catch (failure: Exception) {
                AdvancedOperationLog.exception(appContext, "تعذر إرسال طلب استعادة Fused mock mode", failure)
            }
        }

        private val PROVIDER_SPECS = listOf(
            ProviderSpec(
                name = LocationManager.GPS_PROVIDER,
                requiresNetwork = false,
                requiresSatellite = true,
                requiresCell = false,
                supportsAltitude = true,
                supportsSpeed = true,
                supportsBearing = true,
                required = true,
            ),
            ProviderSpec(
                name = LocationManager.NETWORK_PROVIDER,
                requiresNetwork = true,
                requiresSatellite = false,
                requiresCell = true,
                supportsAltitude = false,
                supportsSpeed = false,
                supportsBearing = false,
                required = false,
            ),
            ProviderSpec(
                name = FRAMEWORK_FUSED_PROVIDER,
                requiresNetwork = false,
                requiresSatellite = false,
                requiresCell = false,
                supportsAltitude = false,
                supportsSpeed = false,
                supportsBearing = false,
                required = false,
            ),
        )

        private const val MOCK_STATE_PREFS = "proxy_mock_location_state"
        private const val KEY_FUSED_MOCK_MODE_PENDING = "fused_mock_mode_pending_restore"
        private const val FRAMEWORK_FUSED_PROVIDER = "fused"
        private const val LOOPBACK_READY_TIMEOUT_MS = 12_000L
        private const val LOOPBACK_CONNECT_TIMEOUT_MS = 350
        private const val LOOPBACK_RETRY_MS = 200L
        private const val LOCATION_PUBLISH_INTERVAL_MS = 2_000L
        private const val GEO_IP_REFRESH_MS = 15 * 60 * 1000L
        private const val FUSED_TASK_TIMEOUT_MS = 15_000L
        private const val GEO_IP_URL = "https://ipwho.is/"
    }
}
