package com.v2ray.compose.core.util

import kotlin.random.Random

/** Wall-clock time in milliseconds. Provided per platform. */
expect fun currentTimeMillis(): Long

/** Human name of the running platform, shown in the About screen. */
expect fun platformName(): String

/** Generate a random 32-char lowercase hex identifier for new profiles. */
fun newId(): String {
    val bytes = Random.nextBytes(16)
    val sb = StringBuilder(32)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        sb.append(hex[v ushr 4])
        sb.append(hex[v and 0xF])
    }
    return sb.toString()
}

private const val hex = "0123456789abcdef"
