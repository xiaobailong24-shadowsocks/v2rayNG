package com.v2ray.compose.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.compose.core.config.XrayConfigBuilder
import com.v2ray.compose.core.util.platformName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(serverCount: Int, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    row("Platform", platformName())
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    row("Servers", serverCount.toString())
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    row("Local SOCKS port", XrayConfigBuilder.SOCKS_PORT.toString())
                    row("Local HTTP port", XrayConfigBuilder.HTTP_PORT.toString())
                }
            }
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("v2rayNG · Compose Multiplatform", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A Kotlin/Compose Multiplatform proxy client sharing one UI and one " +
                            "config engine across Android, iOS and desktop. Supported protocols: " +
                            "VMess, VLESS, Trojan, Shadowsocks, SOCKS, Hysteria2, TUIC.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "The tunnel is provided by Xray-core via platform integrations " +
                            "(VpnService + libXray on Android, NEPacketTunnelProvider on iOS).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun row(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
