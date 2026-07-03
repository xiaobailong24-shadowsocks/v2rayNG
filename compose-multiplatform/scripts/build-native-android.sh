#!/usr/bin/env bash
# Build the Android native artifacts this app consumes:
#   • libv2ray.aar          — Xray-core (gomobile build of AndroidLibXrayLite)
#   • libhev-socks5-tunnel.so — tun2socks, built with this app's JNI package
#
# These are the SAME upstreams v2rayNG uses. Requirements: Go 1.22+, gomobile,
# Android NDK (set NDK_HOME), and the two submodules checked out.
#
# Usage (from compose-multiplatform/):
#   NDK_HOME=/path/to/ndk ./scripts/build-native-android.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
: "${NDK_HOME:?set NDK_HOME to your Android NDK}"

# The parent v2rayNG repo already declares these as submodules; reuse them, or
# clone standalone if this project lives outside that tree.
XRAY_SRC="${XRAY_SRC:-$ROOT/../AndroidLibXrayLite}"
HEV_SRC="${HEV_SRC:-$ROOT/../hev-socks5-tunnel}"
[ -d "$XRAY_SRC" ] || git clone --recurse-submodules https://github.com/2dust/AndroidLibXrayLite "$XRAY_SRC"
[ -d "$HEV_SRC" ]  || git clone --recurse-submodules https://github.com/heiher/hev-socks5-tunnel "$HEV_SRC"

echo ">> [1/2] Building libv2ray.aar (Xray-core via gomobile)"
(
  cd "$XRAY_SRC"
  go install golang.org/x/mobile/cmd/gomobile@latest
  export PATH="$(go env GOPATH)/bin:$PATH"
  gomobile init
  go mod tidy
  gomobile bind -target=android -androidapi 24 -o "$ROOT/composeApp/libs/libv2ray.aar" .
)

echo ">> [2/2] Building libhev-socks5-tunnel.so (tun2socks, PKGNAME=com/v2ray/compose/vpn)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/jni"
ln -s "$HEV_SRC" "$TMP/jni/hev-socks5-tunnel"
echo 'include $(call all-subdir-makefiles)' > "$TMP/jni/Android.mk"
"$NDK_HOME/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT="$TMP/jni/Android.mk" \
    "APP_ABI=armeabi-v7a arm64-v8a x86_64" \
    APP_PLATFORM=android-24 \
    NDK_LIBS_OUT="$TMP/libs" \
    NDK_OUT="$TMP/obj" \
    "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/compose/vpn" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu"

DEST="$ROOT/composeApp/src/androidMain/jniLibs"
for abi in armeabi-v7a arm64-v8a x86_64; do
  mkdir -p "$DEST/$abi"
  cp "$TMP/libs/$abi/libhev-socks5-tunnel.so" "$DEST/$abi/"
done

echo "Done. Build the app with:  ./gradlew :composeApp:assembleDebug -PwithNative=true"
