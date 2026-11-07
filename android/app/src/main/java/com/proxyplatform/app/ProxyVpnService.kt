package com.proxyplatform.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions

/**
 * Recommended, one-tap connection mode: routes device traffic through a
 * standard Android VpnService TUN interface running sing-box.
 *
 * This is the low-friction path — the user only sees Android's built-in
 * "Connection request" dialog once (handled by [VpnService.prepare] in
 * [MainActivity]). No Developer options, no wireless debugging pairing, and
 * no separate app (Shizuku) to install. The advanced local-proxy mode in
 * [ProxyLocalService] remains available for users who specifically want to
 * avoid the system VPN key icon.
 */
class ProxyVpnService : VpnService(), CommandServerHandler {
    private var commandServer: CommandServer? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var setupReady = false

    override fun onCreate() {
        super.onCreate()
        AdvancedOperationLog.info(this, "إنشاء خدمة VPN.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AdvancedOperationLog.info(this, "استلمت خدمة VPN طلب إيقاف.")
            stopTunnel(clearError = true)
            return START_NOT_STICKY
        }
        if (commandServer != null) return START_STICKY

        val host = intent?.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL).orEmpty().lowercase()
        val username = intent?.getStringExtra(EXTRA_USERNAME)?.trim().orEmpty()
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val includeMockLocationInbound = intent?.getBooleanExtra(EXTRA_MOCK_LOCATION_INBOUND, false) ?: false

