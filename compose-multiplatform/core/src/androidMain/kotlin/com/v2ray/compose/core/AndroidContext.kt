package com.v2ray.compose.core

import android.content.Context

/**
 * Holds the application [Context] so platform actuals (storage, VpnService
 * launching) can reach it without threading it through common code. Set once
 * from the app's Application.onCreate().
 */
object AndroidContext {
    lateinit var app: Context

    val isInitialized: Boolean get() = ::app.isInitialized

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }
}
