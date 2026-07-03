package com.v2ray.compose.core.util

import android.os.Build

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformName(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
