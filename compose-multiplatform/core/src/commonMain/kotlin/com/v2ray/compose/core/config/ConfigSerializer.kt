package com.v2ray.compose.core.config

import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.util.Base64Util
import com.v2ray.compose.core.util.UrlCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Serialises a [ProfileItem] back into a shareable proxy URI. */
object ConfigSerializer {

    private val json = Json { encodeDefaults = true }

    fun toUri(item: ProfileItem): String = when (item.configType) {
        EConfigType.VMESS -> vmessUri(item)
        EConfigType.VLESS -> stdUri(item)
        EConfigType.TROJAN -> stdUri(item)
        EConfigType.SHADOWSOCKS -> ssUri(item)
        EConfigType.SOCKS, EConfigType.HTTP -> socksUri(item)
        EConfigType.HYSTERIA2 -> hysteria2Uri(item)
        else -> ""
    }

    private fun vmessUri(item: ProfileItem): String {
        val obj: JsonObject = buildJsonObject {
            put("v", "2")
            put("ps", item.remarks)
            put("add", item.server)
            put("port", item.serverPort)
            put("id", item.password ?: "")
            put("aid", (item.alterId ?: 0).toString())
            put("scy", item.security ?: "auto")
            put("net", item.network ?: "tcp")
            put("type", item.headerType ?: "none")
            put("host", item.host ?: "")
            put("path", item.path ?: "")
            put("tls", item.streamSecurity ?: "")
            put("sni", item.sni ?: "")
            put("alpn", item.alpn ?: "")
            put("fp", item.fingerprint ?: "")
        }
        return "vmess://" + Base64Util.encodeToString(json.encodeToString(JsonObject.serializer(), obj))
    }

    /** Shared builder for vless:// and trojan:// which have the same shape. */
    private fun stdUri(item: ProfileItem): String {
        val q = LinkedHashMap<String, String>()
        if (item.configType == EConfigType.VLESS) q["encryption"] = "none"
        putIf(q, "flow", item.flow)
        putIf(q, "security", item.streamSecurity)
        putIf(q, "sni", item.sni)
        putIf(q, "fp", item.fingerprint)
        putIf(q, "pbk", item.publicKey)
        putIf(q, "sid", item.shortId)
        putIf(q, "spx", item.spiderX)
        putIf(q, "alpn", item.alpn)
        q["type"] = item.network ?: "tcp"
        when (item.network) {
            "ws", "httpupgrade", "xhttp", "h2" -> {
                putIf(q, "host", item.host)
                putIf(q, "path", item.path)
            }
            "grpc" -> putIf(q, "serviceName", item.path)
            "kcp" -> putIf(q, "seed", item.seed)
        }
        val query = q.entries.joinToString("&") { "${it.key}=${UrlCodec.encode(it.value)}" }
        val cred = UrlCodec.encode(item.password ?: "")
        val frag = if (item.remarks.isNotEmpty()) "#" + UrlCodec.encode(item.remarks) else ""
        return "${item.configType.scheme}://$cred@${item.server}:${item.serverPort}?$query$frag"
    }

    private fun ssUri(item: ProfileItem): String {
        val userInfo = Base64Util.encodeUrlSafeNoPadding("${item.method}:${item.password}".encodeToByteArray())
        val frag = if (item.remarks.isNotEmpty()) "#" + UrlCodec.encode(item.remarks) else ""
        return "ss://$userInfo@${item.server}:${item.serverPort}$frag"
    }

    private fun socksUri(item: ProfileItem): String {
        val auth = if (!item.method.isNullOrEmpty()) {
            Base64Util.encodeUrlSafeNoPadding("${item.method}:${item.password ?: ""}".encodeToByteArray()) + "@"
        } else ""
        val frag = if (item.remarks.isNotEmpty()) "#" + UrlCodec.encode(item.remarks) else ""
        return "${item.configType.scheme}://$auth${item.server}:${item.serverPort}$frag"
    }

    private fun hysteria2Uri(item: ProfileItem): String {
        val q = LinkedHashMap<String, String>()
        putIf(q, "sni", item.sni)
        if (item.allowInsecure == true) q["insecure"] = "1"
        putIf(q, "obfs-password", item.obfsPassword)
        val query = if (q.isEmpty()) "" else "?" + q.entries.joinToString("&") { "${it.key}=${UrlCodec.encode(it.value)}" }
        val cred = UrlCodec.encode(item.password ?: "")
        val frag = if (item.remarks.isNotEmpty()) "#" + UrlCodec.encode(item.remarks) else ""
        return "hysteria2://$cred@${item.server}:${item.serverPort}$query$frag"
    }

    private fun putIf(map: MutableMap<String, String>, key: String, value: String?) {
        if (!value.isNullOrEmpty()) map[key] = value
    }
}
