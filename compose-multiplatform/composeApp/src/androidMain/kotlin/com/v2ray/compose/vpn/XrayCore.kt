package com.v2ray.compose.vpn

import android.content.Context

/**
 * Abstraction over the Xray-core native library (`libv2ray.aar`, the gomobile
 * build of AndroidLibXrayLite — the same core v2rayNG ships).
 *
 * The concrete implementation ([nativeimpl.LibXrayCore]) lives in the
 * `androidNative` source set, which is only compiled when the app is built with
 * `-PwithNative=true` and the AAR is present. When it is absent, [load] returns
 * null and the service reports the tunnel as unavailable — so the UI still
 * builds and runs without the native core.
 */
interface XrayCore {
    /** Initialise the core environment once (asset path, device id). */
    fun initEnv(context: Context)

    /** Start the core with a full Xray JSON config. Returns "" on success or an error. */
    fun start(configJson: String): String

    fun stop()

    fun isRunning(): Boolean

    fun version(): String

    companion object {
        private const val IMPL = "com.v2ray.compose.vpn.nativeimpl.LibXrayCore"

        /** Reflectively load the native implementation, or null when not bundled. */
        fun load(): XrayCore? = runCatching {
            Class.forName(IMPL).getDeclaredConstructor().newInstance() as XrayCore
        }.getOrNull()
    }
}
