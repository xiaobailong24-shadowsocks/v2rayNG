package com.v2ray.compose.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
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
import java.io.File

/**
 * Android data path, mirroring v2rayNG: Xray-core (via [XrayCore] → libv2ray)
 * exposes a local SOCKS inbound, and hev-socks5-tunnel ([TProxyService]) bridges the
 * VpnService TUN fd to it.
 */
class V2RayVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var xray: XrayCore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statsJob: Job? = null
    private var activeProfileId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                activeProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                startTunnel(intent?.getStringExtra(EXTRA_CONFIG_PATH))
            }
        }
        return START_STICKY
    }

    private fun startTunnel(configPath: String?) {
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.CONNECTING, activeProfileId = activeProfileId)

        // Enter the foreground FIRST: the service is launched with
        // startForegroundService(), so the platform kills the process with
        // ForegroundServiceDidNotStartInTimeException unless startForeground() runs
        // within ~5s — well before the core boot + establish() below, and it must
        // also run on the fail() paths. fail()/stopTunnel() tear it back down.
        startForegroundNotification()

        val core = XrayCore.load()
        if (core == null || !TProxyService.available) {
            fail("Native core not bundled (build with -PwithNative=true; see NATIVE.md).")
            return
        }
        val config = configPath?.let { runCatching { File(it).readText() }.getOrNull() }
        if (config.isNullOrBlank()) {
            fail("Missing Xray config")
            return
        }

        try {
            // 1) Start Xray-core (serves SOCKS on 127.0.0.1:SOCKS_PORT).
            core.initEnv(applicationContext)
            val err = core.start(config)
            if (err.isNotEmpty()) {
                fail("Xray-core failed to start: $err")
                return
            }
            xray = core

            // 2) TUN interface. Excluding our own package keeps the core's outbound
            //    sockets out of the tunnel (loop avoidance) without per-socket protect.
            val builder = Builder()
                .setSession("v2rayNG Compose")
                .setMtu(MTU)
                .addAddress(TUN_IPV4, 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            runCatching { builder.addDisallowedApplication(packageName) }

            val fd = builder.establish() ?: error("VPN establish() returned null")
            tunFd = fd

            // 3) hev tun2socks bridges the TUN fd to the core's SOCKS inbound.
            val tunConfig = TProxyService.buildConfig(
                mtu = MTU,
                ipv4Client = TUN_IPV4,
                socksAddress = "127.0.0.1",
                socksPort = XrayConfigBuilder.SOCKS_PORT,
            )
            val tunConfigFile = File(filesDir, "hev-socks5-tunnel.yaml").apply { writeText(tunConfig) }
            TProxyService.start(tunConfigFile.absolutePath, fd.fd)

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
                val stats = TProxyService.stats()
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
        runCatching { TProxyService.stop() }
        runCatching { xray?.stop() }
        xray = null
        runCatching { tunFd?.close() }
        tunFd = null
        stopForegroundCompat()
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.DISCONNECTED)
        stopSelf()
    }

    private fun fail(message: String) {
        runCatching { xray?.stop() }
        xray = null
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
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        // NotificationChannel is API 26+; NotificationCompat.Builder backports the
        // channel-id constructor to API 24/25 (the plain Notification.Builder(ctx,
        // channelId) does NOT exist below 26 and would NoSuchMethodError there).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN status", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, V2RayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("v2rayNG Compose")
            .setContentText("Proxy service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .addAction(0, "Disconnect", stopIntent)
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
        private const val TUN_IPV4 = "10.10.10.1"
    }
}
