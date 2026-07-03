package com.v2ray.compose.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.v2ray.compose.core.config.XrayConfigBuilder
import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.vpn.ConnectionStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Establishes the TUN interface and runs Xray-core + tun2socks through
 * [V2RayBridge]. This is the Android data path — the counterpart of iOS's
 * `PacketTunnelProvider`.
 */
class V2RayVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statsJob: Job? = null
    private var activeProfileId: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                val configPath = intent?.getStringExtra(EXTRA_CONFIG_PATH)
                activeProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                val serverAddress = intent?.getStringExtra(EXTRA_SERVER_ADDRESS)
                startTunnel(configPath, serverAddress)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(configPath: String?, serverAddress: String?) {
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.CONNECTING, activeProfileId = activeProfileId)
        if (!V2RayBridge.available) {
            fail("Native transport (libv2ray.so) is not bundled in this build. See NATIVE.md.")
            return
        }
        val config = configPath?.let { runCatching { java.io.File(it).readText() }.getOrNull() }
        if (config.isNullOrBlank()) {
            fail("Missing Xray config")
            return
        }
        try {
            // 1) Xray-core first, so its SOCKS inbound is ready before tun2socks connects.
            val err = V2RayBridge.startXray(config)
            if (err.isNotEmpty()) {
                fail("Xray-core failed to start: $err")
                return
            }

            // 2) TUN interface. The proxy server address is excluded from the tunnel
            //    routes so Xray's own outbound (protected below) reaches the internet.
            val builder = Builder()
                .setSession("v2rayNG Compose")
                .setMtu(MTU)
                .addAddress("10.10.10.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            // Keep this app's own traffic out of the tunnel to avoid loops.
            runCatching { builder.addDisallowedApplication(packageName) }

            val fd = builder.establish() ?: error("VPN establish() returned null")
            tunFd = fd

            startForegroundNotification()

            // 3) tun2socks pumps packets between the TUN fd and Xray's SOCKS inbound.
            V2RayBridge.startTun2socks(fd.fd, "127.0.0.1", XrayConfigBuilder.SOCKS_PORT, MTU)

            startStatsPolling()
            TunnelState.flow.value = ConnectionStats(
                status = ConnectionStatus.CONNECTED,
                activeProfileId = activeProfileId,
                message = "Tunnel established",
            )
        } catch (t: Throwable) {
            fail(t.message ?: "Failed to start tunnel")
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                val stats = runCatching { V2RayBridge.queryStats() }.getOrNull()
                if (stats != null && stats.size >= 2) {
                    val current = TunnelState.flow.value
                    if (current.status == ConnectionStatus.CONNECTED) {
                        TunnelState.flow.value = current.copy(uploadTotal = stats[0], downloadTotal = stats[1])
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopTunnel() {
        statsJob?.cancel()
        runCatching { V2RayBridge.stopTun2socks() }
        runCatching { V2RayBridge.stopXray() }
        runCatching { tunFd?.close() }
        tunFd = null
        stopForegroundCompat()
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.DISCONNECTED)
        stopSelf()
    }

    private fun fail(message: String) {
        runCatching { V2RayBridge.stopXray() }
        runCatching { tunFd?.close() }
        tunFd = null
        stopForegroundCompat()
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.ERROR, message = message)
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { tunFd?.close() }
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN status", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, V2RayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("v2rayNG Compose")
            .setContentText("Connected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stopIntent).build())
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
        const val EXTRA_SERVER_ADDRESS = "server_address"
        private const val CHANNEL_ID = "v2ray_vpn"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500

        @Volatile
        private var instance: V2RayVpnService? = null

        /**
         * Called from native code (see `cpp/bridge.c`) so Xray-core's outbound
         * sockets are protected from the VPN routing loop.
         */
        @JvmStatic
        fun protectSocket(fd: Int): Boolean = instance?.protect(fd) ?: false
    }
}
