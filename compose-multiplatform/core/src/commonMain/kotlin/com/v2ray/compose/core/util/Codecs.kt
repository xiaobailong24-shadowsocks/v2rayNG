package com.v2ray.compose.core.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64 helpers that tolerate the many shapes proxy links use in the wild:
 * standard vs url-safe alphabet, and missing `=` padding.
 */
@OptIn(ExperimentalEncodingApi::class)
object Base64Util {

    /** Decode [input] to a UTF-8 string, trying url-safe then standard alphabets. */
    fun decodeToString(input: String): String? = decode(input)?.decodeToString()

    fun decode(input: String): ByteArray? {
        val cleaned = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val padded = cleaned.padToBase64()
        // Try url-safe first (handles '-' and '_'), then the standard alphabet.
        return runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(padded) }
            .recoverCatching { Base64.decode(padded) }
            .recoverCatching { Base64.Mime.decode(padded) }
            .getOrNull()
    }

    fun encode(bytes: ByteArray): String = Base64.encode(bytes)

    fun encodeUrlSafeNoPadding(bytes: ByteArray): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

    fun encodeToString(text: String): String = encode(text.encodeToByteArray())

    private fun String.padToBase64(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }
}
