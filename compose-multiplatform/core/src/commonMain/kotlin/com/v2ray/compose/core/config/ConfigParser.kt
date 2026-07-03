package com.v2ray.compose.core.config

import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.util.Base64Util
import com.v2ray.compose.core.util.LinkUri
import com.v2ray.compose.core.util.UrlCodec
import com.v2ray.compose.core.util.newId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses share links (`vmess://`, `vless://`, `trojan://`, `ss://`, ...) into
 * [ProfileItem]s. Mirrors the behaviour of v2rayNG's importer so links copied
 * from either app are understood by the other.
 */
object ConfigParser {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Entry point. Returns null when [link] is not a recognised proxy URI. */
    fun parse(link: String): ProfileItem? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null
        val scheme = trimmed.substringBefore("://", "").lowercase()
        return when (EConfigType.fromScheme(scheme)) {
            EConfigType.VMESS -> parseVmess(trimmed)
            EConfigType.VLESS -> parseVless(trimmed)
            EConfigType.TROJAN -> parseTrojan(trimmed)
            EConfigType.SHADOWSOCKS -> parseShadowsocks(trimmed)
            EConfigType.SOCKS -> parseSocksHttp(trimmed, EConfigType.SOCKS)
            EConfigType.HTTP -> parseSocksHttp(trimmed, EConfigType.HTTP)
            EConfigType.HYSTERIA2 -> parseHysteria2(trimmed)
            EConfigType.TUIC -> parseTuic(trimmed)
            else -> null
        }
    }

    // ---- VMess (base64 JSON) -------------------------------------------------
    private fun parseVmess(link: String): ProfileItem? {
        val body = link.substring("vmess://".length)
        val json = Base64Util.decodeToString(body) ?: return null
        val obj = runCatching { lenientJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        fun str(key: String): String? = (obj[key] as? JsonElement)?.let {
            (it as? JsonPrimitive)?.contentOrNull
        }
        val server = str("add") ?: return null
        val port = str("port") ?: return null
        return ProfileItem.create(EConfigType.VMESS, newId()).apply {
            remarks = str("ps").orEmpty()
            this.server = server
            serverPort = port
            password = str("id")
            alterId = str("aid")?.toIntOrNull() ?: 0
            security = str("scy") ?: "auto"
            network = (str("net") ?: "tcp")
            headerType = str("type") ?: "none"
            host = str("host")
            path = str("path")
            streamSecurity = str("tls")
            sni = str("sni")
            alpn = str("alpn")
            fingerprint = str("fp")
        }
    }

    // ---- VLESS ---------------------------------------------------------------
    private fun parseVless(link: String): ProfileItem? {
        val uri = LinkUri.parse(link) ?: return null
        val uuid = uri.userInfo?.let { UrlCodec.decode(it) } ?: return null
        val q = uri.queryParams
        return ProfileItem.create(EConfigType.VLESS, newId()).apply {
            remarks = uri.decodedFragment.ifEmpty { uri.host }
            server = uri.host
            serverPort = (uri.port ?: return null).toString()
            password = uuid
            flow = q["flow"]
            applyStreamSettings(this, q)
        }
    }

    // ---- Trojan --------------------------------------------------------------
    private fun parseTrojan(link: String): ProfileItem? {
        val uri = LinkUri.parse(link) ?: return null
        val pwd = uri.userInfo?.let { UrlCodec.decode(it) } ?: return null
        val q = uri.queryParams
        return ProfileItem.create(EConfigType.TROJAN, newId()).apply {
            remarks = uri.decodedFragment.ifEmpty { uri.host }
            server = uri.host
            serverPort = (uri.port ?: return null).toString()
            password = pwd
            flow = q["flow"]
            applyStreamSettings(this, q)
            // trojan defaults to tls when no security is specified
            if (streamSecurity.isNullOrEmpty()) streamSecurity = "tls"
        }
    }

    // ---- Shadowsocks ---------------------------------------------------------
    // Supports SIP002 (ss://base64(method:pwd)@host:port#tag) and the legacy
    // fully-base64 form (ss://base64(method:pwd@host:port)#tag).
    private fun parseShadowsocks(link: String): ProfileItem? {
        var body = link.substring("ss://".length)
        var remarks = ""
        val hashIdx = body.indexOf('#')
        if (hashIdx >= 0) {
            remarks = UrlCodec.decode(body.substring(hashIdx + 1))
            body = body.substring(0, hashIdx)
        }
        // strip plugin query if present (not modelled here)
        body = body.substringBefore('?')

        val atIdx = body.indexOf('@')
        val method: String
        val password: String
        val server: String
        val port: String
        if (atIdx >= 0) {
            // SIP002: userinfo may be base64(method:password) or plain method:password
            val userInfoRaw = body.substring(0, atIdx)
            val hostPart = body.substring(atIdx + 1)
            val userInfo = if (userInfoRaw.contains(':')) userInfoRaw
            else Base64Util.decodeToString(userInfoRaw) ?: return null
            val mp = userInfo.split(':', limit = 2)
            if (mp.size != 2) return null
            method = mp[0]
            password = mp[1]
            val hp = splitHostPort(hostPart) ?: return null
            server = hp.first
            port = hp.second
        } else {
            // legacy: whole thing base64(method:password@host:port)
            val decoded = Base64Util.decodeToString(body) ?: return null
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            val cred = decoded.substring(0, at).split(':', limit = 2)
            if (cred.size != 2) return null
            method = cred[0]
            password = cred[1]
            val hp = splitHostPort(decoded.substring(at + 1)) ?: return null
            server = hp.first
            port = hp.second
        }
        return ProfileItem.create(EConfigType.SHADOWSOCKS, newId()).apply {
            this.remarks = remarks.ifEmpty { server }
            this.server = server
            this.serverPort = port
            this.method = method
            this.password = password
            this.network = "tcp"
        }
    }

    // ---- SOCKS / HTTP --------------------------------------------------------
    private fun parseSocksHttp(link: String, type: EConfigType): ProfileItem? {
        val uri = LinkUri.parse(link) ?: return null
        var user: String? = null
        var pass: String? = null
        uri.userInfo?.let { info ->
            val decoded = if (info.contains(':')) info else Base64Util.decodeToString(info) ?: info
            val parts = decoded.split(':', limit = 2)
            user = parts.getOrNull(0)
            pass = parts.getOrNull(1)
        }
        return ProfileItem.create(type, newId()).apply {
            remarks = uri.decodedFragment.ifEmpty { uri.host }
            server = uri.host
            serverPort = (uri.port ?: return null).toString()
            method = user
            password = pass
        }
    }

    // ---- Hysteria2 -----------------------------------------------------------
    private fun parseHysteria2(link: String): ProfileItem? {
        val uri = LinkUri.parse(link) ?: return null
        val q = uri.queryParams
        return ProfileItem.create(EConfigType.HYSTERIA2, newId()).apply {
            remarks = uri.decodedFragment.ifEmpty { uri.host }
            server = uri.host
            serverPort = (uri.port ?: return null).toString()
            password = uri.userInfo?.let { UrlCodec.decode(it) }
            sni = q["sni"]
            allowInsecure = q["insecure"] == "1" || q["insecure"] == "true"
            obfsPassword = q["obfs-password"]
            streamSecurity = "tls"
        }
    }

    // ---- TUIC ----------------------------------------------------------------
    private fun parseTuic(link: String): ProfileItem? {
        val uri = LinkUri.parse(link) ?: return null
        val cred = uri.userInfo?.split(':', limit = 2)
        val q = uri.queryParams
        return ProfileItem.create(EConfigType.TUIC, newId()).apply {
            remarks = uri.decodedFragment.ifEmpty { uri.host }
            server = uri.host
            serverPort = (uri.port ?: return null).toString()
            password = cred?.getOrNull(0)             // uuid
            method = cred?.getOrNull(1)               // token/password
            sni = q["sni"]
            alpn = q["alpn"]
            allowInsecure = q["allow_insecure"] == "1"
            streamSecurity = "tls"
        }
    }

    // ---- shared helpers ------------------------------------------------------
    private fun applyStreamSettings(item: ProfileItem, q: Map<String, String>) {
        item.network = q["type"] ?: "tcp"
        item.headerType = q["headerType"] ?: "none"
        item.streamSecurity = q["security"] ?: ""
        item.sni = q["sni"]
        item.alpn = q["alpn"]
        item.fingerprint = q["fp"]
        item.publicKey = q["pbk"]
        item.shortId = q["sid"]
        item.spiderX = q["spx"]
        item.mode = q["mode"]
        // transport specific host/path
        when (item.network) {
            "ws", "httpupgrade", "xhttp", "h2", "http" -> {
                item.host = q["host"]
                item.path = q["path"]
            }
            "grpc" -> {
                item.path = q["serviceName"]
                item.mode = q["mode"] ?: "gun"
            }
            "kcp" -> {
                item.headerType = q["headerType"] ?: "none"
                item.seed = q["seed"]
            }
            "quic" -> {
                item.host = q["quicSecurity"] ?: "none"
                item.path = q["key"]
                item.headerType = q["headerType"] ?: "none"
            }
        }
    }

    private fun splitHostPort(value: String): Pair<String, String>? {
        val v = value.trim()
        if (v.startsWith("[")) {
            val close = v.indexOf(']')
            if (close < 0) return null
            val host = v.substring(1, close)
            val after = v.substring(close + 1)
            val port = if (after.startsWith(":")) after.substring(1) else return null
            return host to port
        }
        val colon = v.lastIndexOf(':')
        if (colon < 0) return null
        val host = v.substring(0, colon)
        val port = v.substring(colon + 1)
        if (host.isEmpty() || port.toIntOrNull() == null) return null
        return host to port
    }
}
