package com.geoiq.lk_vision_demo

import android.net.Uri
import com.geoiq.geoiq_android_lk_vision_bot_sdk.ConnectionQuality
import com.geoiq.geoiq_android_lk_vision_bot_sdk.LocalVideoTrack
import com.geoiq.lk_vision_demo.data.AgentType
import com.geoiq.lk_vision_demo.data.GeoEnv
import com.geoiq.lk_vision_demo.data.GeoProtocol
import com.geoiq.lk_vision_demo.data.LocaleConfigItem

data class ChatMessage(
    val text: String,
    val isLocal: Boolean,
    val sender: String?,
    val timestamp: String,
)

enum class ConnectionPhase { Disconnected, Connecting, Connected, Reconnecting, Error }

/**
 * Which deployment a single mode connects to.
 *
 * Production is fixed per mode; staging is editable so any staging host can be targeted. The key
 * travels with the socket rather than being configured separately — that pairing is what stops a
 * host being used with another host's key.
 */
data class ModeEnvConfig(
    val useProd: Boolean,
    val stagingSocketUrl: String = GeoEnv.DEFAULT_STAGING_SOCKET,
    val stagingApiKey: String = GeoEnv.DEFAULT_STAGING_API_KEY,
) {
    fun isDefaultFor(mode: SessionMode): Boolean =
        useProd == GeoEnv.isProdConfigured(mode) &&
            stagingSocketUrl == GeoEnv.DEFAULT_STAGING_SOCKET &&
            stagingApiKey == GeoEnv.DEFAULT_STAGING_API_KEY
}

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
    val localeConfig: List<LocaleConfigItem> = emptyList(),
    val selectedLanguageCode: String? = null,
    val voiceEnv: ModeEnvConfig = ModeEnvConfig(useProd = GeoEnv.isProdConfigured(SessionMode.Video)),
    val chatEnv: ModeEnvConfig = ModeEnvConfig(useProd = GeoEnv.isProdConfigured(SessionMode.Chat)),
) {
    val isConnected: Boolean get() = phase == ConnectionPhase.Connected
    val isConnecting: Boolean get() = phase == ConnectionPhase.Connecting
    val isReconnecting: Boolean get() = phase == ConnectionPhase.Reconnecting

    /** True whenever a room is live or being (re)established — i.e. Disconnect is meaningful. */
    val hasActiveSession: Boolean get() = isConnected || isConnecting || isReconnecting

    fun envFor(mode: SessionMode): ModeEnvConfig = when (mode) {
        SessionMode.Video -> voiceEnv
        SessionMode.Chat -> chatEnv
    }

    fun socketUrlFor(mode: SessionMode): String = envFor(mode).let { env ->
        if (env.useProd) GeoEnv.prodSocketUrl(mode) else env.stagingSocketUrl
    }

    /** Always the key that pairs with [socketUrlFor]'s result. */
    fun apiKeyFor(mode: SessionMode): String = envFor(mode).let { env ->
        if (env.useProd) GeoEnv.prodApiKey(mode) else env.stagingApiKey
    }

    val isConfigModified: Boolean
        get() = !voiceEnv.isDefaultFor(SessionMode.Video) || !chatEnv.isDefaultFor(SessionMode.Chat)
}

enum class SessionMode { Video, Chat }

/**
 * Which backend agent this mode talks to. Sent as `agent_type` on the token request —
 * without it the backend defaults to the voice agent regardless of the UI mode.
 */
val SessionMode.agentType: AgentType
    get() = when (this) {
        SessionMode.Video -> AgentType.VOICE_ASSIST
        SessionMode.Chat -> AgentType.CHAT_ASSIST
    }

/** Entry-point event reported to the backend when a session starts outside a handover. */
val SessionMode.defaultSourceEvent: String
    get() = when (this) {
        SessionMode.Video -> GeoProtocol.VAEP_LENS_PACKAGE
        SessionMode.Chat -> GeoProtocol.VAEP_PDP_CHAT
    }

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

    /** Switches a mode between its production and staging deployment. */
    data class SelectEnvSource(val mode: SessionMode, val useProd: Boolean) : MainIntent

    /** Persists an edited staging socket + key pair. Applies on that mode's next connect. */
    data class SaveStagingConfig(
        val mode: SessionMode,
        val socketUrl: String,
        val apiKey: String,
    ) : MainIntent
}

sealed interface MainEffect {
    data class CameraFlipped(val isFrontCamera: Boolean) : MainEffect
    data class HandoverReady(val target: SessionMode) : MainEffect
}
