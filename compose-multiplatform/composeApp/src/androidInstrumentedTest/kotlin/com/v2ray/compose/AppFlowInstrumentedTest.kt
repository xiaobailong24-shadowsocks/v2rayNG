package com.v2ray.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.compose.core.AndroidContext
import com.v2ray.compose.core.config.XrayConfigBuilder
import com.v2ray.compose.core.model.ConnectionStatus
import com.v2ray.compose.core.presentation.AppViewModel
import com.v2ray.compose.core.repository.StoredProfileRepository
import com.v2ray.compose.core.storage.KeyValueStore
import com.v2ray.compose.core.vpn.NoopVpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device end-to-end check of the Android build's real machinery — no mocks
 * for the parts that matter: the actual [StoredProfileRepository] backed by real
 * Android SharedPreferences, the real share-link [com.v2ray.compose.core.config.ConfigParser],
 * the real [XrayConfigBuilder], and the shared [AppViewModel]. Only the tunnel
 * transport is stubbed (a real Xray tunnel needs a live server), so this proves
 * everything up to "hand Xray-core its JSON" works on an Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowInstrumentedTest {

    private val vlessLink =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&type=tcp#Tokyo%20Node"

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidContext.init(ctx)
        // Start from a clean store so assertions are deterministic across reruns.
        ctx.getSharedPreferences("v2ray_compose", 0).edit().clear().commit()
    }

    @Test
    fun importPersistsAndDrivesConnectionState() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val repo = StoredProfileRepository(KeyValueStore())
        val vpn = NoopVpnController()
        val vm = AppViewModel(repo, vpn, scope)

        // 1) Empty to start.
        assertEquals(0, vm.uiState.value.profiles.size)

        // 2) Import a real share link — exercises ConfigParser on device.
        val count = vm.importLinks(vlessLink)
        assertEquals(1, count)
        val profile = vm.uiState.value.profiles.single()
        assertEquals("Tokyo Node", profile.remarks)
        assertEquals("example.com", profile.server)

        // 3) The real Xray config builder produces valid JSON for it.
        val json = XrayConfigBuilder.buildConfigJson(profile)
        assertTrue("Xray config should contain the outbound server", json.contains("example.com"))
        assertTrue("Xray config should declare a SOCKS inbound", json.contains("socks"))

        // 4) Connect toggle flows through the ViewModel to the controller.
        vm.selectProfile(profile.id)
        vm.toggleConnection()
        assertEquals(ConnectionStatus.CONNECTED, vm.uiState.value.connection.status)
        assertTrue(vm.isConnected)

        vm.toggleConnection()
        assertEquals(ConnectionStatus.DISCONNECTED, vm.uiState.value.connection.status)

        // 5) Persistence: a brand-new repository (fresh SharedPreferences read)
        //    still sees the imported profile — proves the Android storage actual.
        val reopened = StoredProfileRepository(KeyValueStore())
        val persisted = reopened.profiles.value.firstOrNull { it.id == profile.id }
        assertNotNull("Imported profile should survive a store reopen", persisted)
        assertEquals("Tokyo Node", persisted!!.remarks)
    }
}
