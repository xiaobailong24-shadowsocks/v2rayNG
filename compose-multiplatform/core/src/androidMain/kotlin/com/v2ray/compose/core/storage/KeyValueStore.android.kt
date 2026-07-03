package com.v2ray.compose.core.storage

import android.content.Context
import com.v2ray.compose.core.AndroidContext

/** Android store backed by SharedPreferences. */
actual class KeyValueStore {
    private val prefs = AndroidContext.app.getSharedPreferences("v2ray_compose", Context.MODE_PRIVATE)

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
