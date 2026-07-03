cmake_minimum_required(VERSION 3.22.1)
project(v2raybridge C)

# libv2ray.so = JNI shim (bridge.c) + Xray-core (Go c-archive) + hev-socks5-tunnel.

set(XRAY_OUT "${CMAKE_CURRENT_SOURCE_DIR}/xray/out")

# ---- Xray-core (prebuilt per-ABI archive from xray/build-android.sh) ----
add_library(xray STATIC IMPORTED)
set_target_properties(xray PROPERTIES
    IMPORTED_LOCATION "${XRAY_OUT}/libxray_${ANDROID_ABI}.a")

# ---- hev-socks5-tunnel (vendored source; git submodule at cpp/hev-socks5-tunnel) ----
# Provides hev_socks5_tunnel_main_from_str / hev_socks5_tunnel_quit.
add_subdirectory(hev-socks5-tunnel EXCLUDE_FROM_ALL)

add_library(v2ray SHARED bridge.c)

target_include_directories(v2ray PRIVATE
    "${XRAY_OUT}"
    "${CMAKE_CURRENT_SOURCE_DIR}/hev-socks5-tunnel/src")

find_library(log-lib log)

target_link_libraries(v2ray
    xray
    hev-socks5-tunnel
    ${log-lib})
