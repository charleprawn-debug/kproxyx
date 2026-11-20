package com.proxyplatform.app.adb

import com.proxyplatform.app.tr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

/** Receives only the explicit RemoteInput PendingIntent attached by [AdbPairingNotifier]. */
class AdbPairingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbPairingNotifier.ACTION_PAIR_FROM_NOTIFICATION) return
        val rawCode = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AdbPairingNotifier.REMOTE_INPUT_KEY)
            ?.toString()
            .orEmpty()
        val pairingCode = PairingCodeInput.normalize(rawCode)
        if (pairingCode == null) {
            AdbPairingNotifier.showPairing(context, tr("أدخل رمزًا صحيحًا مكوّنًا من 6 أرقام ثم حاول مجددًا.", "Enter a valid 6-digit code and try again."))
            return
        }

        val serviceIntent = Intent(context, AdbPairingService::class.java).apply {
            action = AdbPairingService.ACTION_PAIR
            putExtra(AdbPairingNotifier.EXTRA_PAIRING_CODE, pairingCode)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            .onFailure {
                // Do not put the submitted code or credentials in logs or notifications.
                AdbPairingNotifier.showPairing(context, tr("تعذر بدء خدمة الاقتران. افتح التطبيق وحاول مرة أخرى.", "Could not start the pairing service. Open the app and try again."))
            }
    }
}
