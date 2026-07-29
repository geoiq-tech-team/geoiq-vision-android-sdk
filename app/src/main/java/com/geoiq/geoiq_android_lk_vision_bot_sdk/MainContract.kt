package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.net.Uri

data class ChatMessage(
    val text: String,
    val isLocal: Boolean,
    val sender: String?,
    val timestamp: String,
)

enum class ConnectionPhase { Disconnected, Connecting, Connected, Error }

data class MainUiState(
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    val connectionStatus: String = "Disconnected",
    val connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN,
    val isCameraEnabled: Boolean = false,
    val isMicrophoneEnabled: Boolean = false,
    val isSpeaking: Boolean = false,
    val isFlippingCamera: Boolean = false,
    val agentState: String = "",
    val localVideoTrack: LocalVideoTrack? = null,
    val activeMode: SessionMode = SessionMode.Video,
    val rendererSession: Int = 0,
    val eventLog: List<String> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val apiKey: String = BuildConfig.API_KEY,
    val livekitUrl: String = BuildConfig.BASE_URL,
    val tokenUrl: String = BuildConfig.TOKEN_URL,
) {
    val isConnected: Boolean get() = phase == ConnectionPhase.Connected
    val isConnecting: Boolean get() = phase == ConnectionPhase.Connecting

    val isConfigModified: Boolean
        get() = apiKey != BuildConfig.API_KEY ||
            livekitUrl != BuildConfig.BASE_URL ||
            tokenUrl != BuildConfig.TOKEN_URL
}

enum class SessionMode { Video, Chat }

sealed interface MainIntent {
    data class Connect(val mode: SessionMode) : MainIntent
    data object Disconnect : MainIntent
    data class Handover(val target: SessionMode) : MainIntent
    data object ToggleCamera : MainIntent
    data object ToggleMicrophone : MainIntent
    data object FlipCamera : MainIntent
    data object ClearEventLog : MainIntent
    data object ResetConfig : MainIntent
    data class SendFile(val uri: Uri) : MainIntent
    data class SendChatMessage(val text: String) : MainIntent
    data class SaveConfig(
        val livekitUrl: String,
        val apiKey: String,
        val tokenUrl: String,
    ) : MainIntent
}

sealed interface MainEffect {
    data class CameraFlipped(val isFrontCamera: Boolean) : MainEffect
    data class HandoverReady(val target: SessionMode) : MainEffect
}
