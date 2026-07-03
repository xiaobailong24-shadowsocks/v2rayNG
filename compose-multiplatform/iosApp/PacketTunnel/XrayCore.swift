import Foundation
#if canImport(Xraybridge)
import Xraybridge
#endif

/// Thin wrapper over `Xraybridge.xcframework` (Xray-core compiled for Apple with
/// gomobile — built by `xray-go/build-xray-apple.sh`). Kept isolated so the
/// provider does not depend on the exact framework symbols.
///
/// gomobile exposes the package's exported funcs to Swift as:
///   XraybridgeRunXray(_ datDir: String, _ config: String) -> String   // "" on success
///   XraybridgeStopXray() -> String
///   XraybridgeVersion() -> String
final class XrayCore {
    static let shared = XrayCore()
    private init() {}

    /// Starts Xray-core. Returns nil on success, or an error message.
    func start(configJSON: String) -> String? {
        #if canImport(Xraybridge)
        let datDir = FileManager.default.temporaryDirectory.path
        let result = XraybridgeRunXray(datDir, configJSON)
        return result.isEmpty ? nil : result
        #else
        return "Xraybridge.xcframework not linked (run xray-go/build-xray-apple.sh, see NATIVE.md)"
        #endif
    }

    func stop() {
        #if canImport(Xraybridge)
        _ = XraybridgeStopXray()
        #endif
    }

    var version: String {
        #if canImport(Xraybridge)
        return XraybridgeVersion()
        #else
        return "unavailable"
        #endif
    }
}
