#!/usr/bin/env bash
# Build Xray-core into Xraybridge.xcframework for iOS (device + simulator) using
# gomobile. Run on macOS with Xcode + Go installed.
#
# Output: ../Xraybridge.xcframework  (add it to the PacketTunnel target)
set -euo pipefail
cd "$(dirname "$0")"

go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$(go env GOPATH)/bin:$PATH"

# Ensure golang.org/x/mobile is in the module graph (tools.go keeps it through
# tidy) so `gomobile bind` can find it.
go get golang.org/x/mobile/bind
go mod tidy
gomobile init

gomobile bind \
  -target=ios,iossimulator \
  -iosversion=15.0 \
  -o ../Xraybridge.xcframework \
  .

echo "Built ../Xraybridge.xcframework"
