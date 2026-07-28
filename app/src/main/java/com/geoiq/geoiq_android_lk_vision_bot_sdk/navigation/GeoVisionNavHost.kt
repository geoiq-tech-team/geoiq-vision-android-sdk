package com.geoiq.geoiq_android_lk_vision_bot_sdk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.geoiq.geoiq_android_lk_vision_bot_sdk.SDKInteractionScreen

@Composable
fun GeoVisionNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SdkInteraction)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<SdkInteraction> { SDKInteractionScreen() }
        }
    )
}
