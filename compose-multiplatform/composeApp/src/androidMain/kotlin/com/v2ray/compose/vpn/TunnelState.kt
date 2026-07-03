package com.v2ray.compose.vpn

import com.v2ray.compose.core.vpn.ConnectionStats
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide tunnel state shared between the VpnService and the controller. */
internal object TunnelState {
    val flow = MutableStateFlow(ConnectionStats())
}
