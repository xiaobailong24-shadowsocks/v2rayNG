package com.v2ray.compose.core.repository

import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.model.SubscriptionItem
import com.v2ray.compose.core.storage.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [ProfileRepository] that persists its state to a [KeyValueStore] as JSON.
 * Loads once on construction and re-serialises on every mutation.
 */
class StoredProfileRepository(
    private val store: KeyValueStore,
) : InMemoryProfileRepository() {

    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Snapshot(
        val profiles: List<ProfileItem> = emptyList(),
        val subscriptions: List<SubscriptionItem> = emptyList(),
        val selectedId: String? = null,
    )

    private var loaded = false

    init {
        load()
    }

    private fun load() {
        val raw = store.getString(KEY) ?: run { loaded = true; return }
        runCatching { json.decodeFromString(Snapshot.serializer(), raw) }.getOrNull()?.let {
            restore(it.profiles, it.subscriptions, it.selectedId)
        }
        loaded = true
    }

    override fun onChanged() {
        if (!loaded) return
        val snapshot = Snapshot(snapshotProfiles(), snapshotSubscriptions(), snapshotSelected())
        store.putString(KEY, json.encodeToString(Snapshot.serializer(), snapshot))
    }

    companion object {
        private const val KEY = "v2ray_compose_state_v1"
    }
}
