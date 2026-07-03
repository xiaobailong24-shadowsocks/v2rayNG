package com.v2ray.compose.core.repository

import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.model.SubscriptionItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Storage abstraction for server profiles, subscriptions and the currently
 * selected server. Implementations back this with in-memory state (tests /
 * desktop preview) or platform key-value storage.
 */
interface ProfileRepository {
    val profiles: StateFlow<List<ProfileItem>>
    val subscriptions: StateFlow<List<SubscriptionItem>>
    val selectedId: StateFlow<String?>

    fun upsert(profile: ProfileItem)
    fun upsertAll(items: List<ProfileItem>)
    fun delete(id: String)
    fun deleteBySubscription(subscriptionId: String)
    fun get(id: String): ProfileItem?
    fun select(id: String?)
    fun selected(): ProfileItem?
    fun clear()

    fun upsertSubscription(sub: SubscriptionItem)
    fun deleteSubscription(id: String)
    fun getSubscription(id: String): SubscriptionItem?
}
