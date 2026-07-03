// Package xraybridge is a gomobile-bindable wrapper around Xray-core for Apple
// platforms. `gomobile bind` turns it into Xraybridge.xcframework, exposing the
// exported functions to Swift as XraybridgeRunXray / XraybridgeStopXray /
// XraybridgeVersion (see build-xray-apple.sh).
package xraybridge

import (
	"sync"

	"github.com/xtls/xray-core/core"

	_ "github.com/xtls/xray-core/main/distro/all"
)

var (
	mu       sync.Mutex
	instance *core.Instance
)

// RunXray starts Xray-core from a JSON config. Returns "" on success or an error string.
func RunXray(datDir string, config string) string {
	mu.Lock()
	defer mu.Unlock()
	if instance != nil {
		return ""
	}
	inst, err := core.StartInstance("json", []byte(config))
	if err != nil {
		return err.Error()
	}
	instance = inst
	return ""
}

// StopXray stops the running instance. Returns "" on success.
func StopXray() string {
	mu.Lock()
	defer mu.Unlock()
	if instance != nil {
		err := instance.Close()
		instance = nil
		if err != nil {
			return err.Error()
		}
	}
	return ""
}

// Version returns the Xray-core version.
func Version() string {
	return core.Version()
}
