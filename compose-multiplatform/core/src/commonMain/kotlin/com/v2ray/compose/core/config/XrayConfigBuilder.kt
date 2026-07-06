package com.v2ray.compose.core.config

import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.model.ProfileItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the Xray-core outbound + full config JSON for a [ProfileItem]. The
 * resulting document is what gets handed to the native Xray-core library
 * (`libXray` on Android, the packaged core on iOS) to establish the tunnel.
 */
object XrayConfigBuilder {

    private val json = Json { prettyPrint = true; encodeDefaults = false }

    /** Local SOCKS/HTTP inbound ports used by the platform tunnels. */
    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    fun buildConfigJson(item: ProfileItem): String = json.encodeToString(JsonObject.serializer(), buildConfig(item))

    fun buildConfig(item: ProfileItem): JsonObject = buildJsonObject {
        putJsonObject("log") { put("loglevel", "warning") }
        putJsonArray("inbounds") {
            addJsonObject {
                put("tag", "socks")
                put("port", SOCKS_PORT)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("auth", "noauth")
                    put("udp", true)
                }
                putJsonObject("sniffing") {
                    put("enabled", true)
                    putJsonArray("destOverride") { add("http"); add("tls") }
                }
            }
            addJsonObject {
                put("tag", "http")
                put("port", HTTP_PORT)
                put("listen", "127.0.0.1")
                put("protocol", "http")
            }
        }
        putJsonArray("outbounds") {
            add(buildOutbound(item))
            addJsonObject {
                put("tag", "direct")
                put("protocol", "freedom")
            }
            addJsonObject {
                put("tag", "block")
                put("protocol", "blackhole")
            }
        }
    }

    fun buildOutbound(item: ProfileItem): JsonObject = buildJsonObject {
        put("tag", "proxy")
        put("protocol", protocolName(item.configType))
        putJsonObject("settings") { putProtocolSettings(item) }
        val stream = buildStreamSettings(item)
        if (stream != null) put("streamSettings", stream)
    }

    private fun protocolName(type: EConfigType): String = when (type) {
        EConfigType.VMESS -> "vmess"
        EConfigType.VLESS -> "vless"
        EConfigType.TROJAN -> "trojan"
        EConfigType.SHADOWSOCKS -> "shadowsocks"
        EConfigType.SOCKS -> "socks"
        EConfigType.HTTP -> "http"
        else -> type.scheme
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putProtocolSettings(item: ProfileItem) {
        when (item.configType) {
            EConfigType.VMESS, EConfigType.VLESS -> putJsonArray("vnext") {
                addJsonObject {
                    put("address", item.server)
                    put("port", item.serverPort.toIntOrNull() ?: 443)
                    putJsonArray("users") {
                        addJsonObject {
                            put("id", item.password ?: "")
                            if (item.configType == EConfigType.VMESS) {
                                put("alterId", item.alterId ?: 0)
                                put("security", item.security ?: "auto")
                            } else {
                                put("encryption", "none")
                                if (!item.flow.isNullOrEmpty()) put("flow", item.flow!!)
                            }
                        }
                    }
                }
            }
            EConfigType.TROJAN -> putJsonArray("servers") {
                addJsonObject {
                    put("address", item.server)
                    put("port", item.serverPort.toIntOrNull() ?: 443)
                    put("password", item.password ?: "")
                    if (!item.flow.isNullOrEmpty()) put("flow", item.flow!!)
                }
            }
            EConfigType.SHADOWSOCKS -> putJsonArray("servers") {
                addJsonObject {
                    put("address", item.server)
                    put("port", item.serverPort.toIntOrNull() ?: 443)
                    put("method", item.method ?: "aes-256-gcm")
                    put("password", item.password ?: "")
                }
            }
            EConfigType.SOCKS, EConfigType.HTTP -> putJsonArray("servers") {
                addJsonObject {
                    put("address", item.server)
                    put("port", item.serverPort.toIntOrNull() ?: 1080)
                    if (!item.method.isNullOrEmpty()) {
                        putJsonArray("users") {
                            addJsonObject {
                                put("user", item.method!!)
                                put("pass", item.password ?: "")
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun buildStreamSettings(item: ProfileItem): JsonObject? {
        val network = item.network ?: "tcp"
        val security = item.streamSecurity ?: ""
        if (network == "tcp" && security.isEmpty() && item.configType == EConfigType.SHADOWSOCKS) return null
        return buildJsonObject {
            put("network", network)
            if (security.isNotEmpty()) put("security", security)
            when (security) {
                "tls" -> putJsonObject("tlsSettings") {
                    if (!item.sni.isNullOrEmpty()) put("serverName", item.sni!!)
                    if (item.allowInsecure == true) put("allowInsecure", true)
                    if (!item.fingerprint.isNullOrEmpty()) put("fingerprint", item.fingerprint!!)
                    if (!item.alpn.isNullOrEmpty()) putJsonArray("alpn") {
                        item.alpn!!.split(",").forEach { add(it.trim()) }
                    }
                }
                "reality" -> putJsonObject("realitySettings") {
                    if (!item.sni.isNullOrEmpty()) put("serverName", item.sni!!)
                    if (!item.publicKey.isNullOrEmpty()) put("publicKey", item.publicKey!!)
                    if (!item.shortId.isNullOrEmpty()) put("shortId", item.shortId!!)
                    if (!item.spiderX.isNullOrEmpty()) put("spiderX", item.spiderX!!)
                    if (!item.fingerprint.isNullOrEmpty()) put("fingerprint", item.fingerprint!!)
                }
            }
            when (network) {
                "ws" -> putJsonObject("wsSettings") {
                    if (!item.path.isNullOrEmpty()) put("path", item.path!!)
                    if (!item.host.isNullOrEmpty()) putJsonObject("headers") { put("Host", item.host!!) }
                }
                "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                    if (!item.path.isNullOrEmpty()) put("path", item.path!!)
                    // httpupgrade takes the camouflage host as a dedicated top-level
                    // field; Xray hard-rejects a "Host" key inside `headers` here
                    // (unlike ws, which back-compat-promotes it) and fails the whole
                    // config load.
                    if (!item.host.isNullOrEmpty()) put("host", item.host!!)
                }
                "grpc" -> putJsonObject("grpcSettings") {
                    if (!item.path.isNullOrEmpty()) put("serviceName", item.path!!)
                    put("multiMode", item.mode == "multi")
                }
                "h2" -> putJsonObject("httpSettings") {
                    if (!item.host.isNullOrEmpty()) putJsonArray("host") { add(item.host!!) }
                    if (!item.path.isNullOrEmpty()) put("path", item.path!!)
                }
                "tcp" -> if (item.headerType == "http") putJsonObject("tcpSettings") {
                    putJsonObject("header") {
                        put("type", "http")
                        // Xray parses request.headers as an object (header name ->
                        // string list), not an array; the earlier `headers: []` failed
                        // the whole config parse and dropped the camouflage Host.
                        putJsonObject("request") {
                            put("version", "1.1")
                            put("method", "GET")
                            putJsonArray("path") { add(item.path?.takeIf { it.isNotEmpty() } ?: "/") }
                            putJsonObject("headers") {
                                if (!item.host.isNullOrEmpty()) putJsonArray("Host") {
                                    item.host!!.split(",").forEach { add(it.trim()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
