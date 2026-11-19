package com.proxyplatform.app

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions

/**
 * Libbox is process-global. All proxy services must initialize it once; calling
 * Libbox.setup again while another service is active can stop or corrupt the
 * existing command server on some Android devices.
 */
internal object LibboxRuntime {
    @Volatile private var initialized = false

    @Synchronized
    fun ensure(context: Context, logMaxLines: Int = 300) {
        if (initialized) return
        val app = context.applicationContext
        Libbox.setup(SetupOptions().apply {
            basePath = app.filesDir.path
            workingPath = (app.getExternalFilesDir(null) ?: app.filesDir).path
            tempPath = app.cacheDir.path
            fixAndroidStack = true
            this.logMaxLines = logMaxLines.toLong()
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
            debug = BuildConfig.DEBUG
        })
        initialized = true
    }
}
