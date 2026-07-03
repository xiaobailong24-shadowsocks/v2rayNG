package com.v2ray.compose.core.repository

import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.model.SubscriptionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Non-persistent [ProfileRepository]. Used by unit tests and the desktop preview. */
open class InMemoryProfileRepository : ProfileRepository {

    private val _profiles = MutableStateFlow<List<ProfileItem>>(emptyList())
    private val _subscriptions = MutableStateFlow<List<SubscriptionItem>>(emptyList())
    private val _selectedId = MutableStateFlow<String?>(null)

    override val profiles: StateFlow<List<ProfileItem>> = _profiles.asStateFlow()
    override val subscriptions: StateFlow<List<SubscriptionItem>> = _subscriptions.asStateFlow()
    override val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    override fun upsert(profile: ProfileItem) {
        _profiles.value = _profiles.value.replaceOrAdd(profile)
        onChanged()
    }

    override fun upsertAll(items: List<ProfileItem>) {
        if (items.isEmpty()) return
        var list = _profiles.value
        for (item in items) list = list.replaceOrAdd(item)
        _profiles.value = list
        onChanged()
    }

    override fun delete(id: String) {
        _profiles.value = _profiles.value.filterNot { it.id == id }
        if (_selectedId.value == id) _selectedId.value = _profiles.value.firstOrNull()?.id
        onChanged()
    }

    override fun deleteBySubscription(subscriptionId: String) {
        _profiles.value = _profiles.value.filterNot { it.subscriptionId == subscriptionId }
        if (_profiles.value.none { it.id == _selectedId.value }) {
            _selectedId.value = _profiles.value.firstOrNull()?.id
        }
        onChanged()
    }

    override fun get(id: String): ProfileItem? = _profiles.value.firstOrNull { it.id == id }

    override fun select(id: String?) {
        if (id == null || _profiles.value.any { it.id == id }) {
            _selectedId.value = id
            onChanged()
        }
    }

    override fun selected(): ProfileItem? = _selectedId.value?.let { get(it) }

    override fun clear() {
        _profiles.value = emptyList()
        _subscriptions.value = emptyList()
        _selectedId.value = null
        onChanged()
    }

    override fun upsertSubscription(sub: SubscriptionItem) {
        val existing = _subscriptions.value
        _subscriptions.value = if (existing.any { it.id == sub.id }) {
            existing.map { if (it.id == sub.id) sub else it }
        } else existing + sub
        onChanged()
    }

    override fun deleteSubscription(id: String) {
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        onChanged()
    }

    override fun getSubscription(id: String): SubscriptionItem? =
        _subscriptions.value.firstOrNull { it.id == id }

    /** Hook for persistent subclasses to flush state. No-op in memory. */
    protected open fun onChanged() {}

    protected fun snapshotProfiles(): List<ProfileItem> = _profiles.value
    protected fun snapshotSubscriptions(): List<SubscriptionItem> = _subscriptions.value
    protected fun snapshotSelected(): String? = _selectedId.value

    protected fun restore(
        profiles: List<ProfileItem>,
        subscriptions: List<SubscriptionItem>,
        selectedId: String?,
    ) {
        _profiles.value = profiles
        _subscriptions.value = subscriptions
        _selectedId.value = selectedId
    }

    private fun List<ProfileItem>.replaceOrAdd(item: ProfileItem): List<ProfileItem> =
        if (any { it.id == item.id }) map { if (it.id == item.id) item else it } else this + item
}
