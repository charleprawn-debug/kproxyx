package com.proxyplatform.app.adb

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
            AdbPairingNotifier.showPairing(context, "أدخل رمزًا صحيحًا مكوّنًا من 6 أرقام ثم حاول مجددًا.")
            return
        }

        val serviceIntent = Intent(context, AdbPairingService::class.java).apply {
            action = AdbPairingService.ACTION_PAIR
            putExtra(AdbPairingNotifier.EXTRA_PAIRING_CODE, pairingCode)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            .onFailure {
                // Do not put the submitted code or credentials in logs or notifications.
                AdbPairingNotifier.showPairing(context, "تعذر بدء خدمة الاقتران. افتح التطبيق وحاول مرة أخرى.")
            }
    }
}
