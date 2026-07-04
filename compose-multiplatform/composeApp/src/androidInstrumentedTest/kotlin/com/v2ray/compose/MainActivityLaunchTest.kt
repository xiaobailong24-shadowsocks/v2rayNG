package com.v2ray.compose

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real entry-point [MainActivity] on the device. This drives the
 * whole Android startup path — [V2RayApplication] init, `AndroidVpnController`
 * construction, and `setContent { App(...) }` composing the *entire* shared
 * Compose UI on a real Android runtime — and asserts it reaches RESUMED without
 * crashing. If anything in the shared UI failed to compose on Android, the
 * activity would crash and this test would fail.
 *
 * The CI job pre-grants the VPN consent app-op so MainActivity's
 * `VpnService.prepare` returns null and no system consent dialog blocks RESUMED.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun launchesAndComposesSharedUi() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
