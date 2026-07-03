# cinterop libs

Place the iOS build of hev-socks5-tunnel here:

    libhev-socks5-tunnel.a

It is linked into the ComposeApp Kotlin/Native framework via `../hev.def` and
consumed by `IosTun2socks` (see `composeApp/src/iosMain/.../vpn/IosTun2socks.kt`).
Build it for the iOS device + simulator slices from
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (its CMake/Xcode
build), then drop the fat/xcframework-extracted static archive here. See NATIVE.md.
