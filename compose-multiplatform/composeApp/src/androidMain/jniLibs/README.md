# jniLibs

Prebuilt native `.so` libraries, one folder per ABI:

    arm64-v8a/libhev-socks5-tunnel.so
    armeabi-v7a/libhev-socks5-tunnel.so
    x86_64/libhev-socks5-tunnel.so

`libhev-socks5-tunnel.so` is [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
(tun2socks), built with `-DPKGNAME=com/v2ray/compose/vpn` so its JNI symbols bind
to `com.v2ray.compose.vpn.TProxyService`. Build it with
`scripts/build-native-android.sh`.

AGP picks up this directory automatically. When empty, `TProxyService.available`
is false at runtime and the app reports the tunnel as unavailable.
