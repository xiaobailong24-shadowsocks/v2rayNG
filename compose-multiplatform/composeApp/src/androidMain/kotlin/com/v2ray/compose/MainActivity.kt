package com.v2ray.compose

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.v2ray.compose.core.presentation.AppViewModel
import com.v2ray.compose.core.repository.StoredProfileRepository
import com.v2ray.compose.core.storage.KeyValueStore
import com.v2ray.compose.ui.App
import com.v2ray.compose.vpn.AndroidVpnController

class MainActivity : ComponentActivity() {

    private val viewModel by lazy {
        AppViewModel(
            repository = StoredProfileRepository(KeyValueStore()),
            vpn = AndroidVpnController(applicationContext),
            scope = lifecycleScope,
        )
    }

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result handled implicitly: once granted the service can establish the tunnel.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestVpnPermissionIfNeeded()
        setContent { App(viewModel) }
    }

    /** Ask for the system VPN consent up-front so later connects start silently. */
    private fun requestVpnPermissionIfNeeded() {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null) vpnConsent.launch(intent)
    }
}
