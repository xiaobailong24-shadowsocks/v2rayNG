package com.v2ray.compose.core.util

/**
 * A tolerant, multiplatform URI splitter for proxy links of the form
 * `scheme://[userinfo@]host[:port][/path][?query][#fragment]`.
 *
 * `java.net.URI` is not available in commonMain and is also too strict for the
 * loosely-formatted links shared by proxy panels, so this does the minimal
 * splitting the parsers need.
 */
data class LinkUri(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    val queryParams: Map<String, String> by lazy { parseQuery(query) }
    val decodedFragment: String get() = fragment?.let { UrlCodec.decode(it) } ?: ""

    companion object {
        fun parse(raw: String): LinkUri? {
            val schemeSep = raw.indexOf("://")
            if (schemeSep <= 0) return null
            val scheme = raw.substring(0, schemeSep).lowercase()
            var rest = raw.substring(schemeSep + 3)

            // fragment
            var fragment: String? = null
            val hashIdx = rest.indexOf('#')
            if (hashIdx >= 0) {
                fragment = rest.substring(hashIdx + 1)
                rest = rest.substring(0, hashIdx)
            }

            // query
            var query: String? = null
            val qIdx = rest.indexOf('?')
            if (qIdx >= 0) {
                query = rest.substring(qIdx + 1)
                rest = rest.substring(0, qIdx)
            }

            // userinfo
            var userInfo: String? = null
            val atIdx = rest.lastIndexOf('@')
            if (atIdx >= 0) {
                userInfo = rest.substring(0, atIdx)
                rest = rest.substring(atIdx + 1)
            }

            // path
            var path = ""
            val slashIdx = rest.indexOf('/')
            if (slashIdx >= 0) {
                path = rest.substring(slashIdx)
                rest = rest.substring(0, slashIdx)
            }

            // host:port — handle IPv6 in [ ]
            val host: String
            val port: Int?
            if (rest.startsWith("[")) {
                val close = rest.indexOf(']')
                if (close < 0) return null
                host = rest.substring(1, close)
                val after = rest.substring(close + 1)
                port = if (after.startsWith(":")) after.substring(1).toIntOrNull() else null
            } else {
                val colon = rest.lastIndexOf(':')
                if (colon >= 0) {
                    host = rest.substring(0, colon)
                    port = rest.substring(colon + 1).toIntOrNull()
                } else {
                    host = rest
                    port = null
                }
            }
            if (host.isEmpty()) return null
            return LinkUri(scheme, userInfo, host, port, path, query, fragment)
        }
    }
}
