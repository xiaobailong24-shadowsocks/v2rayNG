package com.v2ray.compose.core.model

import kotlinx.serialization.Serializable

/**
 * A single proxy server profile. This is the multiplatform equivalent of
 * v2rayNG's `ProfileItem`, holding every field needed to reconstruct an
 * Xray-core outbound and to round-trip the sharing URI.
 */
@Serializable
data class ProfileItem(
    val id: String,
    val configType: EConfigType,
    var remarks: String = "",
    // ---- connection ----
    var server: String = "",
    var serverPort: String = "",
    // ---- auth ----
    var password: String? = null,   // trojan / ss password, vless & vmess uuid
    var method: String? = null,     // ss cipher
    var flow: String? = null,       // vless flow (e.g. xtls-rprx-vision)
    var alterId: Int? = null,       // vmess alterId
    var security: String? = null,   // vmess cipher (auto/aes-128-gcm/...)
    // ---- stream / transport ----
    var network: String? = null,    // tcp / ws / grpc / h2 / quic / kcp / httpupgrade / xhttp
    var headerType: String? = null,
    var host: String? = null,       // ws/h2 Host header, quic security
    var path: String? = null,       // ws/h2 path, grpc serviceName, kcp/quic key
    var seed: String? = null,       // kcp seed
    var mode: String? = null,       // grpc mode: gun / multi
    // ---- tls / reality ----
    var streamSecurity: String? = null, // "", tls, reality
    var sni: String? = null,
    var alpn: String? = null,
    var fingerprint: String? = null,     // utls fingerprint
    var allowInsecure: Boolean? = null,
    var publicKey: String? = null,       // reality
    var shortId: String? = null,         // reality
    var spiderX: String? = null,         // reality
    // ---- misc protocol specific ----
    var obfsPassword: String? = null,    // hysteria2 obfs
    var portHopping: String? = null,     // hysteria2
    // ---- bookkeeping ----
    var subscriptionId: String = "",
) {
    val displayAddress: String get() = "$server:$serverPort"

    companion object {
        fun create(configType: EConfigType, id: String): ProfileItem =
            ProfileItem(id = id, configType = configType)
    }
}
