package com.proxyplatform.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SingBoxConfig {
    fun write(
        context: Context,
        protocol: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        localPort: Int = 10808,
        blockWebRtc: Boolean = false,
        allowUdp: Boolean = false,
    ): File {
        val outbound = JSONObject()
            .put("type", if (protocol == "socks5") "socks" else protocol)
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", port)
            // TCP-only providers remain usable; when UDP ASSOCIATE succeeds,
            // the network field is omitted so sing-box can relay TCP and UDP.
        if (protocol in setOf("vmess", "vless", "trojan", "shadowsocks")) {
            outbound.put("multiplex", JSONObject().put("enabled", true).put("protocol", "smux").put("max_connections", 4))
        }
        if (!allowUdp) outbound.put("network", "tcp")
        if (username.isNotBlank()) {
            outbound.put("username", username)
            outbound.put("password", password)
        }

        val dns = JSONObject()
            .put("type", "https")
            .put("tag", "cloudflare-doh")
            .put("server", "1.1.1.1")
            .put("server_port", 443)
            .put("path", "/dns-query")
            .put("detour", "proxy")

        val config = JSONObject()
            .put("log", JSONObject().put("level", "error"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put(dns))
                .put("final", "cloudflare-doh")
                .put("strategy", "prefer_ipv4"))
            .put("inbounds", JSONArray().put(JSONObject()
                .put("type", "mixed")
                .put("tag", "mixed-in")
                .put("listen", "127.0.0.1")
                .put("listen_port", localPort)))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("route", JSONObject()
                .put("rules", JSONArray())
                .put("auto_detect_interface", false)
                .put("final", "proxy"))

        return File(context.filesDir, CONFIG_FILE).also {
            it.parentFile?.mkdirs()
            it.writeText(config.toString(2))
        }
    }

    /** Same TCP-adaptive outbound with an Android VpnService TUN inbound. */
    fun writeTun(
        context: Context,
        protocol: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        includeMockLocationInbound: Boolean = false,
        blockWebRtc: Boolean = false,
        allowUdp: Boolean = false,
    ): File {
        val outbound = JSONObject()
            .put("type", if (protocol == "socks5") "socks" else protocol)
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", port)
        if (protocol in setOf("vmess", "vless", "trojan", "shadowsocks")) {
            outbound.put("multiplex", JSONObject().put("enabled", true).put("protocol", "smux").put("max_connections", 4))
        }
        if (!allowUdp) outbound.put("network", "tcp")
        if (username.isNotBlank()) {
            outbound.put("username", username)
            outbound.put("password", password)
        }

        val dns = JSONObject()
            .put("type", "https")
            .put("tag", "cloudflare-doh")
            .put("server", "1.1.1.1")
            .put("server_port", 443)
            .put("path", "/dns-query")
            .put("detour", "proxy")

        val tunInbounds = JSONArray()
        if (includeMockLocationInbound) {
            tunInbounds.put(JSONObject()
                .put("type", "mixed")
                .put("tag", "mock-location-in")
                .put("listen", "127.0.0.1")
                .put("listen_port", ProxyVpnService.MOCK_LOCATION_PROXY_PORT))
        }
        tunInbounds.put(JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("interface_name", "proxyplatform")
            .put("address", JSONArray().put(TUN_ADDRESS).put(TUN_IPV6_ADDRESS))
            .put("mtu", TUN_MTU)
            .put("auto_route", true)
            .put("strict_route", false)
            .put("stack", "gvisor")
            .put("sniff", false))

        val config = JSONObject()
            .put("log", JSONObject().put("level", "error"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put(dns))
                .put("final", "cloudflare-doh")
                .put("strategy", "prefer_ipv4"))
            .put("inbounds", tunInbounds)
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("route", JSONObject()
                // DNS is handled by sing-box; upstream traffic is TCP-only so
                // SOCKS5 providers without UDP relay still work normally.
                .put("rules", JSONArray().apply {
                    put(JSONObject().put("port", 53).put("action", "hijack-dns"))
                    // A TCP-only SOCKS5 server cannot carry WebRTC/UDP. Reject
                    // those packets locally instead of sending them to a SOCKS
                    // outbound that will fail and stall the browser session.
                    if (!allowUdp) put(JSONObject().put("network", "udp").put("action", "reject"))
                })
                // On Android, TunPlatformInterface protects the upstream socket
                // through VpnService.protect(). Generic interface auto-detection
                // can select the TUN itself and create a routing loop.
                .put("auto_detect_interface", false)
                .put("final", "proxy"))

        return File(context.filesDir, TUN_CONFIG_FILE).also {
            it.parentFile?.mkdirs()
            it.writeText(config.toString(2))
        }
    }

    private const val CONFIG_FILE = "Config.json"
    private const val TUN_CONFIG_FILE = "ConfigTun.json"
    const val TUN_ADDRESS = "172.19.0.1/28"
    private const val TUN_IPV6_ADDRESS = "fdfe:dcba:9876::1/126"
    const val TUN_MTU = 1400
}
