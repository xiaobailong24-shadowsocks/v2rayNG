# v2rayNG · Compose Multiplatform

A cross-platform proxy client that shares **one Compose UI** and **one config
engine** across **Android, iOS and desktop (JVM)**, built with
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) +
[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/).

It is a multiplatform re-imagining of [v2rayNG](https://github.com/2dust/v2rayNG):
the parsing, config-building and presentation logic that v2rayNG keeps in Android
code lives here in shared `commonMain`, and the transport uses the same native
pieces (Xray-core via `libXray`, plus a tun2socks bridge) behind a thin
per-platform tunnel layer.

Built on the current toolchain: **Kotlin 2.4.0**, **Compose Multiplatform 1.11.1**,
kotlinx-coroutines/serialization 1.11.0, AGP 8.7.3, Android SDK 35. (Compose MP
1.11 dropped Apple x86_64, so the iOS slices are `iosArm64` + `iosSimulatorArm64`.)

```
┌─────────────────────────────────────────────────────────────┐
│                     commonMain (shared)                      │
│  models · URI parsers (vmess/vless/trojan/ss/socks/hy2/tuic) │
│  subscription decode · XrayConfigBuilder · repository        │
│  AppViewModel (presentation) · Compose UI (screens)          │
└───────────────┬───────────────┬───────────────┬─────────────┘
       androidMain           iosMain          desktopMain
   VpnService + libXray   NEPacketTunnel     preview / dev run
```

## Modules

| Module        | What it is | Targets |
|---------------|------------|---------|
| `core`        | Pure-Kotlin engine: data models, share-link parsers/serializers, subscription handling, `XrayConfigBuilder`, repository, `AppViewModel`, platform `expect`s (storage, http, clock). **No Compose.** | jvm · android · ios |
| `composeApp`  | Compose Multiplatform UI + platform entry points and tunnel controllers. | desktop(jvm) · android · ios |
| `iosApp`      | Xcode project (SwiftUI shell + `NEPacketTunnelProvider` extension) that hosts the shared framework. | iOS |

## Supported protocols

VMess · VLESS (+ Reality/XTLS flow) · Trojan · Shadowsocks (SIP002 & legacy) ·
SOCKS · Hysteria2 · TUIC — parsing, editing, sharing and Xray-outbound
generation. Transports: TCP · WS · gRPC · HTTP/2 · HTTPUpgrade · KCP · QUIC,
with TLS / Reality stream security.

## Building

Requirements: JDK 17+. The repo ships a Gradle wrapper (`./gradlew`).

The Android and iOS targets are **auto-detected** so the project still builds on a
plain JVM host:

* Android target activates when an Android SDK is found (`ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`).
* iOS targets activate on macOS.
* Force either with `-PenableAndroid=true|false` / `-PenableIos=true|false`.

```bash
# Shared engine — unit tests (runs anywhere, no SDK needed)
./gradlew :core:jvmTest

# Desktop app (developer preview of the shared UI)
./gradlew :composeApp:run

# Render the UI to a PNG headlessly (visual check, no display server)
./gradlew :composeApp:screenshot -Dscreenshot.out=preview.png

# Android APK (needs the Android SDK)
./gradlew :composeApp:assembleDebug

# iOS (needs macOS + Xcode + xcodegen)
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

## Native transport integration

The tunnel is fully wired on both platforms — Xray-core (from the Xray JSON that
`XrayConfigBuilder` produces) exposes a local SOCKS inbound, and hev-socks5-tunnel
(tun2socks) bridges the OS tunnel interface to it:

* **Android** — consumes the **same prebuilt binaries as v2rayNG**: `libv2ray.aar`
  (Xray-core, gomobile build of AndroidLibXrayLite) + `libhev-socks5-tunnel.so`
  (tun2socks). No Go/CMake build lives in this tree. `V2RayVpnService` drives
  `XrayCore` (→ libv2ray) and `TProxyService` (→ hev). Build the artifacts with
  `scripts/build-native-android.sh`.
* **iOS** — `PacketTunnelProvider` + `XrayCore` (gomobile `Xraybridge.xcframework`)
  + `Tun2socks` (hev-socks5-tunnel via a bridging header).

The native layer is **opt-in** (build with `-PwithNative=true` / the iOS build
scripts) and degrades gracefully when absent. Full build instructions:
[NATIVE.md](NATIVE.md).

## Verification status

**All four CI workflows are green** — every platform builds end-to-end on
GitHub Actions, and Android is additionally verified **running on a device**:

* ✅ **verify** — `:core:jvmTest` (36 tests on Kotlin 2.4.0: link parsing &
  round-trips for every protocol, subscription decoding, Xray config generation,
  repository behaviour, full `AppViewModel` interaction logic), compiles the
  whole shared Compose UI (`compileKotlinDesktop`), and **renders the real UI
  headlessly** (`:composeApp:screenshot`, `ImageComposeScene`) — the PNG is
  uploaded as the `ui-screenshot` artifact.
* ✅ **android** — debug APK with the native core (prebuilt `libv2ray.aar`
  Xray-core + `libhev-socks5-tunnel.so` built from the submodule), uploaded as
  the `composeApp-debug-apk` artifact.
* ✅ **android (emulator)** — **on-device instrumented tests** on an x86_64
  emulator: the real `MainActivity` launches and composes the whole shared UI on
  a live Android runtime, and an end-to-end flow imports a share link → parses it
  (`ConfigParser`) → builds valid Xray JSON (`XrayConfigBuilder`) → toggles the
  connection through `AppViewModel` → persists it across a real SharedPreferences
  reopen. Only the tunnel transport is stubbed (a live Xray tunnel needs a real
  server); everything up to handing Xray-core its config is exercised for real.
* ✅ **ios** — Xray gomobile xcframework + hev static lib (Kotlin/Native
  cinterop) + shared `ComposeApp.framework`, then `xcodebuild` builds the app
  **and the `PacketTunnel` network extension** for the iOS Simulator (unsigned).

(The development sandbox itself blocks Google Maven — where every Compose
artifact ships from since CMP 1.8 unified with AndroidX — and has no Android
SDK/Xcode, so in-sandbox checks cover `:core:jvmTest` and Gradle configuration;
everything above is verified by CI.)

### CI

GitHub Actions workflows (`.github/workflows/compose-mp-*.yml`) run the full
matrix on GitHub infra, where Google Maven is reachable:

| Workflow | Runner | Does |
|----------|--------|------|
| `compose-mp-verify` | ubuntu | `:core:jvmTest` + compiles the shared Compose UI (`compileKotlinDesktop`) + headless UI screenshot artifact. |
| `compose-mp-android` | ubuntu | downloads the release `libv2ray.aar`, builds `libhev-socks5-tunnel.so` from the submodule, then `assembleDebug -PwithNative=true`; uploads the APK. |
| `compose-mp-android-emulator` | ubuntu + KVM | boots an x86_64 emulator and runs `connectedDebugAndroidTest` — on-device launch + end-to-end import/parse/config/persist flow. |
| `compose-mp-ios` | macos-15 | builds the Xray xcframework + hev static lib + `ComposeApp.framework`, then `xcodebuild` (app + PacketTunnel extension) for the iOS Simulator (unsigned). macos-15+ required — CMP 1.11 links against iOS 18.4+ SDK symbols. |

## License

Inherits the v2rayNG project license (see the repository root `LICENSE`).
