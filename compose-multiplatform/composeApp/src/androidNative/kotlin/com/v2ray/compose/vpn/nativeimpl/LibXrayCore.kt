package com.v2ray.compose.vpn.nativeimpl

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.v2ray.compose.vpn.XrayCore
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/**
 * Real [XrayCore] backed by `libv2ray.aar` (gomobile build of AndroidLibXrayLite,
 * the exact core v2rayNG ships). Loaded reflectively by [XrayCore.load]; only
 * compiled into the `androidNative` source set (build with -PwithNative=true).
 *
 * Mirrors v2rayNG's CoreNativeManager: init the core env once, create a
 * CoreController with a callback, then startLoop/stopLoop. The tunnel data path
 * is handled by hev tun2socks, so the core runs with tunFd = 0 and only exposes
 * its local SOCKS inbound.
 */
@Suppress("unused")
class LibXrayCore : XrayCore {

    private var controller: CoreController? = null
    private var envReady = false

    override fun initEnv(context: Context) {
        if (envReady) return
        Seq.setContext(context.applicationContext)
        val assetPath = File(context.filesDir, "assets").apply { mkdirs() }.absolutePath
        val deviceId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        Libv2ray.initCoreEnv(assetPath, deviceId)
        envReady = true
    }

    override fun start(configJson: String): String {
        return try {
            val handler = object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(l: Long, s: String?): Long = 0
            }
            val c = Libv2ray.newCoreController(handler)
            // tunFd = 0: hev-socks5-tunnel owns the TUN; the core only serves SOCKS.
            c.startLoop(configJson, 0)
            controller = c
            if (!c.isRunning) "Core failed to start" else ""
        } catch (t: Throwable) {
            Log.e("LibXrayCore", "start failed", t)
            t.message ?: "Core start error"
        }
    }

    override fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
    }

    override fun isRunning(): Boolean = controller?.isRunning ?: false

    override fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrElse { "unknown" }
}
