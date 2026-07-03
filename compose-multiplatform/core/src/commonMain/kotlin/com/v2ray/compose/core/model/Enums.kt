package com.v2ray.compose.core.model

/**
 * Proxy protocols supported by the client. Mirrors the set handled by v2rayNG /
 * Xray-core so that configurations exported from either side interoperate.
 */
enum class EConfigType(val scheme: String) {
    VMESS("vmess"),
    SHADOWSOCKS("ss"),
    SOCKS("socks"),
    HTTP("http"),
    VLESS("vless"),
    TROJAN("trojan"),
    HYSTERIA2("hysteria2"),
    TUIC("tuic"),
    WIREGUARD("wireguard"),
    CUSTOM("custom");

    companion object {
        fun fromScheme(scheme: String): EConfigType? {
            val s = scheme.lowercase()
            return entries.firstOrNull { it.scheme == s }
                ?: when (s) {
                    "hy2" -> HYSTERIA2
                    "hysteria" -> HYSTERIA2
                    else -> null
                }
        }
    }
}

/** Transport / stream network type. */
enum class NetworkType(val value: String) {
    TCP("tcp"),
    KCP("kcp"),
    WS("ws"),
    HTTP_UPGRADE("httpupgrade"),
    XHTTP("xhttp"),
    H2("h2"),
    GRPC("grpc"),
    QUIC("quic");

    companion object {
        fun of(value: String?): NetworkType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: when (value?.lowercase()) {
                    "http" -> H2
                    "tcp", null, "" -> TCP
                    else -> TCP
                }
    }
}

/** Runtime connection state exposed to the UI. */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}