        if (host.isBlank() || port !in 1..65535 || protocol !in setOf("http", "socks", "socks5")) {
            AdvancedOperationLog.error(this, "بيانات upstream لنفق VPN غير صالحة؛ لا تُسجّل بيانات الاعتماد.")
            recordError("أدخل مضيف البروكسي والمنفذ والبروتوكول بشكل صحيح.")
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            AdvancedOperationLog.info(this, "بدء نفق VPN: protocol=${protocol.uppercase()}, endpoint=$host:$port, authentication=${username.isNotBlank()}.")
            startForegroundCompat("جارٍ تجهيز نفق VPN")
            if (!setupReady) {
                AdvancedOperationLog.info(this, "تهيئة محرك sing-box لوضع VPN.")
                setupLibbox()
                setupReady = true
            }
            val config = SingBoxConfig.writeTun(
                this,
                protocol,
                host,
                port,
                username,
                password,
                includeMockLocationInbound,
            )
            val server = CommandServer(this, TunPlatformInterface(this))
            server.start()
            server.startOrReloadService(config.readText(), OverrideOptions().apply { autoRedirect = false })
            commandServer = server
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, true)
                .remove(KEY_ERROR)
                .apply()
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("نفق VPN نشط"))
            AdvancedOperationLog.output(this, "اكتمل تشغيل نفق VPN بنجاح.")
            START_STICKY
        } catch (error: Exception) {
            AdvancedOperationLog.error(this, "فشل تشغيل نفق VPN: ${error.javaClass.simpleName}: ${error.message ?: "بلا تفاصيل"}")
            recordError("تعذر تشغيل نفق VPN: ${error.message ?: "تحقق من بيانات البروكسي"}")
            stopTunnel()
            START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        // The user revoked VPN access from system settings (or another VPN app took over).
        AdvancedOperationLog.error(this, "ألغى Android إذن VPN أو استحوذ تطبيق VPN آخر على الاتصال.")
        stopTunnel(clearError = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        AdvancedOperationLog.info(this, "تدمير خدمة VPN.")
        stopTunnel()
        super.onDestroy()
    }

    /**
     * Called by [TunPlatformInterface.openTun]. Builds the VpnService
     * interface from sing-box's requested [TunOptions] and returns the raw
     * file descriptor sing-box should read/write packets from.
     */
    fun establishTun(options: TunOptions): Int {
        val mtu = options.getMTU()
        val builder = Builder()
            .setSession("KUN Proxy")
            .setMtu(if (mtu > 0) mtu else SingBoxConfig.TUN_MTU)
            .setBlocking(false)

        val inet4 = options.getInet4Address()
        while (inet4.hasNext()) {
            val prefix = inet4.next()
            runCatching { builder.addAddress(prefix.address(), prefix.prefix()) }
        }
        val inet6 = options.getInet6Address()
        while (inet6.hasNext()) {
            val prefix = inet6.next()
            runCatching { builder.addAddress(prefix.address(), prefix.prefix()) }
        }

        if (options.getAutoRoute()) {
            val route4 = options.getInet4RouteAddress()
            if (!route4.hasNext()) {
                builder.addRoute("0.0.0.0", 0)
            } else {
                while (route4.hasNext()) {
                    val prefix = route4.next()
                    runCatching { builder.addRoute(prefix.address(), prefix.prefix()) }
                }
            }
            val route6 = options.getInet6RouteAddress()
            while (route6.hasNext()) {
                val prefix = route6.next()
                runCatching { builder.addRoute(prefix.address(), prefix.prefix()) }
            }
            val dns = options.getDNSServerAddress()
            while (dns.hasNext()) {
                runCatching { builder.addDnsServer(dns.next()) }
            }
        }

        // Exclude our own app from the tunnel so the app's own connection to
        // the proxy server (and to the API) never loops back through the TUN.
        runCatching { builder.addDisallowedApplication(packageName) }

        val pfd = builder.establish()
            ?: error("VPN permission is not granted. Call VpnService.prepare() first.")
        tunFd?.let { old -> runCatching { old.close() } }
        tunFd = pfd
        return pfd.fd
    }

    private fun setupLibbox() {
        val working = getExternalFilesDir(null) ?: filesDir
        Libbox.setup(SetupOptions().apply {
            basePath = filesDir.path
            workingPath = working.path
            tempPath = cacheDir.path
            fixAndroidStack = true
            logMaxLines = 300
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
            debug = BuildConfig.DEBUG
        })
        AdvancedOperationLog.info(
            this,
            "محرك sing-box=${Libbox.version()}، TUN stack=gvisor، MTU=${SingBoxConfig.TUN_MTU}."
        )
    }

    @Suppress("DEPRECATION")
    private fun startForegroundCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(text))
        }
    }

    private fun stopTunnel(clearError: Boolean = false) {
        AdvancedOperationLog.info(this, "إغلاق نفق VPN وخدمة sing-box.")
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        tunFd?.let { runCatching { it.close() } }
        tunFd = null
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false)
        if (clearError) editor.remove(KEY_ERROR)
        editor.apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordError(message: String) {
        AdvancedOperationLog.error(this, message)
        val userMessage = ProxyFailureMessages.connection(advanced = false, details = message)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false)
            .putString(KEY_ERROR, userMessage)
            .apply()
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(userMessage.replace('\n', ' ')))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "نفق VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "حالة نفق VPN النشط"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("KUN Proxy")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun connectSSHAgent(): Int = -1
    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply { available = false; enabled = false }
    override fun serviceReload() = Unit
    override fun serviceStop() = stopTunnel()
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun triggerNativeCrash() = Unit
    override fun writeDebugMessage(message: String) {
        AdvancedOperationLog.singBox(this, message)
    }

    companion object {
        const val ACTION_START = "com.proxyplatform.app.action.START_VPN"
        const val ACTION_STOP = "com.proxyplatform.app.action.STOP_VPN"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_MOCK_LOCATION_INBOUND = "include_mock_location_inbound"
        const val MOCK_LOCATION_PROXY_PORT = 10809
        const val PREFS = "proxy_vpn_tun"
        const val KEY_RUNNING = "running"
        private const val KEY_ERROR = "last_error"
        const val CHANNEL_ID = "proxy_vpn_service"
        private const val NOTIFICATION_ID = 7002

        fun isRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

        fun lastError(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ERROR, null)

        fun clearLastError(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ERROR).apply()
        }

        fun start(
            context: Context,
            protocol: String,
            host: String,
            port: String,
            username: String,
            password: String,
            includeMockLocationInbound: Boolean = false,
        ) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROTOCOL, protocol)
                putExtra(EXTRA_HOST, host.trim())
                putExtra(EXTRA_PORT, port.toInt())
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_MOCK_LOCATION_INBOUND, includeMockLocationInbound)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ProxyVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
