package com.v2ray.compose.vpn

import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.vpn.ConnectionStats
import com.v2ray.compose.core.vpn.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NEVPNStatus

/**
 * iOS [VpnController] driving a Packet Tunnel provider.
 *
 * The actual data path lives in the `PacketTunnel` app extension (see
 * `iosApp/PacketTunnel/PacketTunnelProvider.swift`): it runs Xray-core and a
 * tun2socks bridge inside `NEPacketTunnelProvider`. This class installs/loads the
 * tunnel configuration and starts/stops it; the rendered Xray JSON is handed to
 * the extension via the protocol's `providerConfiguration`.
 */
class IosVpnController : VpnController {

    private val _stats = MutableStateFlow(ConnectionStats())
    override val stats: StateFlow<ConnectionStats> = _stats.asStateFlow()

    override suspend fun start(profile: ProfileItem, xrayConfigJson: String) {
        _stats.value = ConnectionStats(status = ConnectionStatus.CONNECTING, activeProfileId = profile.id)
        NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { managers, error ->
            if (error != null) {
                _stats.value = ConnectionStats(status = ConnectionStatus.ERROR, message = error.localizedDescription)
                return@loadAllFromPreferencesWithCompletionHandler
            }
            val manager = (managers?.firstOrNull() as? NETunnelProviderManager) ?: NETunnelProviderManager()
            val proto = NETunnelProviderProtocol().apply {
                providerBundleIdentifier = PACKET_TUNNEL_BUNDLE_ID
                serverAddress = profile.server
                // INTEGRATION: the extension reads this to configure Xray-core.
                providerConfiguration = mapOf<Any?, Any?>(
                    "xrayConfig" to xrayConfigJson,
                    "profileId" to profile.id,
                )
            }
            manager.protocolConfiguration = proto
            manager.localizedDescription = "v2rayNG Compose"
            manager.enabled = true
            manager.saveToPreferencesWithCompletionHandler { saveError ->
                if (saveError != null) {
                    _stats.value = ConnectionStats(status = ConnectionStatus.ERROR, message = saveError.localizedDescription)
                    return@saveToPreferencesWithCompletionHandler
                }
                runCatching { manager.connection.startVPNTunnelAndReturnError(null) }
                    .onSuccess {
                        _stats.value = ConnectionStats(status = ConnectionStatus.CONNECTED, activeProfileId = profile.id)
                    }
                    .onFailure {
                        _stats.value = ConnectionStats(status = ConnectionStatus.ERROR, message = it.message ?: "start failed")
                    }
            }
        }
    }

    override suspend fun stop() {
        _stats.value = ConnectionStats(status = ConnectionStatus.DISCONNECTING)
        NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { managers, _ ->
            (managers?.firstOrNull() as? NETunnelProviderManager)?.connection?.stopVPNTunnel()
            _stats.value = ConnectionStats(status = ConnectionStatus.DISCONNECTED)
        }
    }

    @Suppress("unused")
    private fun mapStatus(status: NEVPNStatus): ConnectionStatus = when (status) {
        NEVPNStatus.NEVPNStatusConnected -> ConnectionStatus.CONNECTED
        NEVPNStatus.NEVPNStatusConnecting, NEVPNStatus.NEVPNStatusReasserting -> ConnectionStatus.CONNECTING
        NEVPNStatus.NEVPNStatusDisconnecting -> ConnectionStatus.DISCONNECTING
        else -> ConnectionStatus.DISCONNECTED
    }

    companion object {
        const val PACKET_TUNNEL_BUNDLE_ID = "com.v2ray.compose.PacketTunnel"
    }
}
