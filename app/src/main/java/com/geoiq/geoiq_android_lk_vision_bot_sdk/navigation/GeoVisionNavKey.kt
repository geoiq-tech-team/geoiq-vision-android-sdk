package com.geoiq.geoiq_android_lk_vision_bot_sdk.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface GeoVisionNavKey : NavKey

@Serializable
data object SdkInteraction : GeoVisionNavKey

@Serializable
data object Chat : GeoVisionNavKey

@Serializable
data object Settings : GeoVisionNavKey
