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
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * A proxy listener owned only by independent GeoIP features.
 * It never changes Android's system proxy and is never used by advanced mode.
 */
class FeatureProxyService : Service(), CommandServerHandler {
    private val worker = Executors.newSingleThreadExecutor()
    private var commandServer: CommandServer? = null
    private var setupReady = false
    @Volatile private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runCatching {
            startForegroundCompat("تجهيز خدمة الموقع والمنطقة الزمنية")
            LibboxRuntime.ensure(this, logMaxLines = 300, namespace = FEATURE_NAMESPACE)
            setupReady = true
        }.onFailure { failure ->
            Log.e(TAG, "Feature proxy setup failed", failure)
            recordError("تعذر تشغيل خدمة الميزات: ${failure.rootCauseMessage()}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        worker.execute {
            if (!destroyed) {
                if (intent?.action == ACTION_STOP) stopTunnel(true) else startTunnel(intent)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(intent: Intent?) {
        if (!setupReady || commandServer != null) return
        val host = intent?.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL).orEmpty().lowercase()
        val username = intent?.getStringExtra(EXTRA_USERNAME)?.trim().orEmpty()
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty()
        if (host.isBlank() || port !in 1..65535 || protocol !in setOf("http", "socks", "socks5")) {
            recordError("بيانات بروكسي الميزات غير صالحة.")
            stopSelf()
            return
        }
        try {
            val localPort = findAvailablePort()
            val config = SingBoxConfig.write(
                this, protocol, host, port, username, password,
                localPort = localPort,
                blockWebRtc = false,
                allowUdp = false,
                configFileName = FEATURE_CONFIG_FILE,
            )
            val server = CommandServer(this, SingBoxPlatformInterface())
            commandServer = server
            server.start()
            server.startOrReloadService(config.readText(), OverrideOptions().apply { autoRedirect = false })
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, true)
                .putInt(KEY_LOCAL_PORT, localPort)
                .remove(KEY_ERROR)
                .apply()
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("خدمة الموقع والمنطقة الزمنية نشطة"))
        } catch (failure: Exception) {
            Log.e(TAG, "Feature proxy startup failed", failure)
            recordError("تعذر تشغيل خدمة الميزات: ${failure.rootCauseMessage()}")
            stopTunnel()
        }
    }

    override fun onDestroy() {
        destroyed = true
        worker.execute { stopTunnel() }
        worker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun startForegroundCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification(text))
    }

    private fun stopTunnel(clearError: Boolean = false) {
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false)
            .apply { if (clearError) remove(KEY_ERROR) }
            .apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordError(message: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false)
            .putString(KEY_ERROR, message)
            .apply()
        AdvancedOperationLog.error(this, message)
    }

    private fun findAvailablePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("Kun cloak")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ميزات الموقع والمنطقة الزمنية", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun Throwable.rootCauseMessage(): String {
        var cause: Throwable = this
        while (cause.cause != null) cause = cause.cause!!
        return cause.message ?: cause.javaClass.simpleName
    }

    override fun connectSSHAgent(): Int = -1
    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply { available = false; enabled = false }
    override fun serviceReload() = Unit
    override fun serviceStop() { if (!worker.isShutdown) worker.execute { stopTunnel() } }
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun triggerNativeCrash() = Unit
    override fun writeDebugMessage(message: String) { AdvancedOperationLog.singBox(this, message) }

    companion object {
        private const val TAG = "FeatureProxyService"
        const val ACTION_START = "com.proxyplatform.app.action.START_FEATURE_PROXY"
        const val ACTION_STOP = "com.proxyplatform.app.action.STOP_FEATURE_PROXY"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val PREFS = "feature_proxy_service"
        const val FEATURE_CONFIG_FILE = "feature_proxy/FeatureConfig.json"
        private const val FEATURE_NAMESPACE = "feature_proxy"
        private const val KEY_RUNNING = "running"
        private const val KEY_LOCAL_PORT = "local_port"
        private const val KEY_ERROR = "last_error"
        private const val CHANNEL_ID = "feature_proxy_service"
        // Keep this distinct from ProxyVpnService, which may run at the same time.
        private const val NOTIFICATION_ID = 7003
        private const val LOOPBACK = "127.0.0.1"

        fun isRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

        fun localPort(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LOCAL_PORT, 0)

        fun isListenerReady(context: Context): Boolean {
            val port = localPort(context)
            if (!isRunning(context) || port !in 1..65535) return false
            return runCatching {
                Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), 400) }
                true
            }.getOrDefault(false)
        }

        fun lastError(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ERROR, null)

        fun stop(context: Context) { context.stopService(Intent(context, FeatureProxyService::class.java)) }

        fun start(context: Context, protocol: String, host: String, port: Int, username: String, password: String) {
            val intent = Intent(context, FeatureProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROTOCOL, protocol)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_PASSWORD, password)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
