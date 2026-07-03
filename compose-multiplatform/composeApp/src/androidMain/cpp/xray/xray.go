// Package main builds Xray-core into a C archive (libxray.a + libxray.h) that
// the JNI shim (bridge.c) links into libv2ray.so.
//
// Build per-ABI with the NDK toolchain, e.g.:
//   CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
//   CC=$NDK/.../aarch64-linux-android24-clang \
//   go build -buildmode=c-archive -o libxray_arm64.a xray.go
// (see build-android.sh)
package main

/*
// Implemented in bridge.c; protects a socket fd from the VPN routing loop by
// calling VpnService.protect() up in Kotlin. Returns 1 on success.
extern int protect_fd(int fd);
*/
import "C"

import (
	"sync"
	"syscall"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/transport/internet"

	// Pull in all protocols / transports (vmess, vless, trojan, ss, reality, ...).
	_ "github.com/xtls/xray-core/main/distro/all"
)

var (
	mu       sync.Mutex
	instance *core.Instance
)

func init() {
	// Protect every outbound socket Xray opens so its traffic to the proxy
	// server exits the device directly instead of re-entering the TUN.
	internet.RegisterDialerController(func(network, address string, conn syscall.RawConn) error {
		return conn.Control(func(fd uintptr) {
			C.protect_fd(C.int(fd))
		})
	})
}

//export StartXray
func StartXray(cfg *C.char) *C.char {
	mu.Lock()
	defer mu.Unlock()
	if instance != nil {
		return C.CString("")
	}
	inst, err := core.StartInstance("json", []byte(C.GoString(cfg)))
	if err != nil {
		return C.CString(err.Error())
	}
	instance = inst
	return C.CString("")
}

//export StopXray
func StopXray() {
	mu.Lock()
	defer mu.Unlock()
	if instance != nil {
		_ = instance.Close()
		instance = nil
	}
}

//export XrayVersion
func XrayVersion() *C.char {
	return C.CString(core.Version())
}

func main() {}
