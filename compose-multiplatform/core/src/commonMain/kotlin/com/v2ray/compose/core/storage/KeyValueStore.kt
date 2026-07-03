package com.v2ray.compose.core.storage

/**
 * Minimal persistent string key-value store, implemented per platform with
 * SharedPreferences (Android), NSUserDefaults (iOS) and a properties file
 * (JVM/desktop).
 */
expect class KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
