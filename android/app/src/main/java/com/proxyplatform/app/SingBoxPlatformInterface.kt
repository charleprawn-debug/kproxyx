package com.proxyplatform.app

import io.nekohasekai.libbox.*

/**
 * Minimal platform interface for the local proxy mode.
 *
 * Unlike the VPN-based approach, no TUN device is opened and no
 * VpnService.protect() calls are needed. The sing-box engine runs a
 * local mixed (SOCKS + HTTP) inbound on 127.0.0.1; the system HTTP
 * proxy is set separately via Shizuku shell commands.
 */
class SingBoxPlatformInterface : PlatformInterface {
    override fun autoDetectInterfaceControl(fd: Int) = Unit
    override fun openTun(options: TunOptions): Int = error("TUN is not used in local proxy mode")
    override fun includeAllNetworks() = false
    override fun usePlatformAutoDetectInterfaceControl() = false
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
