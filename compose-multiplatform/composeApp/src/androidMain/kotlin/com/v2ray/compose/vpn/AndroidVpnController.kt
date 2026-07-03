package com.v2ray.compose.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.vpn.ConnectionStats
import com.v2ray.compose.core.vpn.VpnController
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * [VpnController] that drives [V2RayVpnService]. The VPN consent dialog
 * (`VpnService.prepare`) must have been accepted by the Activity before
 * [start] is called; [com.v2ray.compose.MainActivity] handles that.
 */
class AndroidVpnController(private val context: Context) : VpnController {

    override val stats: StateFlow<ConnectionStats> = TunnelState.flow

    override suspend fun start(profile: ProfileItem, xrayConfigJson: String) {
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.CONNECTING, activeProfileId = profile.id)
        val configFile = File(context.filesDir, "xray_config.json").apply { writeText(xrayConfigJson) }
        val intent = Intent(context, V2RayVpnService::class.java).apply {
            action = V2RayVpnService.ACTION_START
            putExtra(V2RayVpnService.EXTRA_CONFIG_PATH, configFile.absolutePath)
            putExtra(V2RayVpnService.EXTRA_PROFILE_ID, profile.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override suspend fun stop() {
        TunnelState.flow.value = ConnectionStats(status = ConnectionStatus.DISCONNECTING)
        val intent = Intent(context, V2RayVpnService::class.java).apply { action = V2RayVpnService.ACTION_STOP }
        context.startService(intent)
    }
}
