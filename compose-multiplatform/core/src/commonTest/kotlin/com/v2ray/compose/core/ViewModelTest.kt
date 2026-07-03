package com.v2ray.compose.core

import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.presentation.AppViewModel
import com.v2ray.compose.core.repository.InMemoryProfileRepository
import com.v2ray.compose.core.vpn.NoopVpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ViewModelTest {

    private fun vm(): Pair<AppViewModel, InMemoryProfileRepository> {
        val repo = InMemoryProfileRepository()
        val model = AppViewModel(repo, NoopVpnController(), CoroutineScope(Dispatchers.Unconfined))
        return model to repo
    }

    @Test
    fun import_multiple_links() {
        val (model, _) = vm()
        val text = """
            vless://u1@a.com:443?type=tcp#A
            trojan://pw@b.com:443#B
        """.trimIndent()
        val count = model.importLinks(text)
        assertEquals(2, count)
        assertEquals(2, model.uiState.value.profiles.size)
        // first imported becomes selected automatically
        assertNotNull(model.uiState.value.selectedId)
    }

    @Test
    fun import_rejects_garbage() {
        val (model, _) = vm()
        assertEquals(0, model.importLinks("this is not a link"))
        assertTrue(model.uiState.value.profiles.isEmpty())
    }

    @Test
    fun toggle_connection_connects_and_disconnects() {
        val (model, _) = vm()
        model.importLinks("vless://u@h.com:443?type=tcp#S")
        assertEquals(ConnectionStatus.DISCONNECTED, model.uiState.value.connection.status)

        model.toggleConnection()
        assertEquals(ConnectionStatus.CONNECTED, model.uiState.value.connection.status)
        assertEquals(model.uiState.value.selectedId, model.uiState.value.connection.activeProfileId)

        model.toggleConnection()
        assertEquals(ConnectionStatus.DISCONNECTED, model.uiState.value.connection.status)
    }

    @Test
    fun toggle_without_server_reports_message() {
        val (model, _) = vm()
        model.toggleConnection()
        assertEquals(ConnectionStatus.DISCONNECTED, model.uiState.value.connection.status)
        assertEquals("Add a server first", model.uiState.value.transientMessage)
    }

    @Test
    fun save_and_share_roundtrip() {
        val (model, _) = vm()
        val template = model.newProfileTemplate(EConfigType.TROJAN).copy(
            server = "t.com", serverPort = "443", password = "pw", remarks = "T",
        )
        model.saveProfile(template)
        val link = model.shareLink(template.id)
        assertNotNull(link)
        assertTrue(link.startsWith("trojan://"))
    }

    @Test
    fun subscription_save_and_delete() {
        val (model, repo) = vm()
        model.saveSubscription(
            com.v2ray.compose.core.model.SubscriptionItem(id = "", remarks = "S", url = "https://x"),
        )
        assertEquals(1, repo.subscriptions.value.size)
        val id = repo.subscriptions.value.first().id
        assertTrue(id.isNotEmpty())
        model.deleteSubscription(id, alsoProfiles = true)
        assertTrue(repo.subscriptions.value.isEmpty())
    }
}
