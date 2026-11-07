package com.proxyplatform.app.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.proxyplatform.app.MainActivity
import com.proxyplatform.app.ProxyFailureMessages
import com.proxyplatform.app.R

/** Pair Wireless ADB from the notification shade without opening the application. */
object AdbPairingNotifier {
    const val NOTIFICATION_ID = 5701
    const val ACTION_PAIR_FROM_NOTIFICATION = "com.proxyplatform.app.action.PAIR_WIRELESS_ADB"
    const val EXTRA_PAIRING_CODE = "com.proxyplatform.app.extra.WIRELESS_ADB_PAIRING_CODE"
    const val REMOTE_INPUT_KEY = "com.proxyplatform.app.remoteinput.WIRELESS_ADB_PAIRING_CODE"

    private const val CHANNEL_ID = "adb_pairing"
    private const val REQUEST_PAIR = 5701
    private const val REQUEST_OPEN_APP = 5702

    fun showPairing(context: Context, statusText: String? = null): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = pairingNotification(context, statusText)
        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun progressNotification(context: Context, statusText: String = "جارٍ الاقتران والاتصال عبر Wireless ADB…"): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("اقتران KUN Proxy")
            .setContentText(statusText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    fun showResult(context: Context, success: Boolean, detail: String? = null) {
        if (!success) {
            val message = ProxyFailureMessages.pairing(detail ?: "pairing failed")
                .lineSequence().take(2).joinToString(" ")
            showPairing(context, message)
            return
        }
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("تم اقتران KUN Proxy بنجاح")
            .setContentText("أصبح التصحيح اللاسلكي جاهزًا. ارجع للتطبيق لتشغيل الوضع المتقدم.")
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun pairingNotification(context: Context, statusText: String?): Notification {
        ensureChannel(context)
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel("رمز الاقتران — 6 أرقام")
            .setAllowFreeFormInput(true)
            .build()
        val replyIntent = Intent(context, AdbPairingActionReceiver::class.java).apply {
            action = ACTION_PAIR_FROM_NOTIFICATION
        }
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_PAIR,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_nav_proxy,
            "إدخال الرمز والاقتران",
            replyPendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("اقتران KUN Proxy")
            .setContentText(statusText ?: "أدخل رمز Wireless debugging من الإشعار دون فتح التطبيق")
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .addAction(replyAction)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "اقتران التصحيح اللاسلكي",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "إدخال رمز اقتران Android من الإشعار ومتابعة الاتصال"
                setShowBadge(false)
            },
        )
    }
}
