package com.proxyplatform.app

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File

/**
 * Libbox is process-global. Each Android process initializes it once and owns
 * its own filesystem namespace so cache locks cannot cross service boundaries.
 */
internal object LibboxRuntime {
    @Volatile private var initialized = false

    @Synchronized
    fun ensure(context: Context, logMaxLines: Int = 300, namespace: String? = null) {
        if (initialized) return
        val app = context.applicationContext
        val suffix = namespace?.trim()?.takeIf { it.isNotEmpty() }
        val base = suffix?.let { File(app.filesDir, it) } ?: app.filesDir
        val working = suffix?.let { File(app.getExternalFilesDir(null) ?: app.filesDir, it) }
            ?: (app.getExternalFilesDir(null) ?: app.filesDir)
        val temp = suffix?.let { File(app.cacheDir, it) } ?: app.cacheDir
        base.mkdirs()
        working.mkdirs()
        temp.mkdirs()
        Libbox.setup(SetupOptions().apply {
            basePath = base.path
            workingPath = working.path
            tempPath = temp.path
            fixAndroidStack = true
            this.logMaxLines = logMaxLines.toLong()
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
            debug = BuildConfig.DEBUG
        })
        initialized = true
    }
}
