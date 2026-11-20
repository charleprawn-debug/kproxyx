package com.proxyplatform.app

import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Checks both the proxy listener and (when requested) a real upstream tunnel before Android routes traffic. */
internal object ProxyEndpointProbe {
    const val DEFAULT_TIMEOUT_MS = 5_000
    private const val TARGET_HOST = "1.1.1.1"
    private const val TARGET_PORT = 443

    /** Backwards-compatible reachability check for callers that only need to test an open TCP port. */
    fun check(
        host: String,
        port: Int,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        trace: (String) -> Unit = {},
    ): Result<Unit> = runCatching {
        validateEndpoint(host, port, timeoutMs)
        trace("بدء TCP إلى ${host.trim()}:$port؛ المهلة=${timeoutMs}ms.")
        Socket().use {
            it.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            trace("نجح اتصال TCP إلى ${host.trim()}:$port.")
        }
    }

    /** Performs the actual proxy handshake and asks it to open an internet-facing TLS tunnel. */
    fun check(
        host: String,
        port: Int,
        protocol: String,
        username: String,
        password: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        trace: (String) -> Unit = {},
    ): Result<Unit> = runCatching {
        validateEndpoint(host, port, timeoutMs)
        require(protocol == "http" || protocol == "socks5") { tr("بروتوكول البروكسي غير مدعوم.", "The proxy protocol is not supported.") }
        require((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotEmpty())) {
            tr("أدخل اسم المستخدم وكلمة المرور معًا، أو أوقف المصادقة.", "Authentication failed: enter both the username and password, or disable authentication.")
        }
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            trace("بدء TCP إلى ${host.trim()}:$port؛ protocol=${protocol.uppercase()}؛ auth=${username.isNotBlank()}؛ المهلة=${timeoutMs}ms.")
            socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            trace("تم TCP؛ اختبار نفق إلى $TARGET_HOST:$TARGET_PORT.")
            when (protocol) {
                "http" -> checkHttpConnect(socket, username, password, trace)
                "socks5" -> checkSocks5Connect(socket, username, password, trace)
                else -> error(tr("بروتوكول البروكسي غير مدعوم.", "The proxy protocol is not supported."))
            }
            trace("نجح handshake البروكسي ونفق الإنترنت.")
        }
    }

    private fun validateEndpoint(host: String, port: Int, timeoutMs: Int) {
        require(host.isNotBlank()) { tr("أدخل عنوان خادم البروكسي.", "Enter the proxy server address.") }
        require(port in 1..65535) { tr("أدخل منفذًا صحيحًا للبروكسي.", "Enter a valid proxy port.") }
        require(timeoutMs > 0) { tr("مهلة الاتصال غير صالحة.", "The connection time limit is invalid.") }
    }

    private fun checkHttpConnect(socket: Socket, username: String, password: String, trace: (String) -> Unit) {
        trace("إرسال HTTP CONNECT إلى $TARGET_HOST:$TARGET_PORT مع auth=${username.isNotBlank()}؛ لا تُسجّل بيانات الاعتماد.")
        val credentials = if (username.isNotBlank()) {
            val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
            "Proxy-Authorization: Basic $encoded\r\n"
        } else {
            ""
        }
        val request = buildString {
            append("CONNECT $TARGET_HOST:$TARGET_PORT HTTP/1.1\r\n")
            append("Host: $TARGET_HOST:$TARGET_PORT\r\n")
            append(credentials)
            append("Proxy-Connection: Keep-Alive\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(StandardCharsets.ISO_8859_1))
            flush()
        }
        val statusLine = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1)).readLine()
            ?: error(tr("أغلق خادم البروكسي الاتصال قبل تأكيد HTTP CONNECT.", "Failed to connect the HTTP tunnel: the proxy server closed the connection before confirming HTTP CONNECT."))
        val statusCode = statusLine.trim().split(Regex("\\s+"))
            .getOrNull(1)?.toIntOrNull()
            ?: error(tr("لم يُرجع خادم البروكسي استجابة HTTP صالحة.", "The proxy server did not return a valid HTTP response."))
        trace("رد HTTP CONNECT: $statusLine.")
        when (statusCode) {
            in 200..299 -> Unit
            407 -> error(tr("رفض البروكسي بيانات المصادقة. تحقق من اسم المستخدم وكلمة المرور.", "Proxy authentication failed. Check the username and password."))
            403 -> error(tr("رفض البروكسي إنشاء نفق إنترنت إلى $TARGET_HOST:$TARGET_PORT.", "The proxy returned HTTP 403 and refused to open an internet tunnel to $TARGET_HOST:$TARGET_PORT."))
            else -> error(tr("فشل اختبار البروكسي HTTP (الاستجابة $statusCode).", "The HTTP proxy test failed (response $statusCode)."))
        }
    }

    /** Confirms the upstream SOCKS5 server grants a UDP relay for VPN-mode WebRTC traffic. */
    fun checkSocks5UdpAssociation(
        host: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        trace: (String) -> Unit = {},
    ): Result<Unit> = runCatching {
        validateEndpoint(host, port, timeoutMs)
        require((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotEmpty())) {
            tr("أدخل اسم المستخدم وكلمة المرور معًا، أو أوقف خيار المصادقة.", "Authentication failed: enter both the username and password, or disable authentication.")
        }
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            trace("بدء SOCKS5 UDP ASSOCIATE إلى ${host.trim()}:$port؛ المهلة=${timeoutMs}ms.")
            socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            val hasCredentials = username.isNotBlank()
            output.write(byteArrayOf(0x05, 0x01, (if (hasCredentials) 0x02 else 0x00).toByte()))
            output.flush()
            val version = input.readUnsignedByte()
            val method = input.readUnsignedByte()
            if (version != 0x05) error(tr("الخادم لا يتحدث SOCKS5 أثناء فحص UDP.", "The server does not speak SOCKS5 during the UDP check."))
            if (method == 0xFF) error(tr("رفض SOCKS5 طرق المصادقة أثناء فحص UDP.", "SOCKS5 authentication failed during the UDP check."))
            if (hasCredentials && method != 0x02) error(tr("لم يقبل SOCKS5 مصادقة اسم المستخدم أثناء فحص UDP.", "SOCKS5 authentication failed: the server did not accept username authentication during the UDP check."))
            if (!hasCredentials && method != 0x00) error(tr("يتطلب SOCKS5 بيانات مصادقة؛ لم تُرسل بيانات ناقصة.", "SOCKS5 authentication failed: credentials are required; incomplete credentials were not sent."))
            trace("SOCKS5 UDP: اختار الخادم طريقة المصادقة $method.")

            if (method == 0x02) {
                val userBytes = username.toByteArray(StandardCharsets.UTF_8)
                val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
                require(userBytes.size in 1..255 && passwordBytes.size in 1..255) {
                    tr("بيانات مصادقة SOCKS5 يجب ألا تتجاوز 255 بايت لكل حقل.", "SOCKS5 authentication failed: each authentication field must contain no more than 255 bytes.")
                }
                output.writeByte(0x01)
                output.writeByte(userBytes.size)
                output.write(userBytes)
                output.writeByte(passwordBytes.size)
                output.write(passwordBytes)
                output.flush()
                val authVersion = input.readUnsignedByte()
                val authStatus = input.readUnsignedByte()
                if (authVersion != 0x01 || authStatus != 0x00) {
                    error(tr("رفض خادم SOCKS5 بيانات المصادقة أثناء فحص UDP.", "SOCKS5 authentication failed during the UDP check."))
                }
                trace("SOCKS5 UDP: المصادقة قُبلت؛ بيانات الاعتماد مخفية.")
            }

            // RFC 1928 command 0x03 (UDP ASSOCIATE), with the client endpoint unspecified.
            output.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
            val replyVersion = input.readUnsignedByte()
            val reply = input.readUnsignedByte()
            input.readUnsignedByte() // reserved
            val addressType = input.readUnsignedByte()
            val relayAddress = when (addressType) {
                0x01 -> InetAddress.getByAddress(ByteArray(4).also(input::readFully)).hostAddress
                0x03 -> ByteArray(input.readUnsignedByte()).also(input::readFully).toString(StandardCharsets.UTF_8)
                0x04 -> InetAddress.getByAddress(ByteArray(16).also(input::readFully)).hostAddress
                else -> error(tr("أعاد SOCKS5 نوع عنوان غير صالح لمرحّل UDP: $addressType.", "SOCKS5 returned an invalid address type for the relay for UDP: $addressType."))
            }
            val relayPort = input.readUnsignedShort()
            trace("رد UDP ASSOCIATE: إصدار=$replyVersion، النتيجة=$reply، relay=$relayAddress:$relayPort.")
            if (replyVersion != 0x05) error(tr("رد SOCKS5 غير صالح على UDP ASSOCIATE.", "The SOCKS5 response to UDP ASSOCIATE was invalid."))
            if (reply != 0x00) error(tr("هذا الخادم SOCKS5 لا يمنح UDP relay (رمز $reply).", "This SOCKS5 server does not provide a UDP relay (code $reply)."))
            if (relayPort == 0) error(tr("وافق SOCKS5 على UDP لكن لم يُرجع منفذ relay صالحًا.", "SOCKS5 accepted UDP but did not return a valid port for the relay."))
            trace("أكد SOCKS5 فتح UDP relay؛ نفق VPN يوجّه UDP إلى SOCKS5 ولا يحتوي مسار direct احتياطيًا.")
        }
    }

    private fun checkSocks5Connect(socket: Socket, username: String, password: String, trace: (String) -> Unit) {
        val hasCredentials = username.isNotBlank()
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        // Offer only the method that will be used by sing-box for this profile.
        output.write(byteArrayOf(0x05, 0x01, (if (hasCredentials) 0x02 else 0x00).toByte()))
        output.flush()
        trace("SOCKS5: عرض طريقة ${if (hasCredentials) "اسم مستخدم/كلمة مرور" else "بدون مصادقة"}.")
        val version = input.readUnsignedByte()
        val method = input.readUnsignedByte()
        trace("SOCKS5: إصدار الخادم=$version، طريقة المصادقة المختارة=$method.")
        if (version != 0x05) error(tr("الخادم لا يتحدث بروتوكول SOCKS5.", "The server does not speak the SOCKS5 protocol."))
        if (method == 0xFF) error(tr("لم يقبل خادم SOCKS5 طريقة المصادقة المحددة.", "SOCKS5 authentication failed: the server rejected the selected authentication method."))
        if (hasCredentials && method != 0x02) error(tr("خادم SOCKS5 لم يقبل مصادقة اسم المستخدم وكلمة المرور.", "SOCKS5 authentication failed: the server rejected the username and password."))
        if (!hasCredentials && method != 0x00) error(tr("يتطلب هذا البروكسي اسم مستخدم وكلمة مرور؛ فعّل خيار المصادقة.", "Proxy authentication failed: a username and password are required. Enable authentication."))

        if (method == 0x02) {
            val userBytes = username.toByteArray(StandardCharsets.UTF_8)
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
            require(userBytes.size in 1..255 && passwordBytes.size in 1..255) {
                tr("بيانات مصادقة SOCKS5 يجب ألا تتجاوز 255 بايت لكل حقل.", "SOCKS5 authentication failed: each authentication field must contain no more than 255 bytes.")
            }
            output.writeByte(0x01)
            output.writeByte(userBytes.size)
            output.write(userBytes)
            output.writeByte(passwordBytes.size)
            output.write(passwordBytes)
            output.flush()
            val authVersion = input.readUnsignedByte()
            val authStatus = input.readUnsignedByte()
            if (authVersion != 0x01 || authStatus != 0x00) {
                error(tr("رفض خادم SOCKS5 اسم المستخدم أو كلمة المرور.", "SOCKS5 authentication failed: the server rejected the username or password."))
            }
            trace("SOCKS5: قَبِل الخادم بيانات المصادقة.")
        }

        // CONNECT 1.1.1.1:443 without relying on device DNS or sending user traffic.
        output.write(byteArrayOf(
            0x05, 0x01, 0x00, 0x01,
            1, 1, 1, 1,
            (TARGET_PORT ushr 8).toByte(), TARGET_PORT.toByte(),
        ))
        output.flush()
        val replyVersion = input.readUnsignedByte()
        val reply = input.readUnsignedByte()
        input.readUnsignedByte() // reserved
        val addressType = input.readUnsignedByte()
        trace("SOCKS5 CONNECT: إصدار=$replyVersion، نتيجة=$reply، نوع العنوان=$addressType.")
        if (replyVersion != 0x05) error(tr("رد SOCKS5 غير صالح أثناء اختبار الاتصال.", "The SOCKS5 response was invalid during the connection test."))
        if (reply != 0x00) error(tr("تعذر على بروكسي SOCKS5 فتح نفق إنترنت (رمز $reply).", "The SOCKS5 proxy could not open an internet tunnel (code $reply)."))
        when (addressType) {
            0x01 -> input.readFully(ByteArray(4))
            0x03 -> input.readFully(ByteArray(input.readUnsignedByte()))
            0x04 -> input.readFully(ByteArray(16))
            else -> error(tr("أعاد بروكسي SOCKS5 عنوانًا غير صالح.", "The SOCKS5 proxy returned an invalid address."))
        }
        input.readUnsignedShort() // bound port
    }
}
