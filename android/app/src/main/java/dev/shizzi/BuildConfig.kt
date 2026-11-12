package dev.shizzi

/** Build constants for the embedded Shizze service layer. */
internal object BuildConfig {
    const val APPLICATION_ID: String = "com.proxyplatform.app"
    val DEBUG: Boolean get() = com.proxyplatform.app.BuildConfig.DEBUG
    val SERVICE_BUILD_ID: Int get() = com.proxyplatform.app.BuildConfig.VERSION_CODE
}
