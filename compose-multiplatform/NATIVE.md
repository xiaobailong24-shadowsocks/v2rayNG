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

The **C library (hev-socks5-tunnel) is bound with Kotlin/Native cinterop** and
driven from shared `iosMain` Kotlin — no Swift bridging header. The **Go core
(Xray) stays on gomobile** (safer than bridging the Go runtime through cinterop).

| File | Role |
|------|------|
| `iosApp/PacketTunnel/PacketTunnelProvider.swift` | `NEPacketTunnelProvider`: tunnel settings, finds the `utun` fd, starts Xray + tun2socks. Calls the shared framework. |
| `iosApp/PacketTunnel/XrayCore.swift` | Wraps `Xraybridge.xcframework` (gomobile). |
| `composeApp/.../iosMain/.../vpn/IosTun2socks.kt` | tun2socks driver — calls hev via cinterop, runs it on a worker. Exported to Swift by the ComposeApp framework. |
| `composeApp/src/nativeInterop/cinterop/hev.def` (+ header) | cinterop binding for `hev-socks5-tunnel`. |
| `iosApp/PacketTunnel/xray-go/` | gomobile-bindable Xray-core package + `build-xray-apple.sh`. |

The CI workflow `.github/workflows/compose-mp-ios.yml` is the reference build —
these are the same steps it runs (simulator slice shown; swap the SDK/`-arch`
and Gradle task for a device build):

```bash
# 1) Xray-core → Xraybridge.xcframework (gomobile; Go 1.23)
cd iosApp/PacketTunnel/xray-go && ./build-xray-apple.sh && cd ../../..

# 2) hev-socks5-tunnel static lib for the cinterop. hev's Makefile `static`
#    target emits bin/libhev-socks5-tunnel.a (its own objects only), so merge it
#    with the three third-part archives into one fat archive.
cd ../hev-socks5-tunnel && git submodule update --init --recursive
SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
make static CC="$(xcrun --sdk iphonesimulator -f clang)" \
  CFLAGS="-arch arm64 -isysroot $SDK -mios-simulator-version-min=15.0"
DEST=../compose-multiplatform/composeApp/src/nativeInterop/cinterop/libs
mkdir -p "$DEST"
libtool -static -o "$DEST/libhev-socks5-tunnel.a" \
  $(find . -name 'libhev-socks5-tunnel.a' -o -name 'liblwip.a' \
           -o -name 'libhev-task-system.a' -o -name 'libyaml.a')
cd ../compose-multiplatform

# 3) shared ComposeApp framework, staged where the Xcode project references it
#    (both the app and the PacketTunnel extension import it)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -PenableIos=true
mkdir -p composeApp/build/xcode-frameworks
cp -R composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework \
      composeApp/build/xcode-frameworks/

# 4) generate & build/open the Xcode project (Xcode 16+; the KMP framework has
#    no x86_64 simulator slice, so pin ARCHS=arm64 on the simulator)
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

## Notes

* The default generated Xray config has no `stats`/`policy`/`api` block, so
  Android traffic counters come from hev (`TProxyGetStats`); the iOS side can add
  the same.
* Xray-core versions track the AndroidLibXrayLite / gomobile package pins; refresh
  upstream and rebuild.
* The `PacketTunnel` extension links the full `ComposeApp` framework (it only
  needs `IosTun2socks`). Network-extension processes have a tight dirty-memory
  budget (~50 MB); if that ever becomes a problem on device, split the tunnel
  glue into a small dedicated Kotlin/Native framework and link only that from
  the extension.
