package com.v2ray.compose.vpn

import android.util.Log

/**
 * JNI wrapper for hev-socks5-tunnel (tun2socks) — the same native library and
 * API v2rayNG uses (`libhev-socks5-tunnel.so`). It pumps IP packets between the
 * VpnService TUN fd and Xray-core's local SOCKS inbound.
 *
 * The class name and method names must match the hev JNI build: the `.so` is
 * compiled with `-DPKGNAME=com/v2ray/compose/vpn`, so the exported symbols are
 * `Java_com_v2ray_compose_vpn_TProxyService_TProxy*`. The `external`
 * declarations compile without the `.so`; it is only needed at runtime.
 */
object TProxyService {

    @Volatile
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("hev-socks5-tunnel")
            true
        } catch (t: Throwable) {
            Log.w("TProxyService", "libhev-socks5-tunnel.so not bundled: ${t.message}")
            false
        }
    }

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStopService()

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyGetStats(): LongArray?

    /** Start tun2socks with a YAML config file bridging [tunFd] to the local SOCKS proxy. */
    fun start(configPath: String, tunFd: Int) = TProxyStartService(configPath, tunFd)

    fun stop() = TProxyStopService()

    /** [uploadBytes, downloadBytes] or null when unavailable. */
    fun stats(): LongArray? = runCatching { TProxyGetStats() }.getOrNull()

    /** Build the hev-socks5-tunnel YAML config. */
    fun buildConfig(mtu: Int, ipv4Client: String, socksAddress: String, socksPort: Int): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $mtu")
        appendLine("  ipv4: $ipv4Client")
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: $socksAddress")
        appendLine("  udp: 'udp'")
    }
}
