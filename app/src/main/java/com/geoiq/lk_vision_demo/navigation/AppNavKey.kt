package com.geoiq.lk_vision_demo.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AppNavKey : NavKey

@Serializable
data object SdkInteraction : AppNavKey

@Serializable
data object Chat : AppNavKey

@Serializable
data object Settings : AppNavKey
