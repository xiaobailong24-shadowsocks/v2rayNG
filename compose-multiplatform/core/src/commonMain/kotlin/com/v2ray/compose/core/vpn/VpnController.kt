package com.v2ray.compose.core.vpn

import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.model.ProfileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live traffic / connection statistics surfaced to the UI. */
data class ConnectionStats(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeProfileId: String? = null,
    val uploadTotal: Long = 0,
    val downloadTotal: Long = 0,
    val message: String = "",
)

/**
 * Platform-agnostic contract for establishing the VPN tunnel. Android backs
 * this with a `VpnService` + libXray; iOS with a `NEPacketTunnelProvider`; the
 * desktop/test build uses [NoopVpnController].
 */
interface VpnController {
    val stats: StateFlow<ConnectionStats>

    /**
     * Start the tunnel for [profile]. [xrayConfigJson] is the fully-rendered
     * Xray-core config produced by
     * [com.v2ray.compose.core.config.XrayConfigBuilder].
     */
    suspend fun start(profile: ProfileItem, xrayConfigJson: String)

    suspend fun stop()
}

/**
 * A controller that models state transitions without a real tunnel. Used by the
 * desktop preview and unit tests; the actual traffic path is provided by the
 * Android / iOS implementations in the app module.
 */
open class NoopVpnController : VpnController {
    protected val _stats = MutableStateFlow(ConnectionStats())
    override val stats: StateFlow<ConnectionStats> = _stats.asStateFlow()

    override suspend fun start(profile: ProfileItem, xrayConfigJson: String) {
        _stats.value = ConnectionStats(
            status = ConnectionStatus.CONNECTED,
            activeProfileId = profile.id,
            message = "Connected (preview – no real tunnel on this platform)",
        )
    }

    override suspend fun stop() {
        _stats.value = ConnectionStats(status = ConnectionStatus.DISCONNECTED)
    }
}
