#!/usr/bin/env bash
# Prepare the Android native artifacts this app consumes:
#   • libv2ray.aar           — Xray-core, DOWNLOADED prebuilt from the
#                              AndroidLibXrayLite release matching the submodule
#                              tag (same approach as v2rayNG — avoids the fragile
#                              gomobile-from-source build).
#   • libhev-socks5-tunnel.so — tun2socks, built from the submodule with this
#                              app's JNI package (-DPKGNAME=com/v2ray/compose/vpn).
#
# Requirements: Android NDK (set NDK_HOME), curl, and the two submodules checked
# out (the parent v2rayNG repo declares them). Go is NOT required.
#
# Usage (from compose-multiplatform/):
#   NDK_HOME=/path/to/ndk ./scripts/build-native-android.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
: "${NDK_HOME:?set NDK_HOME to your Android NDK}"

XRAY_SRC="${XRAY_SRC:-$ROOT/../AndroidLibXrayLite}"
HEV_SRC="${HEV_SRC:-$ROOT/../hev-socks5-tunnel}"

# ---- 1) libv2ray.aar (prebuilt download) ------------------------------------
mkdir -p composeApp/libs
if [ -e "$XRAY_SRC/.git" ]; then
  TAG="$(git -C "$XRAY_SRC" describe --tags --abbrev=0)"
else
  TAG="${LIBV2RAY_TAG:?AndroidLibXrayLite submodule missing; set LIBV2RAY_TAG}"
fi
echo ">> [1/2] downloading libv2ray.aar @ $TAG"
curl -fL --retry 3 -o composeApp/libs/libv2ray.aar \
  "https://github.com/2dust/AndroidLibXrayLite/releases/download/${TAG}/libv2ray.aar"

# ---- 2) libhev-socks5-tunnel.so (ndk-build from submodule) ------------------
[ -d "$HEV_SRC" ] || { echo "hev-socks5-tunnel submodule missing at $HEV_SRC"; exit 1; }
echo ">> [2/2] building libhev-socks5-tunnel.so (PKGNAME=com/v2ray/compose/vpn)"
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
