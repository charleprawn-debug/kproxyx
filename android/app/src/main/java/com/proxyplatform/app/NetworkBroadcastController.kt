package com.proxyplatform.app

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.Continuation

/** Owns the shell-privileged Shizuku UserService used by the broadcast tab. */
internal object NetworkBroadcastController {
    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, BroadcastShellService::class.java.name),
    ).daemon(true).tag("kuncloak-broadcast").version(1)

    private var remote: IBroadcastShellService? = null
    private var pending: Continuation<IBroadcastShellService>? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder?.let { IBroadcastShellService.Stub.asInterface(it) }
            if (service == null) pending?.resumeWithException(IllegalStateException("Shizuku UserService returned no binder"))
            else { remote = service; pending?.resume(service) }
            pending = null
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
        }
    }

    suspend fun start(): Result<String> = call { it.startBroadcast() }
    suspend fun stop(): Result<String> = call { it.stopBroadcast() }
    suspend fun status(): Result<String> = call { it.getStatus() }

    private suspend fun call(operation: (IBroadcastShellService) -> String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(ShizukuGate.isReady()) { "Shizuku is not ready" }
            val service = remote?.takeIf { it.asBinder().pingBinder() } ?: bind()
            operation(service)
        }
    }

    private suspend fun bind(): IBroadcastShellService = suspendCoroutine { continuation ->
        pending = continuation
        try {
            Shizuku.bindUserService(args, connection)
        } catch (failure: Throwable) {
            pending = null
            continuation.resumeWithException(failure)
        }
    }
}
