package com.proxyplatform.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Application language, independent of the device language. Arabic is the historical default. */
internal object AppLanguage {
    private const val PREFERENCES = "app_language"
    private const val KEY_ARABIC = "is_arabic"
    private const val ACTION_CHANGED = "com.proxyplatform.app.LANGUAGE_CHANGED"
    private var appContext: Context? = null
    private var preferences: SharedPreferences? = null
    private var currentArabic by mutableStateOf(true)
    private val receiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_CHANGED && intent.hasExtra(KEY_ARABIC)) {
                    currentArabic = intent.getBooleanExtra(KEY_ARABIC, true)
                }
            }
        }
    }

    /** Must be called from Application.onCreate, including in secondary processes. */
    fun initialize(context: Context) {
        if (preferences != null) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        appContext = app
        preferences = prefs
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Registration and initialization run on the main thread. A concurrent
        // broadcast is delivered afterwards, so it cannot be overwritten by an
        // older initial preference value.
        currentArabic = prefs.getBoolean(KEY_ARABIC, true)
    }

    /** Reading this property in composition subscribes the caller to language changes. */
    var isArabic: Boolean
        get() = currentArabic
        set(value) {
            if (currentArabic == value) return
            currentArabic = value
            // Commit before notifying other processes so a newly started process
            // cannot read the previous value while the disk write is pending.
            preferences?.edit()?.putBoolean(KEY_ARABIC, value)?.commit()
            appContext?.let { app ->
                app.sendBroadcast(Intent(ACTION_CHANGED).setPackage(app.packageName).putExtra(KEY_ARABIC, value))
            }
        }
}

/** Select a UI string; safe to call from Compose or ordinary Kotlin code. */
internal fun tr(ar: String, en: String): String = if (AppLanguage.isArabic) ar else en
