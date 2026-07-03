package com.v2ray.compose.core

import com.v2ray.compose.core.config.ConfigParser
import com.v2ray.compose.core.model.SubscriptionItem
import com.v2ray.compose.core.repository.InMemoryProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {

    private fun profile(remark: String, sub: String = "") =
        ConfigParser.parse("vless://u@h.com:443?type=tcp#$remark")!!.copy(subscriptionId = sub)

    @Test
    fun upsert_and_select() {
        val repo = InMemoryProfileRepository()
        val a = profile("A")
        val b = profile("B")
        repo.upsert(a)
        repo.upsert(b)
        assertEquals(2, repo.profiles.value.size)
        repo.select(a.id)
        assertEquals(a.id, repo.selectedId.value)
        assertEquals("A", repo.selected()?.remarks)
    }

    @Test
    fun upsert_replaces_existing_by_id() {
        val repo = InMemoryProfileRepository()
        val a = profile("A")
        repo.upsert(a)
        repo.upsert(a.copy(remarks = "A2"))
        assertEquals(1, repo.profiles.value.size)
        assertEquals("A2", repo.profiles.value.first().remarks)
    }

    @Test
    fun delete_reselects() {
        val repo = InMemoryProfileRepository()
        val a = profile("A")
        val b = profile("B")
        repo.upsertAll(listOf(a, b))
        repo.select(a.id)
        repo.delete(a.id)
        assertEquals(1, repo.profiles.value.size)
        // selection falls back to a remaining profile
        assertEquals(b.id, repo.selectedId.value)
    }

    @Test
    fun delete_by_subscription() {
        val repo = InMemoryProfileRepository()
        repo.upsertAll(listOf(profile("A", "sub1"), profile("B", "sub1"), profile("C", "sub2")))
        repo.deleteBySubscription("sub1")
        assertEquals(1, repo.profiles.value.size)
        assertEquals("C", repo.profiles.value.first().remarks)
    }

    @Test
    fun subscription_crud() {
        val repo = InMemoryProfileRepository()
        val sub = SubscriptionItem(id = "s1", remarks = "Sub", url = "https://x")
        repo.upsertSubscription(sub)
        assertEquals(1, repo.subscriptions.value.size)
        repo.upsertSubscription(sub.copy(remarks = "Sub2"))
        assertEquals("Sub2", repo.getSubscription("s1")?.remarks)
        repo.deleteSubscription("s1")
        assertTrue(repo.subscriptions.value.isEmpty())
    }

    @Test
    fun clear_resets_selection() {
        val repo = InMemoryProfileRepository()
        repo.upsert(profile("A"))
        repo.select(repo.profiles.value.first().id)
        repo.clear()
        assertTrue(repo.profiles.value.isEmpty())
        assertNull(repo.selectedId.value)
    }
}
