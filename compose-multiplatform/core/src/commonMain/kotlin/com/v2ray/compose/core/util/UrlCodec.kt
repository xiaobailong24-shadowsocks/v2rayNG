package com.v2ray.compose.core.util

/**
 * Minimal, dependency-free percent-encoding used when parsing / building proxy
 * URIs. Kept in commonMain so behaviour is identical on every platform.
 */
object UrlCodec {

    fun decode(value: String): String {
        if (value.isEmpty() || (value.indexOf('%') < 0 && value.indexOf('+') < 0)) return value
        val out = StringBuilder(value.length)
        val bytes = ArrayList<Byte>()

        fun flushBytes() {
            if (bytes.isNotEmpty()) {
                out.append(bytes.toByteArray().decodeToString())
                bytes.clear()
            }
        }

        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    val b = hex.toIntOrNull(16)
                    if (b != null) {
                        bytes.add(b.toByte())
                        i += 3
                        continue
                    } else {
                        flushBytes(); out.append(c)
                    }
                }
                c == '+' -> { flushBytes(); out.append(' ') }
                else -> { flushBytes(); out.append(c) }
            }
            i++
        }
        flushBytes()
        return out.toString()
    }

    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    fun encode(value: String): String {
        val out = StringBuilder(value.length)
        for (b in value.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if (c in UNRESERVED) {
                out.append(c)
            } else {
                out.append('%')
                out.append(((b.toInt() shr 4) and 0xF).hexDigit())
                out.append((b.toInt() and 0xF).hexDigit())
            }
        }
        return out.toString()
    }

    private fun Int.hexDigit(): Char = if (this < 10) ('0' + this) else ('A' + (this - 10))
}

/** Parse a query string (without leading `?`) into a map, percent-decoding values. */
fun parseQuery(query: String?): Map<String, String> {
    if (query.isNullOrEmpty()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    for (part in query.split('&')) {
        if (part.isEmpty()) continue
        val idx = part.indexOf('=')
        if (idx < 0) {
            result[UrlCodec.decode(part)] = ""
        } else {
            val key = UrlCodec.decode(part.substring(0, idx))
            val value = UrlCodec.decode(part.substring(idx + 1))
            result[key] = value
        }
    }
    return result
}
