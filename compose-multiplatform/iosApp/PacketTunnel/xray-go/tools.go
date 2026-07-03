//go:build tools

// Keeps golang.org/x/mobile in the module dependency graph so `gomobile bind`
// can find it (it is a build tool, not imported by the library code, so
// `go mod tidy` would otherwise drop it).
package xraybridge

import (
	_ "golang.org/x/mobile/bind"
)
