package com.geoiq.geoiq_android_lk_vision_bot_sdk.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.geoiq.geoiq_android_lk_vision_bot_sdk.chat.ChatScreen
import com.geoiq.geoiq_android_lk_vision_bot_sdk.MainEffect
import com.geoiq.geoiq_android_lk_vision_bot_sdk.MainIntent
import com.geoiq.geoiq_android_lk_vision_bot_sdk.MainViewModel
import com.geoiq.geoiq_android_lk_vision_bot_sdk.voice.VoiceScreen
import com.geoiq.geoiq_android_lk_vision_bot_sdk.SessionMode
import com.geoiq.geoiq_android_lk_vision_bot_sdk.settings.SettingsScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
) {
    val backStack = rememberNavBackStack(SdkInteraction)
    val current = backStack.lastOrNull() as? AppNavKey
    val state by viewModel.state.collectAsStateWithLifecycle()

    val openSettings = { if (backStack.lastOrNull() != Settings) backStack.add(Settings) }
    val goBack = { backStack.removeLastOrNull(); Unit }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is MainEffect.HandoverReady) {
                val target = when (effect.target) {
                    SessionMode.Video -> SdkInteraction
                    SessionMode.Chat -> Chat
                }
                backStack.switchTopLevelTo(target)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (current.isTopLevel()) {
                AppBottomBar(
                    current = current,
                    onSelect = { key ->
                        if (key == current) return@AppBottomBar
                        val target = when (key) {
                            is Chat -> SessionMode.Chat
                            is SdkInteraction -> SessionMode.Video
                            else -> return@AppBottomBar
                        }
                        if (state.isConnected || state.isConnecting) {
                            viewModel.onIntent(MainIntent.Handover(target))
                        } else {
                            backStack.switchTopLevelTo(key)
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (current.isTopLevel()) {
                val canDisconnect = state.isConnected || state.isConnecting
                ExtendedFloatingActionButton(
                    onClick = {
                        if (canDisconnect) {
                            viewModel.onIntent(MainIntent.Disconnect)
                        } else {
                            val mode = when (current) {
                                is Chat -> SessionMode.Chat
                                else -> SessionMode.Video
                            }
                            viewModel.onIntent(MainIntent.Connect(mode))
                        }
                    },
                    containerColor = if (canDisconnect) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (canDisconnect) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting...")
                    } else if (state.isConnected) {
                        Icon(Icons.Default.CallEnd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Disconnect")
                    } else {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect")
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<SdkInteraction> { VoiceScreen(onOpenSettings = openSettings) }
                entry<Chat> { ChatScreen(onOpenSettings = openSettings) }
                entry<Settings> { SettingsScreen(onBack = goBack) }
            }
        )
    }
}

private fun NavBackStack<NavKey>.switchTopLevelTo(key: AppNavKey) {
    if (lastOrNull() == key) return
    while (size > 1) removeAt(size - 1)
    // Must never go empty — NavDisplay crashes on an empty back stack.
    if (isEmpty()) add(key) else set(0, key)
}
