package com.proxyplatform.app

/** Adds a small stdout marker because libadb 3.1.x reports a clean, empty shell close as "Stream closed". */
internal object AdbShellCommand {
    const val EXIT_MARKER = "__PROXYPLATFORM_ADB_EXIT_7D693A__"
    private val statusPattern = Regex("(?m)^${Regex.escape(EXIT_MARKER)}:(\\d+)\\r?$")

    data class Response(val output: String, val exitCode: Int)

    fun wrap(command: String): String =
        "${command.trimEnd()}\n__proxy_platform_status=\$?; printf '\\n$EXIT_MARKER:%s\\n' \"\$__proxy_platform_status\""

    fun hasCompletionMarker(response: String): Boolean = statusPattern.containsMatchIn(response)

    fun parse(response: String): Response {
        val marker = statusPattern.find(response)
            ?: error("لم يُرجع ADB علامة اكتمال الأمر؛ لم يتم تأكيد انتهاء الأمر عن بُعد.")
        val trailing = response.substring(marker.range.last + 1).trim()
        require(trailing.isEmpty()) { "وصلت بيانات غير متوقعة بعد علامة اكتمال ADB." }
        val output = response.substring(0, marker.range.first).trimEnd('\r', '\n')
        return Response(output, marker.groupValues[1].toInt())
    }
}
