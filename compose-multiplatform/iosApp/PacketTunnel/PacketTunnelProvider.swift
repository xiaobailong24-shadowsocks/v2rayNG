import NetworkExtension

/// Packet Tunnel provider — the iOS counterpart of Android's `V2RayVpnService`.
///
/// It receives the Xray-core JSON (built by the shared `XrayConfigBuilder`) through
/// `providerConfiguration`, starts Xray-core (via `libXray`, packaged as an
/// `xcframework`) which exposes a local SOCKS inbound, and bridges the tunnel's
/// packet flow to that inbound with a tun2socks engine. The lifecycle below is
/// complete; the two `INTEGRATION` points are where the native core is invoked.
class PacketTunnelProvider: NEPacketTunnelProvider {

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        guard
            let proto = self.protocolConfiguration as? NETunnelProviderProtocol,
            let providerConfig = proto.providerConfiguration,
            let xrayConfig = providerConfig["xrayConfig"] as? String
        else {
            completionHandler(NSError(domain: "com.v2ray.compose", code: 1,
                                      userInfo: [NSLocalizedDescriptionKey: "Missing xrayConfig"]))
            return
        }

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.ipv4Settings = {
            let s = NEIPv4Settings(addresses: ["10.10.10.1"], subnetMasks: ["255.255.255.0"])
            s.includedRoutes = [NEIPv4Route.default()]
            return s
        }()
        settings.dnsSettings = NEDNSSettings(servers: ["1.1.1.1", "8.8.8.8"])
        settings.mtu = 1500

        setTunnelNetworkSettings(settings) { error in
            if let error = error {
                completionHandler(error)
                return
            }

            // INTEGRATION 1: start Xray-core with `xrayConfig` (libXray) → SOCKS 127.0.0.1:10808
            // INTEGRATION 2: run tun2socks reading from self.packetFlow, forwarding to that SOCKS

            completionHandler(nil)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        // INTEGRATION: stop tun2socks and Xray-core
        completionHandler()
    }
}
