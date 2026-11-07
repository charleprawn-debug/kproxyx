package com.proxyplatform.app.adb

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.proxyplatform.app.WirelessDebuggingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Performs the user-requested, short Wireless ADB pairing operation from a notification action. */
class AdbPairingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pairingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PAIR) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val pairingCode = PairingCodeInput.normalize(
            intent.getStringExtra(AdbPairingNotifier.EXTRA_PAIRING_CODE).orEmpty(),
        )
        startForegroundCompat(AdbPairingNotifier.progressNotification(this))
        if (pairingCode == null) {
            AdbPairingNotifier.showResult(this, success = false, detail = "invalid pairing code")
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (pairingJob?.isActive == true) return START_NOT_STICKY

        pairingJob = serviceScope.launch {
            val result = runCatching { WirelessDebuggingManager.pair(applicationContext, pairingCode).getOrThrow() }
            if (result.isSuccess) {
                AdbPairingNotifier.showResult(this@AdbPairingService, success = true)
            } else {
                AdbPairingNotifier.showResult(
                    this@AdbPairingService,
                    success = false,
                    detail = result.exceptionOrNull()?.message,
                )
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AdbPairingNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(AdbPairingNotifier.NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAIR = "com.proxyplatform.app.action.RUN_WIRELESS_ADB_PAIRING"
    }
}
