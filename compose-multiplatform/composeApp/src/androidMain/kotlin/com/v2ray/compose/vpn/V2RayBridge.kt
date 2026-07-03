package com.v2ray.compose.vpn

import android.util.Log

/**
 * JNI bridge to the native transport (`libv2ray.so`, built from `src/androidMain/cpp`).
 *
 * The native library links two pieces:
 *   • **Xray-core** — compiled to a c-shared library from `cpp/xray/xray.go`
 *     (`StartXray`/`StopXray`); it opens a local SOCKS inbound on 127.0.0.1:10808.
 *   • **hev-socks5-tunnel** — the tun2socks engine that pumps packets between the
 *     VpnService TUN fd and that SOCKS inbound.
 *
 * The `external` declarations compile without the `.so` present; the library is
 * only required at runtime. [available] reports whether it loaded so the service
 * can surface a clear error instead of crashing.
 */
object V2RayBridge {

    @Volatile
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("v2ray")
            true
        } catch (t: Throwable) {
            Log.w("V2RayBridge", "native libv2ray.so not bundled: ${t.message}")
            false
        }
    }

    /** Start Xray-core with a full config JSON. Returns an error string, empty on success. */
    external fun startXray(configJson: String): String

    /** Stop the running Xray-core instance. */
    external fun stopXray()

    /** Query Xray-core version (for the About screen / diagnostics). */
    external fun xrayVersion(): String

    /**
     * Start tun2socks. [tunFd] is the VpnService TUN file descriptor; traffic is
     * forwarded to the SOCKS inbound at [socksAddress]:[socksPort].
     */
    external fun startTun2socks(tunFd: Int, socksAddress: String, socksPort: Int, mtu: Int)

    external fun stopTun2socks()

    /** Cumulative traffic counters: index 0 = uplink bytes, 1 = downlink bytes. */
    external fun queryStats(): LongArray
}
