# iosApp

The iOS host for the shared Compose Multiplatform UI.

The `.xcodeproj` is **not** committed — it is generated from `project.yml` with
[XcodeGen](https://github.com/yonaskolb/XcodeGen) so the project definition stays
reviewable in git.

## Generate & open

```bash
brew install xcodegen        # once
xcodegen generate            # produces iosApp.xcodeproj
open iosApp.xcodeproj
```

Set your `DEVELOPMENT_TEAM` in `project.yml` (or in Xcode signing) and build the
`iosApp` scheme onto a device. A pre-build script runs
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` to build the Kotlin
framework.

## Targets

* **iosApp** — SwiftUI shell (`iOSApp.swift` → `ContentView.swift`) that embeds the
  shared UI via `MainViewControllerKt.MainViewController()`.
* **PacketTunnel** — `NEPacketTunnelProvider` app extension that carries the
  Xray-core tunnel. Requires the Network Extensions capability and an App Group
  (`group.com.v2ray.compose`), declared in the `.entitlements` files.
