package com.geoiq.geoiq_android_lk_vision_bot_sdk.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

private data class BottomBarDestination(
    val key: AppNavKey,
    val label: String,
    val icon: ImageVector,
)

private val BottomBarDestinations = listOf(
    BottomBarDestination(SdkInteraction, "Session", Icons.Filled.Videocam),
    BottomBarDestination(Chat, "Chat", Icons.AutoMirrored.Filled.Chat),
)

internal fun AppNavKey?.isTopLevel(): Boolean =
    BottomBarDestinations.any { it.key == this }

@Composable
fun AppBottomBar(
    current: AppNavKey?,
    onSelect: (AppNavKey) -> Unit,
) {
    NavigationBar {
        BottomBarDestinations.forEach { destination ->
            NavigationBarItem(
                selected = current == destination.key,
                onClick = { onSelect(destination.key) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
