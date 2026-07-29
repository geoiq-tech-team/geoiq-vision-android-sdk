package com.geoiq.lk_vision_demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geoiq.geoiq_android_lk_vision_bot_sdk.CameraPosition
import com.geoiq.geoiq_android_lk_vision_bot_sdk.ConnectionQuality
import com.geoiq.geoiq_android_lk_vision_bot_sdk.DataPublishReliability
import com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionEvent
import com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionRoomOptions
import com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionRoomState
import com.geoiq.geoiq_android_lk_vision_bot_sdk.LocalParticipant
import com.geoiq.geoiq_android_lk_vision_bot_sdk.LocalVideoTrack
import com.geoiq.geoiq_android_lk_vision_bot_sdk.LocalVideoTrackOptions
import com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager
import com.geoiq.lk_vision_demo.data.ConfigStore
import com.geoiq.lk_vision_demo.data.SessionToken
import com.geoiq.lk_vision_demo.data.TokenClient
import com.geoiq.lk_vision_demo.data.copyUriToCache
import io.livekit.android.room.datastream.StreamTextOptions
import io.livekit.android.room.track.Track
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val CHAT_TOPIC = "lk_va_publish"
        const val FILE_TOPIC = "send-file"
        const val BOT_CONNECTED_TOPIC = "vinay_bot_connected"
        const val AGENT_STATE_ATTRIBUTE = "lk.agent.state"
        const val MAX_EVENT_LOG_ENTRIES = 200
        const val DISCONNECT_AWAIT_TIMEOUT_MS = 1500L
    }

    private val configStore = ConfigStore(application)

    private val _state = MutableStateFlow(
        MainUiState(
            apiKey = configStore.apiKey,
            livekitUrl = configStore.livekitUrl,
            tokenUrl = configStore.tokenUrl,
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MainEffect>(extraBufferCapacity = 10)
    val effects = _effects.asSharedFlow()

    init {
        restoreOngoingSession()
        observeSdkEvents()
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.Connect -> connect(intent.mode)
            MainIntent.Disconnect -> VisionBotSDKManager.disconnectFromGeoVisionRoom()
            is MainIntent.Handover -> handover(intent.target)
            MainIntent.ToggleCamera -> toggleCamera()
            MainIntent.ToggleMicrophone -> toggleMicrophone()
            MainIntent.FlipCamera -> flipCamera()
            MainIntent.ClearEventLog -> _state.update { it.copy(eventLog = emptyList()) }
            MainIntent.ResetConfig -> resetConfig()
            is MainIntent.SendFile -> sendFile(intent)
            is MainIntent.SendChatMessage -> sendChatMessage(intent.text)
            is MainIntent.SaveConfig -> saveConfig(intent)
        }
    }

    private fun connect(mode: SessionMode) {
        viewModelScope.launch {
            _state.update { it.copy(activeMode = mode) }
            val current = _state.value
            val token = TokenClient.fetch(current.tokenUrl, current.apiKey)
            if (token == null) {
                log("Failed to fetch token")
                return@launch
            }
            log("Connecting (${mode.name}) to ${token.roomName} as ${token.identity}")
            VisionBotSDKManager.connectToGeoVisionRoom(
                context = getApplication(),
                socketUrl = current.livekitUrl,
                accessToken = token.accessToken,
                roomOptions = GeoVisionRoomOptions(
                    videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT)
                ),
            )
        }
    }

    private fun handover(target: SessionMode) {
        if (!_state.value.isConnected && !_state.value.isConnecting) {
            viewModelScope.launch { _effects.emit(MainEffect.HandoverReady(target)) }
            return
        }
        viewModelScope.launch {
            log("Handover → ${target.name}: disconnecting current session")
            VisionBotSDKManager.disconnectFromGeoVisionRoom()
            val disconnected = withTimeoutOrNull(DISCONNECT_AWAIT_TIMEOUT_MS.milliseconds) {
                VisionBotSDKManager.events.first { it is GeoVisionEvent.Disconnected }
            }
            if (disconnected == null) {
                log("Handover: disconnect timed out, forcing cleanup")
                VisionBotSDKManager.releaseRoomResources()
            }
            _effects.emit(MainEffect.HandoverReady(target))
            log("Handover → ${target.name}: reconnecting")
            connect(target)
        }
    }

    private fun toggleCamera() {
        viewModelScope.launch {
            val enable = !VisionBotSDKManager.isCameraEnabled()
            if (VisionBotSDKManager.setCameraEnabled(enable)) {
                _state.update { it.copy(isCameraEnabled = enable) }
            }
        }
    }

    private fun toggleMicrophone() {
        viewModelScope.launch {
            val enable = !VisionBotSDKManager.isMicrophoneEnabled()
            if (VisionBotSDKManager.setMicrophoneEnabled(enable)) {
                _state.update { it.copy(isMicrophoneEnabled = enable) }
            }
        }
    }

    private fun flipCamera() {
        viewModelScope.launch {
            _state.update { it.copy(isFlippingCamera = true) }
            try {
                if (VisionBotSDKManager.flipCameraPosition()) {
                    val track = VisionBotSDKManager.getLocalParticipant()?.getOrCreateDefaultVideoTrack()
                    _effects.emit(
                        MainEffect.CameraFlipped(track?.options?.position == CameraPosition.FRONT)
                    )
                    log("Camera flipped")
                } else {
                    log("Failed to flip camera")
                }
            } finally {
                _state.update { it.copy(isFlippingCamera = false) }
            }
        }
    }

    private fun sendFile(intent: MainIntent.SendFile) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val file = app.copyUriToCache(intent.uri) ?: run {
                log("Failed to read selected file")
                return@launch
            }
            log("Sending: ${file.name}")
            val sent = VisionBotSDKManager.sendFile(app, file, FILE_TOPIC)
            log(if (sent) "Sent: ${file.name}" else "Send failed: ${file.name}")
            file.delete()
        }
    }

    private fun sendChatMessage(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            val localParticipant = VisionBotSDKManager.getLocalParticipant() ?: run {
                log("Cannot send chat: not connected")
                return@launch
            }
            addChatMessage(message, isLocal = true, sender = "You")
            val result = localParticipant.sendText(
                message,
                StreamTextOptions(topic = CHAT_TOPIC),
            )
            if (result.isFailure) {
                log("Chat send failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun saveConfig(intent: MainIntent.SaveConfig) {
        configStore.save(intent.livekitUrl, intent.apiKey, intent.tokenUrl)
        _state.update {
            it.copy(
                livekitUrl = intent.livekitUrl,
                apiKey = intent.apiKey,
                tokenUrl = intent.tokenUrl,
            )
        }
        log("Config saved")
    }

    private fun resetConfig() {
        configStore.clear()
        _state.update {
            it.copy(
                livekitUrl = BuildConfig.BASE_URL,
                apiKey = BuildConfig.API_KEY,
                tokenUrl = BuildConfig.TOKEN_URL,
            )
        }
    }

    private fun observeSdkEvents() {
        viewModelScope.launch {
            VisionBotSDKManager.events.collect(::reduce)
        }
    }

    private fun reduce(event: GeoVisionEvent) {
        when (event) {
            is GeoVisionEvent.Connecting -> {
                log("Connecting to ${event.url}")
                _state.update {
                    it.copy(phase = ConnectionPhase.Connecting, connectionStatus = "Connecting...")
                }
            }

            is GeoVisionEvent.Connected -> {
                log("Connected: ${event.roomName} as ${event.localParticipant.identity}")
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Connected,
                        connectionStatus = "Connected: ${event.roomName}",
                        isCameraEnabled = VisionBotSDKManager.isCameraEnabled(),
                        isMicrophoneEnabled = VisionBotSDKManager.isMicrophoneEnabled(),
                    )
                }
                viewModelScope.launch {
                    when (_state.value.activeMode) {
                        SessionMode.Video -> {
                            VisionBotSDKManager.setCameraEnabled(true)
                            VisionBotSDKManager.setMicrophoneEnabled(true)
                        }
                        SessionMode.Chat -> {
                            VisionBotSDKManager.setCameraEnabled(false)
                            VisionBotSDKManager.setMicrophoneEnabled(false)
                        }
                    }
                    _state.update {
                        it.copy(
                            isCameraEnabled = VisionBotSDKManager.isCameraEnabled(),
                            isMicrophoneEnabled = VisionBotSDKManager.isMicrophoneEnabled(),
                        )
                    }
                }
            }

            is GeoVisionEvent.Disconnected -> {
                log("Disconnected: ${event.reason ?: "Client initiated"}")
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Disconnected,
                        connectionStatus = "Disconnected",
                        connectionQuality = ConnectionQuality.UNKNOWN,
                        isCameraEnabled = false,
                        isMicrophoneEnabled = false,
                        isSpeaking = false,
                        agentState = "",
                        localVideoTrack = null,
                        rendererSession = it.rendererSession + 1,
                        chatMessages = emptyList(),
                    )
                }
            }

            is GeoVisionEvent.ParticipantJoined -> {
                log("Participant joined: ${event.participant.identity}")
                announceBotConnected()
            }

            is GeoVisionEvent.ParticipantLeft ->
                log("Participant left: ${event.participant.identity}")

            is GeoVisionEvent.ParticipantAttributesChanged -> {
                event.changedAttributes[AGENT_STATE_ATTRIBUTE]?.let { agentState ->
                    _state.update { it.copy(agentState = agentState) }
                    log("Agent state: $agentState")
                }
            }

            is GeoVisionEvent.LocalTrackSubscribed -> {
                log("Local track live: ${event.publication.source.name}")
                if (_state.value.activeMode == SessionMode.Video) {
                    when (event.publication.source?.name?.lowercase()) {
                        "camera" -> {
                            val track = VisionBotSDKManager.getLocalParticipant()
                                ?.getTrackPublication(event.publication.source)?.track
                            _state.update {
                                it.copy(
                                    isCameraEnabled = true,
                                    localVideoTrack = track as? LocalVideoTrack ?: it.localVideoTrack,
                                )
                            }
                        }
                        "microphone" -> _state.update { it.copy(isMicrophoneEnabled = true) }
                    }
                }
            }

            is GeoVisionEvent.TrackPublished ->
                log("Track published: ${event.publication.source}")

            is GeoVisionEvent.TrackUnpublished -> {
                log("Track unpublished: ${event.publication.source}")
                when (event.publication.source.name.lowercase()) {
                    "camera" -> _state.update {
                        it.copy(isCameraEnabled = false, localVideoTrack = null)
                    }
                    "microphone" -> _state.update { it.copy(isMicrophoneEnabled = false) }
                }
            }

            is GeoVisionEvent.TrackSubscribed ->
                log("Remote track: ${event.track.name} from ${event.participant.identity}")

            is GeoVisionEvent.TrackUnsubscribed ->
                log("Track unsubscribed: ${event.track.name}")

            is GeoVisionEvent.ConnectionQualityChanged -> {
                if (event.participant is LocalParticipant) {
                    _state.update { it.copy(connectionQuality = event.quality) }
                    log("Quality: ${event.quality}")
                }
            }

            is GeoVisionEvent.ActiveSpeakersChanged ->
                _state.update { it.copy(isSpeaking = VisionBotSDKManager.getIsSpeaking()) }

            is GeoVisionEvent.TranscriptionReceived -> {
                if (event.isFinal) {
                    log("Transcript: ${event.message}")
                    addChatMessage(event.message, isLocal = false, sender = "Agent")
                }
            }

            is GeoVisionEvent.CustomMessageReceived -> {
                log("[${event.topic}] ${event.message}")
                addChatMessage(event.message, isLocal = false, sender = event.topic)
            }

            is GeoVisionEvent.Error -> {
                log("ERROR: ${event.message}")
                _state.update {
                    if (it.isConnected) it
                    else it.copy(phase = ConnectionPhase.Error, connectionStatus = "Error")
                }
            }
        }
    }

    private fun announceBotConnected() {
        viewModelScope.launch {
            try {
                VisionBotSDKManager.getLocalParticipant()?.publishData(
                    "BOT_CONNECTED".toByteArray(Charsets.UTF_8),
                    DataPublishReliability.RELIABLE,
                    BOT_CONNECTED_TOPIC,
                )
            } catch (e: Exception) {
                log("BOT_CONNECTED publish failed: ${e.message}")
            }
        }
    }

    private fun restoreOngoingSession() {
        val room = VisionBotSDKManager.currentRoom ?: return
        if (room.state != GeoVisionRoomState.CONNECTED) return

        val localParticipant = VisionBotSDKManager.getLocalParticipant()
        val cameraTrack = localParticipant
            ?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        _state.update {
            it.copy(
                phase = ConnectionPhase.Connected,
                connectionStatus = "Connected: ${room.name ?: "Unknown Room"}",
                connectionQuality = localParticipant?.connectionQuality ?: ConnectionQuality.UNKNOWN,
                isCameraEnabled = VisionBotSDKManager.isCameraEnabled(),
                isMicrophoneEnabled = VisionBotSDKManager.isMicrophoneEnabled(),
                isSpeaking = VisionBotSDKManager.getIsSpeaking(),
                agentState = localParticipant?.attributes?.get(AGENT_STATE_ATTRIBUTE) ?: "",
                localVideoTrack = cameraTrack,
            )
        }
    }

    private fun addChatMessage(text: String?, isLocal: Boolean, sender: String?) {
        if (text.isNullOrBlank()) return
        val message = ChatMessage(
            text = text,
            isLocal = isLocal,
            sender = sender,
            timestamp = timestamp(),
        )
        _state.update { it.copy(chatMessages = it.chatMessages + message) }
    }

    private fun log(message: String) {
        _state.update {
            it.copy(eventLog = (listOf("[${timestamp()}] $message") + it.eventLog).take(MAX_EVENT_LOG_ENTRIES))
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
