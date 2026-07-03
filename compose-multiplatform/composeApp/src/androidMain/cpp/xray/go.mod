module libxray

go 1.24

// Pin to a recent Xray-core release. Refresh with:
//   go get github.com/xtls/xray-core@latest && go mod tidy
require github.com/xtls/xray-core v1.250608.0
