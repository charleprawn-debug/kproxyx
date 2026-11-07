package com.proxyplatform.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** Persistent local diagnostics captured from process startup and mirrored for pull/export. */
internal object AdvancedOperationLog {
    private const val TAG = "AdvancedOperationLog"
    private const val FILE_NAME = "advanced-operation.log"
    private const val EXIT_INFO_PREFS = "advanced_exit_info"
    private const val LAST_EXIT_TIMESTAMP = "last_exit_timestamp"
    private const val MAX_ENTRY_CHARS = 32_768
    private const val EXIT_TRACE_MAX_CHARS = 256_000
    private val lock = Any()
    private val crashHandlerInstalled = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "advanced-operation-log").apply { isDaemon = true }
    }
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val secretPatterns = listOf(
        Regex("(?i)((?:access[_-]?token|refresh[_-]?token|password|passwd|token|authorization|pairing[ _-]?code)\"?)(\\s*[:=]\\s*\"?)([^\"'\\s,;}]+)"),
        Regex("(?i)([a-z][a-z0-9+.-]*://)([^/@\\s:]+):([^/@\\s]+)@")
    )

    fun installCrashHandler(context: Context) {
        if (!crashHandlerInstalled.compareAndSet(false, true)) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            fatal(app, thread, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun info(context: Context, message: String) = append(context, "INFO", message)
    fun command(context: Context, command: String) = append(context, "CMD", "\$ $command")
    fun output(context: Context, output: String) = append(
        context,
        "OUT",
        output.trimEnd().ifBlank { "<لا يوجد مخرجات من الأمر>" }
    )
    fun error(context: Context, message: String) = append(context, "ERR", message)
    fun singBox(context: Context, message: String) = append(context, "SINGBOX", message)

    fun exception(context: Context, stage: String, throwable: Throwable) {
        error(context, "$stage: ${throwable.javaClass.name}: ${throwable.message ?: "بلا تفاصيل"}")
        throwable.stackTraceToString()
            .take(EXIT_TRACE_MAX_CHARS)
            .lineSequence()
            .forEach { append(context, "TRACE", it) }
    }

    fun appStarted(context: Context) {
        info(
            context,
            "========== بدء عملية التطبيق: package=${context.packageName}, version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}), pid=${android.os.Process.myPid()}, sdk=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL} =========="
        )
        capturePreviousExit(context)
    }

    fun activityEvent(context: Context, event: String, activityName: String) {
        info(context, "Activity $event: $activityName")
    }

    fun append(context: Context, level: String, message: String) {
        val safe = redact(message)
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .take(MAX_ENTRY_CHARS)
        val line = "${timestamp()} [$level] $safe\n"
        writer.execute { writeLine(context, line) }
    }

    /** Persist fatal details synchronously before Android's default crash handler terminates the process. */
    fun fatal(context: Context, thread: Thread, throwable: Throwable) {
        val now = timestamp()
        val lines = buildList {
            add("$now [FATAL] Uncaught exception on thread '${thread.name}': ${redact(throwable.toString())}\n")
            throwable.stackTraceToString()
                .take(EXIT_TRACE_MAX_CHARS)
                .lineSequence()
                .forEach { add("$now [TRACE] ${redact(it)}\n") }
        }
        try {
            val persist = {
                lines.forEach { writeLine(context, it) }
                syncFiles(context)
            }
            if (Thread.currentThread().name == "advanced-operation-log") {
                persist()
            } else {
                writer.submit { persist() }.get(8, TimeUnit.SECONDS)
            }
        } catch (failure: Exception) {
            Log.e(TAG, "Unable to persist fatal crash details", failure)
        }
    }

    fun flush(context: Context) {
        runCatching { writer.execute { syncFiles(context) } }
            .onFailure { Log.e(TAG, "Unable to queue operation log sync", it) }
    }

    fun readLines(context: Context, maxLines: Int = 800): List<String> = runCatching {
        require(maxLines > 0) { "يجب أن يكون عدد أسطر السجل موجبًا." }
        writer.submit<List<String>> {
            synchronized(lock) {
                val file = logFile(context).takeIf(File::exists) ?: return@synchronized emptyList()
                val tail = ArrayDeque<String>(maxLines)
                file.forEachLine(Charsets.UTF_8) { line ->
                    if (tail.size == maxLines) tail.removeFirst()
                    tail.addLast(line)
                }
                tail.toList()
            }
        }.get()
    }.onFailure { Log.e(TAG, "Unable to read operation log", it) }
        .getOrDefault(emptyList())

    /** Copy the complete transcript to a Storage Access Framework destination. */
    fun export(context: Context, destination: Uri): Result<Long> = runCatching {
        writer.submit<Long> {
            synchronized(lock) {
                val source = logFile(context)
                check(source.exists()) { "ملف السجل غير موجود بعد." }
                val output = context.contentResolver.openOutputStream(destination, "wt")
                    ?: error("تعذر فتح ملف الحفظ.")
                output.use { sink -> source.inputStream().use { it.copyTo(sink) } }
                source.length()
            }
        }.get()
    }

    fun adbPullPath(context: Context): String? = mirrorFile(context)?.absolutePath

    private fun capturePreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val exits = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            val prefs = context.getSharedPreferences(EXIT_INFO_PREFS, Context.MODE_PRIVATE)
            val lastTimestamp = prefs.getLong(LAST_EXIT_TIMESTAMP, 0L)
            exits.asReversed().forEach { exit ->
                if (exit.timestamp <= lastTimestamp) return@forEach
                val reason = processExitReasonName(exit.reason)
                val details = "Android process-exit report: reason=$reason (${exit.reason}), status=${exit.status}, importance=${exit.importance}, pss=${exit.pss}, description=${redact(exit.description.orEmpty())}"
                if (exit.reason in setOf(
                        ApplicationExitInfo.REASON_CRASH,
                        ApplicationExitInfo.REASON_CRASH_NATIVE,
                        ApplicationExitInfo.REASON_ANR,
                        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                    )
                ) error(context, details) else info(context, details)
                val trace = runCatching {
                    exit.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull().orEmpty().take(EXIT_TRACE_MAX_CHARS)
                if (trace.isNotBlank()) {
                    trace.lineSequence().forEach { append(context, "EXIT_TRACE", it) }
                }
                prefs.edit().putLong(LAST_EXIT_TIMESTAMP, exit.timestamp).apply()
            }
        }.onFailure { Log.w(TAG, "Unable to read previous Android process exit reasons", it) }
    }

    private fun writeLine(context: Context, line: String) {
        synchronized(lock) {
            runCatching {
                logFile(context).apply { parentFile?.mkdirs() }.appendText(line, Charsets.UTF_8)
            }.onFailure { Log.e(TAG, "Unable to write private operation log", it) }
            mirrorFile(context)?.let { file ->
                runCatching {
                    file.apply { parentFile?.mkdirs() }.appendText(line, Charsets.UTF_8)
                }.onFailure { Log.e(TAG, "Unable to write pullable operation log", it) }
            }
        }
    }

    private fun syncFiles(context: Context) {
        synchronized(lock) {
            listOfNotNull(logFile(context), mirrorFile(context)).distinct().forEach { file ->
                if (!file.exists()) return@forEach
                runCatching { FileOutputStream(file, true).use { it.fd.sync() } }
                    .onFailure { Log.w(TAG, "Unable to sync operation log to storage", it) }
            }
        }
    }

    private fun timestamp(): String = synchronized(timeFormat) { timeFormat.format(Date()) }

    private fun processExitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        else -> "OTHER_OR_UNKNOWN"
    }

    private fun redact(value: String): String {
        var result = value
        secretPatterns.forEach { pattern ->
            result = pattern.replace(result) { match ->
                if (match.groupValues.size >= 4 && match.groupValues[1].endsWith("://")) {
                    "${match.groupValues[1]}[REDACTED]@"
                } else {
                    "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
                }
            }
        }
        return result
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun mirrorFile(context: Context): File? {
        val documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        return File(File(documents, "ProxyPlatform"), FILE_NAME)
    }
}
