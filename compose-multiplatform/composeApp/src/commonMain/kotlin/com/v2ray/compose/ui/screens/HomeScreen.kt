package com.v2ray.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.presentation.AppUiState
import com.v2ray.compose.ui.components.ImportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppUiState,
    snackbar: SnackbarHostState,
    onToggleConnection: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: (String) -> Int,
    onShare: (String) -> String?,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showImport by remember { mutableStateOf(false) }
    var sharePreview by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("v2rayNG · Compose") },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Import")
                    }
                    IconButton(onClick = onOpenSubscriptions) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Subscriptions")
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Add server")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ConnectionCard(
                status = state.connection.status,
                selected = state.profiles.firstOrNull { it.id == state.selectedId },
                message = state.connection.message,
                onToggle = onToggleConnection,
            )

            if (state.profiles.isEmpty()) {
                EmptyState(onImport = { showImport = true }, onAdd = onAdd)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(state.profiles, key = { it.id }) { profile ->
                        ServerRow(
                            profile = profile,
                            selected = profile.id == state.selectedId,
                            onSelect = { onSelect(profile.id) },
                            onEdit = { onEdit(profile.id) },
                            onDelete = { onDelete(profile.id) },
                            onShare = { sharePreview = onShare(profile.id) },
                        )
                    }
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onConfirm = { text -> onImport(text); showImport = false },
        )
    }
    sharePreview?.let { link ->
        ShareDialog(link = link, onDismiss = { sharePreview = null })
    }
}

@Composable
private fun ConnectionCard(
    status: ConnectionStatus,
    selected: ProfileItem?,
    message: String,
    onToggle: () -> Unit,
) {
    val connected = status == ConnectionStatus.CONNECTED
    val busy = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.DISCONNECTING
    val statusColor = when (status) {
        ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> "Connected"
                        ConnectionStatus.CONNECTING -> "Connecting…"
                        ConnectionStatus.DISCONNECTING -> "Disconnecting…"
                        ConnectionStatus.ERROR -> "Error"
                        ConnectionStatus.DISCONNECTED -> "Not connected"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = selected?.let { "${it.remarks.ifBlank { it.displayAddress }} · ${it.configType.name}" }
                        ?: "No server selected",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.isNotEmpty()) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Surface(
                onClick = onToggle,
                enabled = !busy,
                shape = CircleShape,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle connection",
                        tint = if (connected) MaterialTheme.colorScheme.onPrimary else statusColor,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    profile: ProfileItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(
                    profile.remarks.ifBlank { profile.displayAddress },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${profile.configType.name.lowercase()} · ${profile.displayAddress}" +
                        (profile.network?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ProtocolBadge(profile.configType.name)
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ProtocolBadge(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Text(
            name.take(5),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("No servers yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste a vmess:// / vless:// / trojan:// / ss:// link,\nor add a subscription to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(onClick = onImport) { Text("Paste links") }
                androidx.compose.material3.OutlinedButton(onClick = onAdd) { Text("Add manually") }
            }
        }
    }
}
