package com.v2ray.compose.core.util

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformName(): String =
    "Desktop (${System.getProperty("os.name")} ${System.getProperty("os.arch")})"
