package com.v2ray.compose.core.config

import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.util.Base64Util

/**
 * Turns a subscription payload into profiles. Handles both base64-encoded
 * bodies (the common case) and already-decoded plaintext, one link per line.
 */
object SubscriptionParser {

    fun parse(rawBody: String, subscriptionId: String = ""): List<ProfileItem> {
        val text = normalise(rawBody)
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { ConfigParser.parse(it) }
            .map { if (subscriptionId.isNotEmpty()) it.copy(subscriptionId = subscriptionId) else it }
            .toList()
    }

    /** Decode the body if it is base64; otherwise return it unchanged. */
    private fun normalise(rawBody: String): String {
        val body = rawBody.trim()
        if (looksLikeLinks(body)) return body
        val decoded = Base64Util.decodeToString(body)
        return if (decoded != null && looksLikeLinks(decoded)) decoded else body
    }

    private fun looksLikeLinks(text: String): Boolean {
        val schemes = listOf("vmess://", "vless://", "trojan://", "ss://", "socks://", "hysteria2://", "tuic://")
        return schemes.any { text.contains(it) }
    }
}
