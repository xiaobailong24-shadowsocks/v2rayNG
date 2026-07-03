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

The tunnel scaffolding is complete; two `INTEGRATION` points wire in the native
core, exactly as in v2rayNG:

* **Android** — `composeApp/.../vpn/V2RayVpnService.kt` establishes the `TUN`
  interface. Drop in `AndroidLibXrayLite` (`libXray`) to run Xray-core from the
  JSON produced by `XrayConfigBuilder`, and `hev-socks5-tunnel` to bridge the TUN
  fd to the local SOCKS inbound (`127.0.0.1:10808`). Both `.so`s already exist in
  the parent v2rayNG repository.
* **iOS** — `iosApp/PacketTunnel/PacketTunnelProvider.swift` sets up
  `NEPacketTunnelNetworkSettings`; add Xray-core (as an `xcframework`) + tun2socks
  reading from `packetFlow`.

## Verification status

Because this sandbox has no Android SDK and no access to Google's Maven
repository (which serves AndroidX / the Compose Android runtime), automated
verification here covers the parts that resolve from Maven Central:

* ✅ **`:core:jvmTest` — 36 tests passing**: link parsing & round-trips for every
  protocol, subscription decoding, Xray config generation, repository behaviour,
  and full `AppViewModel` interaction logic (import / connect-toggle /
  subscription CRUD).
* ✅ **`:composeApp:compileKotlinDesktop`** — the entire shared Compose UI
  type-checks and compiles.
* ▶️ **`:composeApp:run` / `:screenshot`** and the Android/iOS builds require a
  normal developer machine (Google Maven reachable, SDK / Xcode installed).

## License

Inherits the v2rayNG project license (see the repository root `LICENSE`).
