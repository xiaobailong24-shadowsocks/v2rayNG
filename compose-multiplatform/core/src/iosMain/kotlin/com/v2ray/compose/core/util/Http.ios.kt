package com.v2ray.compose.core.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLResponse
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual suspend fun httpGetText(url: String, userAgent: String): Result<String> =
    suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resume(Result.failure(IllegalArgumentException("Invalid URL")))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        if (userAgent.isNotEmpty()) request.setValue(userAgent, forHTTPHeaderField = "User-Agent")

        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, _: NSURLResponse?, error: NSError? ->
            when {
                error != null -> cont.resume(Result.failure(RuntimeException(error.localizedDescription)))
                data != null -> {
                    val text = NSString.create(data, NSUTF8StringEncoding) as String?
                    if (text != null) cont.resume(Result.success(text))
                    else cont.resume(Result.failure(RuntimeException("Could not decode response")))
                }
                else -> cont.resume(Result.failure(RuntimeException("Empty response")))
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
