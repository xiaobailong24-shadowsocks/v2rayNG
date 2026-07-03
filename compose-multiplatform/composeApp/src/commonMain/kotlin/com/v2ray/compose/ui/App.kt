package com.v2ray.compose.ui

import com.v2ray.compose.core.presentation.AppViewModel

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import com.v2ray.compose.ui.screens.EditProfileScreen
import com.v2ray.compose.ui.screens.HomeScreen
import com.v2ray.compose.ui.screens.SettingsScreen
import com.v2ray.compose.ui.screens.SubscriptionScreen
import com.v2ray.compose.ui.theme.V2rayComposeTheme

/** Simple, dependency-free navigation model. */
sealed interface Screen {
    data object Home : Screen
    data class Edit(val profileId: String?) : Screen
    data object Subscriptions : Screen
    data object Settings : Screen
}

@Composable
fun App(viewModel: AppViewModel) {
    V2rayComposeTheme {
        val state by viewModel.uiState.collectAsState()
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(state.transientMessage) {
            state.transientMessage?.let {
                snackbar.showSnackbar(it)
                viewModel.consumeMessage()
            }
        }

        AnimatedContent(targetState = screen, label = "screen") { current ->
            when (current) {
                is Screen.Home -> HomeScreen(
                    state = state,
                    snackbar = snackbar,
                    onToggleConnection = viewModel::toggleConnection,
                    onSelect = viewModel::selectProfile,
                    onDelete = viewModel::deleteProfile,
                    onImport = viewModel::importLinks,
                    onShare = viewModel::shareLink,
                    onAdd = { screen = Screen.Edit(null) },
                    onEdit = { screen = Screen.Edit(it) },
                    onOpenSubscriptions = { screen = Screen.Subscriptions },
                    onOpenSettings = { screen = Screen.Settings },
                )

                is Screen.Edit -> EditProfileScreen(
                    existing = current.profileId?.let { id -> state.profiles.firstOrNull { it.id == id } },
                    templateFactory = viewModel::newProfileTemplate,
                    onSave = { viewModel.saveProfile(it); screen = Screen.Home },
                    onBack = { screen = Screen.Home },
                )

                is Screen.Subscriptions -> SubscriptionScreen(
                    subscriptions = state.subscriptions,
                    onSave = viewModel::saveSubscription,
                    onDelete = viewModel::deleteSubscription,
                    onUpdate = viewModel::updateSubscription,
                    onBack = { screen = Screen.Home },
                )

                is Screen.Settings -> SettingsScreen(
                    serverCount = state.profiles.size,
                    onBack = { screen = Screen.Home },
                )
            }
        }
    }
}
