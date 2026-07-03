#!/usr/bin/env bash
# Build Xray-core into Xraybridge.xcframework for iOS (device + simulator) using
# gomobile. Run on macOS with Xcode + Go installed.
#
# Output: ../Xraybridge.xcframework  (add it to the PacketTunnel target)
set -euo pipefail
cd "$(dirname "$0")"

go install golang.org/x/mobile/cmd/gomobile@latest
export PATH="$(go env GOPATH)/bin:$PATH"
gomobile init

go mod tidy

gomobile bind \
  -target=ios,iossimulator \
  -iosversion=15.0 \
  -o ../Xraybridge.xcframework \
  .

echo "Built ../Xraybridge.xcframework"
