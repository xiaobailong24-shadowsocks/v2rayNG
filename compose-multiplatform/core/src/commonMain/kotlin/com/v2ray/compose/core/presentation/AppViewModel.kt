package com.v2ray.compose.core.presentation

import com.v2ray.compose.core.config.ConfigParser
import com.v2ray.compose.core.config.ConfigSerializer
import com.v2ray.compose.core.config.SubscriptionParser
import com.v2ray.compose.core.config.SubscriptionUpdater
import com.v2ray.compose.core.config.XrayConfigBuilder
import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.model.SubscriptionItem
import com.v2ray.compose.core.repository.ProfileRepository
import com.v2ray.compose.core.util.currentTimeMillis
import com.v2ray.compose.core.util.newId
import com.v2ray.compose.core.vpn.ConnectionStats
import com.v2ray.compose.core.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot of everything the UI renders. */
data class AppUiState(
    val profiles: List<ProfileItem> = emptyList(),
    val subscriptions: List<SubscriptionItem> = emptyList(),
    val selectedId: String? = null,
    val connection: ConnectionStats = ConnectionStats(),
    val transientMessage: String? = null,
)

/**
 * Presentation-layer state holder shared by all platforms. Deliberately not tied
 * to androidx.lifecycle.ViewModel so the exact same instance drives Android, iOS
 * and desktop.
 */
class AppViewModel(
    private val repository: ProfileRepository,
    private val vpn: VpnController,
    private val scope: CoroutineScope,
) {
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUiState> =
        combine(
            repository.profiles,
            repository.subscriptions,
            repository.selectedId,
            vpn.stats,
            message,
        ) { profiles, subs, selected, stats, msg ->
            AppUiState(profiles, subs, selected, stats, msg)
        }.stateIn(scope, SharingStarted.Eagerly, AppUiState())

    val isConnected: Boolean get() = uiState.value.connection.status == ConnectionStatus.CONNECTED

    // ---- profiles ------------------------------------------------------------
    fun selectProfile(id: String) = repository.select(id)

    fun deleteProfile(id: String) = repository.delete(id)

    fun saveProfile(profile: ProfileItem) {
        repository.upsert(profile)
        notify("Saved ${profile.remarks.ifBlank { profile.displayAddress }}")
    }

    fun newProfileTemplate(type: EConfigType): ProfileItem =
        ProfileItem.create(type, newId()).apply { serverPort = "443"; network = "tcp" }

    fun shareLink(id: String): String? = repository.get(id)?.let { ConfigSerializer.toUri(it) }

    /** Import one or many links (newline or whitespace separated). Returns count imported. */
    fun importLinks(text: String): Int {
        val direct = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { ConfigParser.parse(it) }
            .toList()
        val imported = direct.ifEmpty { SubscriptionParser.parse(text) }
        if (imported.isNotEmpty()) {
            repository.upsertAll(imported)
            if (repository.selectedId.value == null) repository.select(imported.first().id)
            notify("Imported ${imported.size} server(s)")
        } else {
            notify("No valid server links found")
        }
        return imported.size
    }

    // ---- subscriptions -------------------------------------------------------
    fun saveSubscription(sub: SubscriptionItem) {
        repository.upsertSubscription(if (sub.id.isBlank()) sub.copy(id = newId()) else sub)
    }

    fun deleteSubscription(id: String, alsoProfiles: Boolean) {
        if (alsoProfiles) repository.deleteBySubscription(id)
        repository.deleteSubscription(id)
    }

    fun updateSubscription(sub: SubscriptionItem) {
        scope.launch {
            notify("Updating ${sub.remarks}…")
            SubscriptionUpdater.fetch(sub)
                .onSuccess { profiles ->
                    repository.deleteBySubscription(sub.id)
                    repository.upsertAll(profiles)
                    repository.upsertSubscription(sub.copy(lastUpdated = currentTimeMillis()))
                    notify("Updated: ${profiles.size} server(s)")
                }
                .onFailure { notify("Update failed: ${it.message}") }
        }
    }

    // ---- connection ----------------------------------------------------------
    fun toggleConnection() {
        val current = uiState.value
        scope.launch {
            when (current.connection.status) {
                ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING -> vpn.stop()
                else -> {
                    val profile = current.selectedId?.let { repository.get(it) }
                        ?: repository.profiles.value.firstOrNull()
                    if (profile == null) {
                        notify("Add a server first")
                        return@launch
                    }
                    repository.select(profile.id)
                    val config = XrayConfigBuilder.buildConfigJson(profile)
                    vpn.start(profile, config)
                }
            }
        }
    }

    fun consumeMessage() { message.value = null }

    private fun notify(text: String) { message.value = text }
}
