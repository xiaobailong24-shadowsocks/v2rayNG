package com.v2ray.compose

import androidx.compose.ui.window.ComposeUIViewController
import com.v2ray.compose.core.presentation.AppViewModel
import com.v2ray.compose.core.repository.StoredProfileRepository
import com.v2ray.compose.core.storage.KeyValueStore
import com.v2ray.compose.ui.App
import com.v2ray.compose.vpn.IosVpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.UIKit.UIViewController

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private val viewModel: AppViewModel by lazy {
    AppViewModel(
        repository = StoredProfileRepository(KeyValueStore()),
        vpn = IosVpnController(),
        scope = appScope,
    )
}

/** Entry point consumed by the SwiftUI `ComposeView` in the iosApp Xcode project. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(viewModel)
}
