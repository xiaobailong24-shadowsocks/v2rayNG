package com.v2ray.compose.ui

import com.v2ray.compose.core.presentation.AppViewModel

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.v2ray.compose.core.repository.StoredProfileRepository
import com.v2ray.compose.core.storage.KeyValueStore
import com.v2ray.compose.core.vpn.NoopVpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun main() = application {
    val viewModel = remember {
        AppViewModel(
            repository = StoredProfileRepository(KeyValueStore()),
            vpn = NoopVpnController(),
            scope = appScope,
        )
    }

    val windowState = rememberWindowState(size = DpSize(420.dp, 760.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "v2rayNG · Compose",
        state = windowState,
    ) {
        App(viewModel)
    }
}
