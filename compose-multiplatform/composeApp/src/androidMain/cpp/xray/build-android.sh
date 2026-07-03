#!/usr/bin/env bash
# Build Xray-core as a per-ABI C archive (libxray_<abi>.a + header) for CMake.
#
# Requirements: Go 1.22+, Android NDK (r26+). Set ANDROID_NDK_HOME.
#
# Usage: ANDROID_NDK_HOME=/path/to/ndk ./build-android.sh
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to your Android NDK}"
MIN_SDK="${MIN_SDK:-24}"
HOST_TAG="linux-x86_64"   # use darwin-x86_64 on macOS
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"

go mod tidy

build() { # <goarch> <clang-triple> <out-suffix>
  local goarch="$1" triple="$2" suffix="$3"
  echo ">> building xray for $suffix"
  CGO_ENABLED=1 GOOS=android GOARCH="$goarch" \
    CC="$TOOLCHAIN/${triple}${MIN_SDK}-clang" \
    go build -buildmode=c-archive -trimpath -ldflags="-s -w" \
      -o "out/libxray_${suffix}.a" .
}

mkdir -p out
build arm64 aarch64-linux-android   arm64-v8a
build arm   armv7a-linux-androideabi armeabi-v7a
build amd64 x86_64-linux-android     x86_64

# The generated header is identical across ABIs; keep one copy for CMake.
cp out/libxray_arm64-v8a.h out/libxray.h
echo "Done. Archives in $(pwd)/out"
