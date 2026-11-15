package com.proxyplatform.app.adb

// The createUserService bootstrap is adapted from Shizuku's UserService.java
// (Apache License 2.0, Copyright RikkaApps contributors). Modified for the
// Wireless ADB socket bridge in KProxyX.
import android.net.LocalSocket
import android.net.LocalServerSocket
import android.content.Context
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONObject

/** Entry point for app_process; it is launched by Wireless ADB as UID shell. */
object AdbBroadcastHelperMain {
    @JvmStatic
    fun main(args: Array<String>) {
        var server: LocalServerSocket? = null
        var service: ServiceBridge? = null
        try {
            System.err.println("HELPER_MAIN_ENTERED args=${args.size}")
            System.err.flush()
            Looper.prepareMainLooper()

            val socketName = args.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: error("broadcast helper socket name is missing")
            val packageName = args.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: error("broadcast helper package name is missing")
            val serviceClassName = args.getOrNull(2)?.takeIf { it.isNotBlank() }
                ?: error("broadcast helper service class is missing")
            val userId = args.getOrNull(3)?.toIntOrNull() ?: 0
            server = LocalServerSocket(socketName)
            service = createUserService(packageName, serviceClassName, userId)
            val activeServer = server ?: error("broadcast server was not created")
            val activeService = service ?: error("tether service was not created")

            val socketThread = Thread({
                try {
                    var keepRunning = true
                    while (keepRunning) {
                        val client = activeServer.accept()
                        keepRunning = handle(client, activeService)
                    }
                } catch (failure: Throwable) {
                    failure.printStackTrace(System.err)
                    System.err.flush()
                } finally {
                    runCatching { activeService.stop() }
                    runCatching { activeServer.close() }
                    Looper.getMainLooper().quitSafely()
                }
            }, "kuncloak-broadcast-socket")
            socketThread.isDaemon = true
            socketThread.start()

            // Tethering callbacks are posted to the main Handler by Android.
            Looper.loop()
        } catch (failure: Throwable) {
            failure.printStackTrace(System.err)
            System.err.flush()
            throw failure
        } finally {
            runCatching { service?.stop() }
            runCatching { server?.close() }
        }
    }

    /** Minimal equivalent of Shizuku v13's UserService bootstrap. */
    private fun createUserService(
        packageName: String,
        serviceClassName: String,
        userId: Int,
    ): ServiceBridge {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val systemMain = activityThreadClass.getMethod("systemMain").invoke(null)
        val systemContext = activityThreadClass
            .getMethod("getSystemContext")
            .invoke(systemMain) as Context
        val userHandleClass = Class.forName("android.os.UserHandle")
        val userHandle = userHandleClass.getMethod("of", Integer.TYPE).invoke(null, userId)
        val createPackageContextAsUser = Context::class.java.getMethod(
            "createPackageContextAsUser",
            String::class.java,
            Integer.TYPE,
            userHandleClass,
        )
        val appContext = createPackageContextAsUser.invoke(
            systemContext,
            packageName,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            userHandle,
        ) as Context

        val packageInfoField: Field = appContext.javaClass.getDeclaredField("mPackageInfo")
        packageInfoField.isAccessible = true
        val loadedApk = packageInfoField.get(appContext)
            ?: error("loaded APK is missing for $packageName")
        val makeApplication: Method = loadedApk.javaClass.getDeclaredMethod(
            "makeApplication",
            java.lang.Boolean.TYPE,
            android.app.Instrumentation::class.java,
        )
        makeApplication.isAccessible = true
        val application = makeApplication.invoke(loadedApk, true, null)
            as? android.app.Application
            ?: error("could not create application for $packageName")

        val initialApplication = activityThreadClass.getDeclaredField("mInitialApplication")
        initialApplication.isAccessible = true
        initialApplication.set(systemMain, application)

        val serviceClass = application.classLoader.loadClass(serviceClassName)
        val contextConstructor: Constructor<*>? = runCatching {
            serviceClass.getConstructor(Context::class.java)
        }.getOrNull()
        val instance = if (contextConstructor != null) {
            contextConstructor.newInstance(application)
        } else {
            serviceClass.getDeclaredConstructor().newInstance()
        }
        require(instance is android.os.IBinder) {
            "$serviceClassName did not create a Binder service; actual=${instance.javaClass.name}"
        }
        return ServiceBridge(instance)
    }

    private class ServiceBridge(private val target: Any) {
        private val targetClass = target.javaClass

        fun start(): String = call("start", true, "AUTO", "NEVER") as String

        fun startNever(): String = call("start", true, "NEVER", "NEVER") as String

        fun stop(): String = call("stop") as String

        fun status(): String = call("getStatus") as String

        fun compatibility(): String = call("checkCompatibility") as String

        private fun call(name: String, vararg args: Any?): Any? {
            val parameterTypes = args.map { value ->
                when (value) {
                    is Boolean -> java.lang.Boolean.TYPE
                    is String -> String::class.java
                    null -> Any::class.java
                    else -> value.javaClass
                }
            }.toTypedArray()
            val method = targetClass.getMethod(name, *parameterTypes)
            method.isAccessible = true
            return method.invoke(target, *args)
        }
    }

    private fun handle(socket: LocalSocket, service: ServiceBridge): Boolean {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.inputStream, StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(it.outputStream, StandardCharsets.UTF_8))
            val request = reader.readLine().orEmpty()
            val command = runCatching { JSONObject(request).optString("command") }.getOrDefault("")
            val response = runCatching {
                when (command) {
                    "status" -> service.status()
                    "start" -> service.start()
                    "startNever" -> service.startNever()
                    "stop" -> service.stop()
                    "checkCompatibility" -> service.compatibility()
                    "shutdown" -> service.stop()
                    else -> error("unsupported helper command")
                }
            }.fold(
                onSuccess = { JSONObject().put("ok", true).put("result", it) },
                onFailure = { failure ->
                    JSONObject().put("ok", false).put(
                        "error",
                        "${failure.javaClass.simpleName}: ${failure.message.orEmpty()}",
                    )
                },
            )
            writer.write(response.toString())
            writer.newLine()
            writer.flush()
            return command != "shutdown"
        }
    }
}
