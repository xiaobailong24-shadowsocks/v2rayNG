import Foundation

/// Runs the `hev-socks5-tunnel` tun2socks engine on a background thread. The
/// C entry points are exposed through the bridging header
/// (`PacketTunnel-Bridging-Header.h`) and linked from `libhev-socks5-tunnel.a`
/// (built for iOS — see NATIVE.md).
final class Tun2socks {
    static let shared = Tun2socks()
    private init() {}

    private var worker: Thread?

    func start(tunnelFd: Int32, socksAddress: String, socksPort: Int, mtu: Int) {
        let config = """
        tunnel:
          mtu: \(mtu)
        socks5:
          address: \(socksAddress)
          port: \(socksPort)
          udp: 'udp'
        """
        let thread = Thread {
            let bytes = Array(config.utf8)
            // Blocks until stop() calls hev_socks5_tunnel_quit().
            _ = hev_socks5_tunnel_main_from_str(bytes, UInt32(bytes.count), tunnelFd)
        }
        thread.stackSize = 8 * 1024 * 1024
        worker = thread
        thread.start()
    }

    func stop() {
        hev_socks5_tunnel_quit()
        worker = nil
    }
}
