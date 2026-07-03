package com.v2ray.compose.core.util

/**
 * Simple HTTP GET returning the response body as text. Implemented with the
 * platform networking stack (HttpURLConnection on JVM/Android, NSURLSession on
 * iOS). Used to refresh subscriptions.
 */
expect suspend fun httpGetText(url: String, userAgent: String = ""): Result<String>
