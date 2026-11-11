package com.proxyplatform.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

internal sealed interface ShizukuStatus {
    data object NotRunning : ShizukuStatus
    data object PermissionRequired : ShizukuStatus
    data class Ready(val uid: Int) : ShizukuStatus
}

internal object ShizukuGate {
    const val REQUEST_CODE = 4_281
    private const val SHIZUKU_DOWNLOAD_URL = "https://github.com/RikkaApps/Shizuku/releases"

    fun status(): ShizukuStatus = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !Shizuku.pingBinder()) {
            ShizukuStatus.NotRunning
        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            ShizukuStatus.PermissionRequired
        } else {
            ShizukuStatus.Ready(Shizuku.getUid())
        }
    }.getOrDefault(ShizukuStatus.NotRunning)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun openDownloadPage(context: Context) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL)))
        }
    }

    fun isReady(): Boolean = status() is ShizukuStatus.Ready
}
