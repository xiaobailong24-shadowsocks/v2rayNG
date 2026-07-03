# Native transport (Xray-core + tun2socks)

The shared UI/logic is pure Kotlin, but the actual encrypted tunnel is carried by
native code — the same pieces v2rayNG uses. This document explains how the native
layer is wired and how to build it. It is **opt-in**: the app compiles and runs
(UI, parsing, config generation) without it; connecting just needs the native
libraries present.

```
 ProfileItem ──XrayConfigBuilder──► Xray JSON
                                       │
        Android                        │                 iOS
 ┌───────────────────────┐            │        ┌────────────────────────────┐
 │ V2RayVpnService (TUN)  │           ▼        │ PacketTunnelProvider (utun) │
 │   └► V2RayBridge (JNI) │   ┌──────────────┐ │   ├► XrayCore (Xraybridge)  │
 │        ├► Xray-core    │◄──┤ SOCKS :10808 ├─►│   └► Tun2socks (hev)        │
 │        └► hev tun2socks│   └──────────────┘ │                              │
 └───────────────────────┘                     └────────────────────────────┘
```

Both platforms run **Xray-core** (exposing a local SOCKS inbound on
`127.0.0.1:10808`) and **hev-socks5-tunnel** (tun2socks) to bridge the OS tunnel
interface to that inbound.

## Android

Sources live in `composeApp/src/androidMain/cpp/`:

| File | Role |
|------|------|
| `xray/xray.go` | Xray-core wrapped as a C archive (`StartXray`/`StopXray`/`XrayVersion`), with a dialer controller that calls `protect_fd` so Xray's own sockets skip the TUN. |
| `bridge.c` | JNI shim implementing `V2RayBridge` and the `protect_fd` up-call to `VpnService.protect()`. |
| `CMakeLists.txt` | Links `bridge.c` + the Xray archive + `hev-socks5-tunnel` into `libv2ray.so`. |

Build steps:

```bash
cd composeApp/src/androidMain/cpp
# 1) hev-socks5-tunnel as a git submodule (reused from the parent v2rayNG repo)
git submodule add https://github.com/heiher/hev-socks5-tunnel hev-socks5-tunnel
git -C hev-socks5-tunnel submodule update --init

# 2) Xray-core → per-ABI C archives
ANDROID_NDK_HOME=/path/to/ndk ./xray/build-android.sh

# 3) build the app with the native step enabled
cd -
./gradlew :composeApp:assembleDebug -PwithNative=true
```

Without `-PwithNative=true` the CMake step is skipped and `V2RayBridge.available`
is `false` at runtime (connecting reports a clear error).

## iOS

Sources live in `iosApp/PacketTunnel/`:

| File | Role |
|------|------|
| `PacketTunnelProvider.swift` | `NEPacketTunnelProvider`: sets tunnel settings, finds the `utun` fd, starts Xray + tun2socks. |
| `XrayCore.swift` | Wraps `Xraybridge.xcframework` (gomobile). |
| `Tun2socks.swift` + `PacketTunnel-Bridging-Header.h` | Calls `hev-socks5-tunnel` C API. |
| `xray-go/` | The gomobile-bindable Xray-core package + `build-xray-apple.sh`. |

Build steps (macOS):

```bash
cd iosApp/PacketTunnel/xray-go
./build-xray-apple.sh                      # → ../Xraybridge.xcframework

# Build libhev-socks5-tunnel.a for iOS (device+sim) and drop it in
#   iosApp/PacketTunnel/libs/    (see hev-socks5-tunnel build docs)

cd ../../
xcodegen generate && open iosApp.xcodeproj # build the iosApp scheme
```

## Notes

* The default generated Xray config has no stats/policy block, so traffic
  counters report 0. Add a `stats`/`policy`/`api` block to the config and query
  the Xray stats API to populate them.
* Xray-core versions in the two `go.mod` files are pinned; refresh with
  `go get github.com/xtls/xray-core@latest && go mod tidy`.
