package com.v2ray.compose.core.storage

import java.io.File
import java.util.Properties

/** Desktop store backed by a properties file under the user's home directory. */
actual class KeyValueStore(
    private val file: File = defaultFile(),
) {
    private val props = Properties()

    init {
        if (file.exists()) file.inputStream().use { props.load(it) }
    }

    actual fun getString(key: String): String? = props.getProperty(key)

    actual fun putString(key: String, value: String) {
        props.setProperty(key, value)
        flush()
    }

    actual fun remove(key: String) {
        props.remove(key)
        flush()
    }

    private fun flush() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "V2rayNGCompose") }
    }

    companion object {
        private fun defaultFile(): File {
            val home = System.getProperty("user.home") ?: "."
            return File(home, ".v2rayng-compose/store.properties")
        }
    }
}
