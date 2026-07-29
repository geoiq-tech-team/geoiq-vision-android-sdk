package com.geoiq.geoiq_android_lk_vision_bot_sdk.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AppNavKey : NavKey

@Serializable
data object SdkInteraction : AppNavKey

@Serializable
data object Chat : AppNavKey

@Serializable
data object Settings : AppNavKey
