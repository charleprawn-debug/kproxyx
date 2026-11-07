package com.proxyplatform.app

import io.nekohasekai.libbox.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Platform interface for the one-tap VPN connection mode.
 *
 * Unlike [SingBoxPlatformInterface] (local proxy mode, which needs Shizuku to
 * set the system proxy), [openTun] here hands sing-box a real TUN file
 * descriptor obtained through the standard `android.net.VpnService.Builder`
 * — the same mechanism every VPN app on Google Play uses. The only consent
 * required is Android's single, built-in "Connection request" system dialog.
 */
class TunPlatformInterface(private val service: ProxyVpnService) : PlatformInterface {
    private val protectFailureLogged = AtomicBoolean(false)

    override fun autoDetectInterfaceControl(fd: Int) {
        val protected = runCatching { service.protect(fd) }.getOrDefault(false)
        if (!protected && protectFailureLogged.compareAndSet(false, true)) {
            AdvancedOperationLog.error(
                service,
                "Android لم يتمكن من حماية اتصال sing-box الخارجي من الرجوع إلى واجهة VPN."
            )
        }
    }
    override fun openTun(options: TunOptions): Int = service.establishTun(options)
    override fun includeAllNetworks() = false
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun usePlatformBridge() = false
    override fun usePlatformShell() = false
    override fun useProcFS() = false
    override fun registerMyInterface(name: String) = Unit
    override fun clearDNSCache() = Unit
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun readWIFIState(): WIFIState? = null
    override fun tailscaleHostname(): String = ""
    override fun underNetworkExtension() = false
    override fun getInterfaces(): NetworkInterfaceIterator = object : NetworkInterfaceIterator {
        override fun hasNext() = false
        override fun next(): NetworkInterface = error("No cached interface list")
    }
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) = Unit
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) = Unit
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun sendNotification(notification: Notification) = Unit
    override fun cancelNotification(group: String?, id: Int) = Unit
    override fun findConnectionOwner(
        network: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner = ConnectionOwnerFallback.unknown()
    override fun lookupUser(username: String): PlatformUser? = null
    override fun lookupSFTPServer(): String = ""
    override fun checkPlatformShell() = error("Platform shell is not available")
    override fun openShellSession(
        user: PlatformUser,
        command: String,
        environ: StringIterator,
        term: String,
        rows: Int,
        cols: Int
    ): ShellSession? = null
    override fun createBridge(options: BridgeOptions): BridgeSession? = null
    override fun readSystemSSHHostKey(): String = ""
}
