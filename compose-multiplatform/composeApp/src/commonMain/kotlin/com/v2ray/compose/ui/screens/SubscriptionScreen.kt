package com.v2ray.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.compose.core.model.SubscriptionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    subscriptions: List<SubscriptionItem>,
    onSave: (SubscriptionItem) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onUpdate: (SubscriptionItem) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<SubscriptionItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add subscription")
            }
        },
    ) { padding ->
        if (subscriptions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No subscriptions", style = MaterialTheme.typography.titleMedium)
                Text("Add a subscription URL to auto-import servers.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(subscriptions, key = { it.id }) { sub ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.remarks.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
                                Text(sub.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(checked = sub.enabled, onCheckedChange = { onSave(sub.copy(enabled = it)) })
                            IconButton(onClick = { onUpdate(sub) }) { Icon(Icons.Default.Refresh, "Update") }
                            IconButton(onClick = { editing = sub; showEditor = true }) {
                                Icon(Icons.Default.Add, "Edit")
                            }
                            IconButton(onClick = { onDelete(sub.id, true) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        SubscriptionEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onConfirm = { onSave(it); showEditor = false },
        )
    }
}

@Composable
private fun SubscriptionEditor(
    initial: SubscriptionItem?,
    onDismiss: () -> Unit,
    onConfirm: (SubscriptionItem) -> Unit,
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add subscription" else "Edit subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = initial ?: SubscriptionItem(id = "")
                    onConfirm(base.copy(remarks = remarks, url = url))
                },
                enabled = url.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
