package com.v2ray.compose.vpn

import hev.hev_socks5_tunnel_main_from_str
import hev.hev_socks5_tunnel_quit
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlin.native.concurrent.Worker

/**
 * tun2socks (hev-socks5-tunnel) driven from Kotlin/Native via cinterop — replaces
 * the former `Tun2socks.swift` + bridging header. The C library is bound through
 * `src/nativeInterop/cinterop/hev.def` and linked into the ComposeApp framework,
 * so the Swift `PacketTunnelProvider` just calls into this shared code.
 *
 * `hev_socks5_tunnel_main_from_str` blocks until [stop], so it runs on a worker.
 */
@OptIn(ExperimentalForeignApi::class)
object IosTun2socks {

    private var worker: Worker? = null

    fun start(mtu: Int, ipv4: String, socksAddress: String, socksPort: Int, tunnelFd: Int) {
        val config = buildConfig(mtu, ipv4, socksAddress, socksPort)
        val w = Worker.start(name = "hev-tun2socks")
        worker = w
        w.executeAfter(0L) {
            val bytes = config.encodeToByteArray()
            bytes.usePinned { pinned ->
                hev_socks5_tunnel_main_from_str(
                    pinned.addressOf(0).reinterpret(),
                    bytes.size.convert(),
                    tunnelFd,
                )
            }
        }
    }

    fun stop() {
        hev_socks5_tunnel_quit()
        worker?.requestTermination(processScheduledJobs = false)
        worker = null
    }

    private fun buildConfig(mtu: Int, ipv4: String, socksAddress: String, socksPort: Int): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $mtu")
        appendLine("  ipv4: $ipv4")
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: $socksAddress")
        appendLine("  udp: 'udp'")
    }
}
