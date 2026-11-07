package com.proxyplatform.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Local proxy service that runs sing-box with a mixed (SOCKS + HTTP) inbound
 * on 127.0.0.1 instead of a TUN-based VPN. This avoids the system VPN dialog
 * and the persistent key icon entirely.
 *
 * Android's global HTTP proxy is set through the app's embedded Wireless ADB
 * shell client (`settings put global http_proxy`) from the UI layer.
 */
class ProxyLocalService : Service(), CommandServerHandler {
    private val tag = "ProxyLocalService"
    private var commandServer: CommandServer? = null
    private var setupReady = false

    override fun onCreate() {
        super.onCreate()
        AdvancedOperationLog.info(this, "إنشاء خدمة البروكسي المحلي.")
        createNotificationChannel()
        runCatching {
            startForegroundCompat("جارٍ تجهيز البروكسي المحلي")
            AdvancedOperationLog.info(this, "بدء تهيئة محرك sing-box الأصلي.")
            setupLibbox()
            setupReady = true
            AdvancedOperationLog.output(this, "اكتملت تهيئة محرك sing-box.")
        }.onFailure { error ->
            Log.e(tag, "Libbox setup failed", error)
            AdvancedOperationLog.exception(this, "فشلت تهيئة sing-box", error)
            recordError("تعذر تشغيل محرك البروكسي الأصلي: ${error.rootCauseMessage()}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AdvancedOperationLog.info(this, "استلمت الخدمة طلب إيقاف.")
            stopTunnel(clearError = true)
            return START_NOT_STICKY
        }
        if (!setupReady) {
            AdvancedOperationLog.error(this, "رفض بدء البروكسي: محرك sing-box غير جاهز.")
            recordError("محرك البروكسي الأصلي غير متاح على هذا الجهاز.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (commandServer != null) return START_STICKY

        val host = intent?.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL).orEmpty().lowercase()
        val username = intent?.getStringExtra(EXTRA_USERNAME)?.trim().orEmpty()
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val requestedLocalPort = intent?.getIntExtra(EXTRA_LOCAL_PORT, DEFAULT_LOCAL_PORT) ?: DEFAULT_LOCAL_PORT

        if (host.isBlank() || port !in 1..65535 || protocol !in setOf("http", "socks", "socks5")) {
            AdvancedOperationLog.error(this, "بيانات upstream غير صالحة؛ لا تُسجّل بيانات الاعتماد.")
            recordError("أدخل مضيف البروكسي والمنفذ والبروتوكول بشكل صحيح.")
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            AdvancedOperationLog.info(
                this,
                "إعداد outbound: endpoint=$host:$port, protocol=${protocol.uppercase()}, authentication=${username.isNotBlank()}؛ بيانات الاعتماد لا تُسجّل."
            )
            val localPort = findAvailableLocalPort(requestedLocalPort)
            AdvancedOperationLog.info(this, "المنفذ المحلي المختار: 127.0.0.1:$localPort.")
            val config = SingBoxConfig.write(this, protocol, host, port, username, password, localPort)
            AdvancedOperationLog.info(this, "تم إنشاء إعداد sing-box في مساحة التطبيق الخاصة.")
            val server = CommandServer(this, SingBoxPlatformInterface())
            server.start()
            AdvancedOperationLog.info(this, "بدء CommandServer وتحميل إعداد البروكسي المحلي.")
            server.startOrReloadService(config.readText(), OverrideOptions().apply { autoRedirect = false })
            commandServer = server
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, true)
                .putInt(KEY_LOCAL_PORT, localPort)
                .remove(KEY_ERROR)
                .apply()
            AdvancedOperationLog.output(this, "sing-box يعمل؛ inbound=mixed, listen=127.0.0.1:$localPort.")
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("البروكسي المحلي نشط"))
            START_STICKY
        } catch (error: Exception) {
            Log.e(tag, "Local proxy startup failed", error)
            AdvancedOperationLog.exception(this, "فشل تشغيل البروكسي المحلي", error)
            recordError("تعذر تشغيل البروكسي المحلي: ${error.rootCauseMessage()}")
            stopTunnel()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
    }

    @Suppress("DEPRECATION")
    private fun startForegroundCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification(text))
        }
    }

    private fun stopTunnel(clearError: Boolean = false) {
        AdvancedOperationLog.info(this, "إغلاق خدمة sing-box والبروكسي المحلي.")
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false)
        if (clearError) editor.remove(KEY_ERROR)
        editor.apply()
        AdvancedOperationLog.output(this, "أُغلقت خدمة البروكسي المحلي.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordError(message: String) {
        AdvancedOperationLog.error(this, message)
        val userMessage = ProxyFailureMessages.connection(advanced = true, details = message)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false)
            .putString(KEY_ERROR, userMessage)
            .apply()
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(userMessage.replace('\n', ' ')))
        }
    }

    private fun findAvailableLocalPort(preferredPort: Int): Int {
        if (preferredPort in 1024..65535 && canBindLoopback(preferredPort)) return preferredPort
        return ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }
    }

    private fun canBindLoopback(port: Int): Boolean = runCatching {
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK_HOST)).use { }
        true
    }.getOrDefault(false)

    private fun Throwable.rootCauseMessage(): String {
        var cause: Throwable = this
        while (cause.cause != null) cause = cause.cause!!
        return cause.message ?: cause.javaClass.simpleName
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "البروكسي المحلي",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "حالة البروكسي المحلي النشط"
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
        const val ACTION_START = "com.proxyplatform.app.action.START_PROXY"
        const val ACTION_STOP = "com.proxyplatform.app.action.STOP_PROXY"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_LOCAL_PORT = "local_port"
        const val DEFAULT_LOCAL_PORT = 10808
        const val PREFS = "proxy_vpn"
        const val KEY_RUNNING = "running"
        private const val KEY_LOCAL_PORT = "local_port"
        private const val KEY_ERROR = "last_error"
        const val CHANNEL_ID = "proxy_local_service"
        private const val NOTIFICATION_ID = 7001
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val LISTENER_CHECK_TIMEOUT_MS = 500

        fun isRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

        fun isListenerReady(context: Context): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(LOOPBACK_HOST, localPort(context)),
                    LISTENER_CHECK_TIMEOUT_MS
                )
            }
            true
        }.getOrDefault(false)

        fun lastError(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ERROR, null)

        fun localPort(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_LOCAL_PORT, DEFAULT_LOCAL_PORT)

        fun clearLastError(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ERROR).apply()
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProxyLocalService::class.java))
        }
    }
}
