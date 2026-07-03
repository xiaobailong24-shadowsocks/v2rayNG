package com.v2ray.compose.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual suspend fun httpGetText(url: String, userAgent: String): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            if (userAgent.isNotEmpty()) conn.setRequestProperty("User-Agent", userAgent)
            try {
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
                conn.inputStream.readBytes().decodeToString()
            } finally {
                conn.disconnect()
            }
        }
    }
