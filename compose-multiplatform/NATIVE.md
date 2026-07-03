# Native transport (Xray-core + tun2socks)

The shared UI/logic is pure Kotlin; the encrypted tunnel is carried by native
code — the **same prebuilt binaries v2rayNG uses**, not a from-source Go/NDK
build in this tree. The native layer is **opt-in**: the app compiles and runs
(UI, parsing, config generation) without it; connecting just needs the libraries
present.

```
 ProfileItem ──XrayConfigBuilder──► Xray JSON
                                       │
        Android                        │                 iOS
 ┌────────────────────────┐           ▼        ┌────────────────────────────┐
 │ V2RayVpnService (TUN)   │  ┌──────────────┐ │ PacketTunnelProvider (utun) │
 │  ├► XrayCore→libv2ray.aar│─►│ SOCKS :10808 ├─►│  ├► XrayCore (Xraybridge)  │
 │  └► TProxyService (hev)  │◄─┤ (Xray-core)  │ │  └► Tun2socks (hev)         │
 └────────────────────────┘  └──────────────┘ └────────────────────────────┘
```

Both platforms run **Xray-core** (local SOCKS inbound on `127.0.0.1:10808`) and
**hev-socks5-tunnel** (tun2socks) to bridge the OS tunnel interface to it.

## Android — prebuilt binaries (mirrors v2rayNG exactly)

No Go/CMake build lives in this repo. The app consumes two prebuilt artifacts,
identical in origin to v2rayNG's:

| Artifact | Where it goes | What it is |
|----------|---------------|------------|
| `libv2ray.aar` | `composeApp/libs/` | Xray-core, gomobile build of [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite). Exposes `libv2ray.CoreController` / `CoreCallbackHandler`. |
| `libhev-socks5-tunnel.so` | `composeApp/src/androidMain/jniLibs/<abi>/` | [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel), built with `-DPKGNAME=com/v2ray/compose/vpn` → binds to `TProxyService`. |

Kotlin side (`composeApp/src/androidMain/.../vpn/`):
* `XrayCore` — interface; the real impl `nativeimpl/LibXrayCore` (in the
  `androidNative` source set, imports `libv2ray`) is loaded reflectively and only
  compiled with `-PwithNative=true`.
* `TProxyService` — JNI wrapper (`TProxyStartService`/`TProxyStopService`/`TProxyGetStats`).
* `V2RayVpnService` — establishes the TUN (excluding our own package for loop
  avoidance), starts the core, then hev tun2socks; polls stats; disconnect action.

Build:

```bash
# one-time: fetch + build libv2ray.aar and libhev-socks5-tunnel.so
NDK_HOME=/path/to/ndk ./scripts/build-native-android.sh
# then build with the native core enabled
./gradlew :composeApp:assembleDebug -PwithNative=true
```

Without `-PwithNative=true` the `androidNative` source set is excluded and
`XrayCore.load()` returns null → the UI builds and runs, connecting reports the
core as unavailable.

## iOS

Sources in `iosApp/PacketTunnel/`:

| File | Role |
|------|------|
| `PacketTunnelProvider.swift` | `NEPacketTunnelProvider`: tunnel settings, finds the `utun` fd, starts Xray + tun2socks. |
| `XrayCore.swift` | Wraps `Xraybridge.xcframework` (gomobile). |
| `Tun2socks.swift` + `PacketTunnel-Bridging-Header.h` | Calls `hev-socks5-tunnel` C API. |
| `xray-go/` | gomobile-bindable Xray-core package + `build-xray-apple.sh`. |

```bash
cd iosApp/PacketTunnel/xray-go && ./build-xray-apple.sh   # → ../Xraybridge.xcframework
# build libhev-socks5-tunnel.a for iOS, drop into iosApp/PacketTunnel/libs/
cd ../../ && xcodegen generate && open iosApp.xcodeproj
```

> **iOS glue could be slimmed further** by binding hev-socks5-tunnel through
> Kotlin/Native **cinterop** (a `.def` in the iOS target) and driving tun2socks
> from `iosMain` Kotlin instead of Swift — cinterop is a clean fit for the pure-C
> library. The Go core stays on gomobile (safer than bridging the Go runtime
> through cinterop).

## Notes

* The default generated Xray config has no `stats`/`policy`/`api` block, so
  Android traffic counters come from hev (`TProxyGetStats`); the iOS side can add
  the same.
* Xray-core versions track the AndroidLibXrayLite / gomobile package pins; refresh
  upstream and rebuild.
