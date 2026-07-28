package com.geoiq.geoiq_android_lk_vision_bot_sdk.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.geoiq.geoiq_android_lk_vision_bot_sdk.ChatScreen
import com.geoiq.geoiq_android_lk_vision_bot_sdk.SDKInteractionScreen
import com.geoiq.geoiq_android_lk_vision_bot_sdk.setting.SettingsScreen

@Composable
fun GeoVisionNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SdkInteraction)
    val current = backStack.lastOrNull() as? GeoVisionNavKey

    val openSettings = { if (backStack.lastOrNull() != Settings) backStack.add(Settings) }
    val goBack = { backStack.removeLastOrNull(); Unit }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (current.isTopLevel()) {
                GeoVisionBottomBar(
                    current = current,
                    onSelect = { backStack.switchTopLevelTo(it) },
                )
            }
        },
        // Destinations own their own Scaffold; without this the status bar inset is applied twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<SdkInteraction> { SDKInteractionScreen(onOpenSettings = openSettings) }
                entry<Chat> { ChatScreen(onOpenSettings = openSettings) }
                entry<Settings> { SettingsScreen(onBack = goBack) }
            }
        )
    }
}

private fun NavBackStack<NavKey>.switchTopLevelTo(key: GeoVisionNavKey) {
    if (lastOrNull() == key) return
    while (size > 1) removeAt(size - 1)
    // Must never go empty — NavDisplay crashes on an empty back stack.
    if (isEmpty()) add(key) else set(0, key)
}
