package com.proxyplatform.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

class ProxyPlatformApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdvancedOperationLog.installCrashHandler(this)
        AdvancedOperationLog.appStarted(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                AdvancedOperationLog.activityEvent(this@ProxyPlatformApplication, "created", activity.javaClass.simpleName)

            override fun onActivityStarted(activity: Activity) =
                AdvancedOperationLog.activityEvent(this@ProxyPlatformApplication, "started", activity.javaClass.simpleName)

            override fun onActivityResumed(activity: Activity) =
                AdvancedOperationLog.activityEvent(this@ProxyPlatformApplication, "resumed", activity.javaClass.simpleName)

            override fun onActivityPaused(activity: Activity) =
                AdvancedOperationLog.activityEvent(this@ProxyPlatformApplication, "paused", activity.javaClass.simpleName)

            override fun onActivityStopped(activity: Activity) {
                AdvancedOperationLog.activityEvent(this@ProxyPlatformApplication, "stopped", activity.javaClass.simpleName)
                AdvancedOperationLog.flush(this@ProxyPlatformApplication)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) =
                AdvancedOperationLog.activityEvent(this@ProxyPlatformApplication, "destroyed", activity.javaClass.simpleName)
        })
    }
}
