package com.v2ray.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.model.ProfileItem

private val editableTypes = listOf(
    EConfigType.VMESS, EConfigType.VLESS, EConfigType.TROJAN,
    EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.HYSTERIA2,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    existing: ProfileItem?,
    templateFactory: (EConfigType) -> ProfileItem,
    onSave: (ProfileItem) -> Unit,
    onBack: () -> Unit,
) {
    var type by remember { mutableStateOf(existing?.configType ?: EConfigType.VLESS) }
    var draft by remember { mutableStateOf(existing ?: templateFactory(type)) }

    fun set(block: ProfileItem.() -> Unit) {
        draft = draft.copy().apply(block)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add server" else "Edit server") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onSave(draft.copy(configType = type)) }) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (existing == null) {
                Text("Protocol", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editableTypes.forEach { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = { type = t; draft = templateFactory(t).copy(remarks = draft.remarks) },
                            label = { Text(t.name) },
                        )
                    }
                }
            }

            field("Remarks", draft.remarks) { v -> set { remarks = v } }
            field("Address", draft.server) { v -> set { server = v } }
            field("Port", draft.serverPort) { v -> set { serverPort = v } }

            when (type) {
                EConfigType.VMESS, EConfigType.VLESS ->
                    field("UUID", draft.password ?: "") { v -> set { password = v } }
                EConfigType.TROJAN, EConfigType.HYSTERIA2 ->
                    field("Password", draft.password ?: "") { v -> set { password = v } }
                EConfigType.SHADOWSOCKS -> {
                    field("Method (cipher)", draft.method ?: "") { v -> set { method = v } }
                    field("Password", draft.password ?: "") { v -> set { password = v } }
                }
                EConfigType.SOCKS -> {
                    field("Username (optional)", draft.method ?: "") { v -> set { method = v } }
                    field("Password (optional)", draft.password ?: "") { v -> set { password = v } }
                }
                else -> {}
            }

            if (type == EConfigType.VMESS || type == EConfigType.VLESS || type == EConfigType.TROJAN) {
                field("Network (tcp/ws/grpc/h2)", draft.network ?: "tcp") { v -> set { network = v } }
                field("Security (\"\"/tls/reality)", draft.streamSecurity ?: "") { v -> set { streamSecurity = v } }
                field("SNI", draft.sni ?: "") { v -> set { sni = v } }
                field("Host header", draft.host ?: "") { v -> set { host = v } }
                field("Path / serviceName", draft.path ?: "") { v -> set { path = v } }
                if (type == EConfigType.VLESS) {
                    field("Flow", draft.flow ?: "") { v -> set { flow = v } }
                    field("Reality publicKey", draft.publicKey ?: "") { v -> set { publicKey = v } }
                    field("Reality shortId", draft.shortId ?: "") { v -> set { shortId = v } }
                }
            }
        }
    }
}

@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
