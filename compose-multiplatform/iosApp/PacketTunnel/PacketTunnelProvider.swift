import NetworkExtension
import os

/// Packet Tunnel provider — the iOS counterpart of Android's `V2RayVpnService`.
///
/// Data path: Xray-core (from the `LibXray` xcframework, built by
/// `build-xray-apple.sh`) exposes a local SOCKS inbound on 127.0.0.1:10808, and
/// `hev-socks5-tunnel` bridges this tunnel's packets to it. The Xray JSON is the
/// document produced by the shared `XrayConfigBuilder`, delivered through the
/// tunnel protocol's `providerConfiguration`.
class PacketTunnelProvider: NEPacketTunnelProvider {

    private let log = Logger(subsystem: "com.v2ray.compose.PacketTunnel", category: "tunnel")

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        guard
            let proto = protocolConfiguration as? NETunnelProviderProtocol,
            let providerConfig = proto.providerConfiguration,
            let xrayConfig = providerConfig["xrayConfig"] as? String
        else {
            completionHandler(TunnelError.missingConfig)
            return
        }

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        let ipv4 = NEIPv4Settings(addresses: ["10.10.10.1"], subnetMasks: ["255.255.255.0"])
        ipv4.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = ipv4
        settings.dnsSettings = NEDNSSettings(servers: ["1.1.1.1", "8.8.8.8"])
        settings.mtu = 1500

        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self = self else { return }
            if let error = error {
                self.log.error("setTunnelNetworkSettings failed: \(error.localizedDescription)")
                completionHandler(error)
                return
            }

            // 1) Start Xray-core; it opens the local SOCKS inbound.
            if let startError = XrayCore.shared.start(configJSON: xrayConfig) {
                self.log.error("Xray start failed: \(startError)")
                completionHandler(TunnelError.xrayFailed(startError))
                return
            }

            // 2) Bridge the utun fd to Xray's SOCKS inbound via tun2socks.
            guard let fd = self.tunnelFileDescriptor() else {
                completionHandler(TunnelError.noTunnelFd)
                return
            }
            Tun2socks.shared.start(tunnelFd: fd, socksAddress: "127.0.0.1", socksPort: 10808, mtu: 1500)

            self.log.info("tunnel established")
            completionHandler(nil)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        Tun2socks.shared.stop()
        XrayCore.shared.stop()
        completionHandler()
    }

    /// Locates the utun file descriptor backing this Packet Tunnel.
    private func tunnelFileDescriptor() -> Int32? {
        var buffer = [CChar](repeating: 0, count: Int(IFNAMSIZ))
        for fd in 0...1024 as ClosedRange<Int32> {
            var length = socklen_t(buffer.count)
            let result = getsockopt(fd, 2 /* SYSPROTO_CONTROL */, 2 /* UTUN_OPT_IFNAME */, &buffer, &length)
            if result == 0, String(cString: buffer).hasPrefix("utun") {
                return fd
            }
        }
        return nil
    }
}

enum TunnelError: Error, LocalizedError {
    case missingConfig
    case noTunnelFd
    case xrayFailed(String)

    var errorDescription: String? {
        switch self {
        case .missingConfig: return "Missing Xray configuration"
        case .noTunnelFd: return "Could not locate the tunnel file descriptor"
        case .xrayFailed(let m): return "Xray-core failed to start: \(m)"
        }
    }
}
