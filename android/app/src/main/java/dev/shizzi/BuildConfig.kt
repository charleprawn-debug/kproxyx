package dev.shizzi

/** Build constants for the embedded Shizze service layer. */
internal object BuildConfig {
    val APPLICATION_ID: String get() = com.proxyplatform.app.BuildConfig.APPLICATION_ID
    val DEBUG: Boolean get() = com.proxyplatform.app.BuildConfig.DEBUG
    val SERVICE_BUILD_ID: Int get() = com.proxyplatform.app.BuildConfig.SHIZUKU_SERVICE_BUILD_ID
}
