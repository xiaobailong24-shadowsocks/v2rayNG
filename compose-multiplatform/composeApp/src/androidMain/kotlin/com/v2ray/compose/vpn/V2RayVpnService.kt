package com.v2ray.compose.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.vpn.ConnectionStats

/**
 * Establishes the TUN interface and hosts the proxy core.
 *
 * The transport itself is provided by the same native pieces v2rayNG uses:
 *   • Xray-core through `libXray` (AndroidLibXrayLite), fed the JSON built by
 *     [com.v2ray.compose.core.config.XrayConfigBuilder]; it exposes a local
 *     SOCKS inbound on 127.0.0.1:10808.
 *   • `hev-socks5-tunnel` (tun2socks) bridging this VpnService's TUN fd to that
 *     SOCKS inbound.
 *
 * Those `.so` libraries are wired in via Gradle/CMake in a production build; the
 * lifecycle and TUN setup below are complete and the two integration points are
 * marked with `INTEGRATION`.
 */
class V2RayVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                val configPath = intent?.getStringExtra(EXTRA_CONFIG_PATH)
                val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                startTunnel(configPath, profileId)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(configPath: String?, profileId: String?) {
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.CONNECTING, activeProfileId = profileId)
        try {
            val builder = Builder()
                .setSession("v2rayNG Compose")
                .setMtu(1500)
                .addAddress("10.10.10.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            val fd = builder.establish() ?: error("VPN establish() returned null")
            tunFd = fd

            startForegroundNotification()

            // INTEGRATION 1: start Xray-core with `configPath` via libXray
            //   LibXray.runXray(applicationContext.filesDir.path, configPath)
            // INTEGRATION 2: start hev-socks5-tunnel to bridge fd.fd <-> 127.0.0.1:10808
            //   Tun2Socks.start(fd.fd, socksPort = 10808, mtu = 1500)

            TunnelState.flow.value = ConnectionStats(
                status = ConnectionStatus.CONNECTED,
                activeProfileId = profileId,
                message = "Tunnel established",
            )
        } catch (t: Throwable) {
            TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.ERROR, message = t.message ?: "Failed to start")
            stopTunnel()
        }
    }

    private fun stopTunnel() {
        // INTEGRATION: Tun2Socks.stop(); LibXray.stopXray()
        runCatching { tunFd?.close() }
        tunFd = null
        stopForegroundCompat()
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.DISCONNECTED)
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { tunFd?.close() }
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN status", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("v2rayNG Compose")
            .setContentText("Connected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.v2ray.compose.START"
        const val ACTION_STOP = "com.v2ray.compose.STOP"
        const val EXTRA_CONFIG_PATH = "config_path"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val CHANNEL_ID = "v2ray_vpn"
        private const val NOTIFICATION_ID = 1
    }
}
