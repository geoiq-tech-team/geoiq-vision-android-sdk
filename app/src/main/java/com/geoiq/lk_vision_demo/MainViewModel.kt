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
import com.geoiq.lk_vision_demo.data.GeoEnv
import com.geoiq.lk_vision_demo.data.GeoProtocol
import com.geoiq.lk_vision_demo.data.ServiceUpResult
import com.geoiq.lk_vision_demo.data.TokenClient
import com.geoiq.lk_vision_demo.data.copyUriToCache
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val BOT_CONNECTED_TOPIC = "vinay_bot_connected"
        const val MAX_EVENT_LOG_ENTRIES = 200
        const val DISCONNECT_AWAIT_TIMEOUT_MS = 1500L
        const val HANDOVER_SIGNAL_TIMEOUT_MS = 3000L
        const val MAX_RECOVERY_ATTEMPTS = 3
    }

    /** Why a disconnect we initiated happened, which decides what survives it. */
    private enum class TeardownReason {
        /** User tapped Disconnect, or the agent asked us to end the room. */
        UserInitiated,

        /** Switching agents. The next room is a different conversation. */
        Handover,

        /** Backend ended the session; the next room continues the same conversation. */
        SessionRenewal,
        ;

        /** A renewal keeps the transcript; the other two start fresh. */
        val endsConversation: Boolean get() = this != SessionRenewal
    }

    private val configStore = ConfigStore(application)

    /**
     * Session source of the live room, replayed on reconnect so the backend keeps treating
     * the session as the same funnel (including a handover it was created by).
     */
    private var lastSourceEvent: String? = null

    /** Guards the once-per-room bootstrap (session-info publish + RPC handler registration). */
    private var hasBootstrappedSession = false

    /**
     * Why we asked the SDK to disconnect, or null when the drop was not ours. Distinguishes a
     * teardown that ends the conversation from one that only renews the room.
     */
    private var pendingTeardownReason: TeardownReason? = null

    /** Invalidates in-flight reconnects when a newer connect/disconnect supersedes them. */
    private var reconnectFlowId = 0

    /**
     * Consecutive automatic recoveries since the last successful connect. Bounded so a room
     * that fails immediately on every attempt cannot hammer the token endpoint forever.
     */
    private var consecutiveRecoveryAttempts = 0

    private var handoverJob: Job? = null

    private val _state = MutableStateFlow(
        MainUiState(
            voiceEnv = configStore.envConfig(SessionMode.Video),
            chatEnv = configStore.envConfig(SessionMode.Chat),
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
            MainIntent.Disconnect -> disconnect()
            is MainIntent.Handover -> handover(intent.target)
            MainIntent.ToggleCamera -> toggleCamera()
            MainIntent.ToggleMicrophone -> toggleMicrophone()
            MainIntent.FlipCamera -> flipCamera()
            MainIntent.ClearEventLog -> _state.update { it.copy(eventLog = emptyList()) }
            MainIntent.ResetConfig -> resetConfig()
            is MainIntent.SendFile -> sendFile(intent)
            is MainIntent.SendChatMessage -> sendChatMessage(intent.text)
            is MainIntent.SelectEnvSource -> selectEnvSource(intent.mode, intent.useProd)
            is MainIntent.SaveStagingConfig ->
                saveStagingConfig(intent.mode, intent.socketUrl, intent.apiKey)
        }
    }

    private fun connect(mode: SessionMode, sourceEvent: String = mode.defaultSourceEvent) {
        reconnectFlowId += 1
        val flowId = reconnectFlowId
        viewModelScope.launch { runSessionBootstrap(mode, sourceEvent, flowId) }
    }

    /**
     * serviceup → token → connect room, matching the consumer app's ordering. The
     * service-up gate runs on reconnects too, so a backend that is refusing sessions
     * surfaces as an error instead of a failed WebRTC handshake.
     *
     * [flowId] drops the result if a newer connect/disconnect has since superseded this one.
     */
    private suspend fun runSessionBootstrap(mode: SessionMode, sourceEvent: String, flowId: Int) {
        _state.update {
            it.copy(
                activeMode = mode,
                phase = if (it.isReconnecting) ConnectionPhase.Reconnecting else ConnectionPhase.Connecting,
                connectionStatus = if (it.isReconnecting) "Reconnecting..." else "Connecting...",
            )
        }
        val current = _state.value

        // The deployment is chosen per mode: voice and chat are different agents on different
        // hosts. Both the API key and the token host follow from that socket.
        val socketUrl = current.socketUrlFor(mode)
        val apiKey = current.apiKeyFor(mode)
        val tokenBaseUrl = GeoEnv.tokenBaseUrl(socketUrl)
        if (apiKey.isBlank()) {
            onSessionError(
                "No API key for $socketUrl — set ${GeoEnv.missingKeyPropertyName(mode)} " +
                    "in local.properties, or switch to staging in Settings"
            )
            return
        }
        log("Session env (${mode.name}): socket=$socketUrl tokenHost=$tokenBaseUrl")

        when (val serviceUp = TokenClient.serviceUp(tokenBaseUrl, apiKey)) {
            is ServiceUpResult.Unavailable -> {
                onSessionError("Service unavailable (HTTP ${serviceUp.code})")
                return
            }
            ServiceUpResult.NetworkError -> {
                onSessionError("Network error")
                return
            }
            ServiceUpResult.Up -> Unit
        }
        if (flowId != reconnectFlowId) return

        val token = TokenClient.fetch(
            baseUrl = tokenBaseUrl,
            apiKey = apiKey,
            event = sourceEvent,
            agentType = mode.agentType,
            selectedLanguage = current.selectedLanguageCode,
        )
        if (token == null) {
            onSessionError("Failed to fetch token")
            return
        }
        if (flowId != reconnectFlowId) return

        lastSourceEvent = sourceEvent
        _state.update {
            it.copy(
                localeConfig = token.localeConfig,
                // Keep an explicit choice; otherwise adopt the backend default.
                selectedLanguageCode = it.selectedLanguageCode
                    ?: token.localeConfig.firstOrNull { locale -> locale.isDefault }?.code,
            )
        }
        log("Connecting ($sourceEvent, ${mode.agentType.wireValue}) to ${token.roomName} as ${token.identity}")
        VisionBotSDKManager.connectToGeoVisionRoom(
            context = getApplication(),
            socketUrl = socketUrl,
            accessToken = token.accessToken,
            roomOptions = GeoVisionRoomOptions(
                videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT)
            ),
        )
    }

    private fun onSessionError(reason: String) {
        log("Session error: $reason")
        _state.update { it.copy(phase = ConnectionPhase.Error, connectionStatus = reason) }
    }

    private fun disconnect() {
        // Invalidate any pending reconnect so it cannot revive the session behind the user.
        reconnectFlowId += 1
        pendingTeardownReason = TeardownReason.UserInitiated
        hasBootstrappedSession = false
        consecutiveRecoveryAttempts = 0
        handoverJob?.cancel()
        // Resolve the phase here rather than waiting for a Disconnected event: the SDK stays
        // silent when the room is already gone (and reports "not connected" as an Error), so
        // relying on the event would wedge the UI mid-connect or bounce into recovery.
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
        VisionBotSDKManager.disconnectFromGeoVisionRoom()
    }

    /**
     * The per-direction handover contract. The two directions are not symmetric: they use
     * different RPC methods, different data-channel events, and differ on whether a failed
     * signal aborts the switch.
     */
    private data class HandoverPlan(
        val tapRpc: String,
        val tapPublish: String,
        val confirmRpc: String,
        val confirmPublish: String,
        val sourceEvent: String,
        val abortOnSignalFailure: Boolean,
    ) {
        companion object {
            fun forTarget(target: SessionMode): HandoverPlan = when (target) {
                // Voice → chat. Mirrors LKGeoAIActivity.onVoiceToChatHandoverClick +
                // LkGeoIQViewModel.sendVoiceToChatHandover.
                SessionMode.Chat -> HandoverPlan(
                    tapRpc = GeoProtocol.TE_HANDOVER_TEXT,
                    tapPublish = GeoProtocol.VAP_HANDOVER_SHEET,
                    confirmRpc = GeoProtocol.TE_HANDOVER_TEXT_CONFIRM,
                    confirmPublish = GeoProtocol.VAP_HANDOVER_ASSIST,
                    sourceEvent = GeoProtocol.HANDOVER_FROM_VOICE,
                    // Consumer app keeps this best-effort so a dropped signal cannot trap
                    // the user on the voice screen.
                    abortOnSignalFailure = false,
                )
                // Chat → voice. Mirrors BAiChatActivity.onChatToVoiceHandoverClick +
                // BAiActionDispatcher.sendVoiceHandoverTapped/Confirmed.
                SessionMode.Video -> HandoverPlan(
                    tapRpc = GeoProtocol.TE_HANDOVER_VOICE,
                    tapPublish = GeoProtocol.BA_HANDOVER_SHEET,
                    confirmRpc = GeoProtocol.TE_HANDOVER_VOICE_CONFIRM,
                    confirmPublish = GeoProtocol.BA_HANDOVER_VOICE,
                    sourceEvent = GeoProtocol.HANDOVER_FROM_ASSIST,
                    abortOnSignalFailure = true,
                )
            }
        }
    }

    /**
     * Switches session mode: signal the agent, tear the room down, then bring up a fresh
     * room on the handover source event.
     *
     * A handover is not an in-place mode toggle — each mode is served by a different agent
     * (`agent_type`), so it requires a new token and a new room. The consumer app splits
     * this across two activities; here it is one flow ending in [MainEffect.HandoverReady],
     * which drives the tab switch.
     */
    private fun handover(target: SessionMode) {
        if (!_state.value.hasActiveSession) {
            // Nothing to hand over — just switch modes so the next Connect picks the right agent.
            _state.update { it.copy(activeMode = target) }
            viewModelScope.launch { _effects.emit(MainEffect.HandoverReady(target)) }
            return
        }
        if (handoverJob?.isActive == true) {
            log("Handover already in progress, ignoring")
            return
        }
        val plan = HandoverPlan.forTarget(target)
        handoverJob = viewModelScope.launch {
            log("Handover → ${target.name}: signalling agent")

            // The consumer app sends these when the confirmation sheet opens. This sample has
            // no sheet, so they go out immediately before the confirm pair.
            launchRpcCall(plan.tapRpc, "")
            launchPublishEvent(plan.tapPublish, GeoProtocol.TOPIC_LISTEN)

            val signalsSent = sendHandoverConfirmSignals(plan)
            if (!signalsSent && plan.abortOnSignalFailure) {
                log("Handover → ${target.name}: aborted, agent did not acknowledge")
                return@launch
            }

            // Tear down the old room before requesting a new token, so the backend sees the
            // handover as a clean session boundary.
            pendingTeardownReason = TeardownReason.Handover
            hasBootstrappedSession = false
            if (!disconnectAndAwait()) {
                log("Handover: disconnect timed out, forcing cleanup")
                VisionBotSDKManager.releaseRoomResources()
            }

            _state.update { it.copy(activeMode = target) }
            _effects.emit(MainEffect.HandoverReady(target))
            log("Handover → ${target.name}: reconnecting as ${plan.sourceEvent}")
            connect(target, sourceEvent = plan.sourceEvent)
        }
    }

    /** Fires the confirm RPC and data-channel event in parallel; both must land. */
    private suspend fun sendHandoverConfirmSignals(plan: HandoverPlan): Boolean {
        val rpcJob = launchRpcCall(plan.confirmRpc, "")
        val publishJob = launchPublishEvent(plan.confirmPublish, GeoProtocol.TOPIC_LISTEN)
        val result = withTimeoutOrNull(HANDOVER_SIGNAL_TIMEOUT_MS) {
            val rpcResult = rpcJob.await()
            val publishResult = publishJob.await()
            log("Handover signals: rpc=$rpcResult publish=$publishResult")
            rpcResult && publishResult
        }
        if (result == null) {
            rpcJob.cancel()
            publishJob.cancel()
            log("Handover signals timed out")
        }
        return result == true
    }

    /** Disconnects and waits for the SDK to confirm. Returns false on timeout. */
    private suspend fun disconnectAndAwait(
        timeoutMs: Long = DISCONNECT_AWAIT_TIMEOUT_MS,
    ): Boolean {
        if (VisionBotSDKManager.getCurrentroom() == null) return true
        VisionBotSDKManager.disconnectFromGeoVisionRoom()
        return withTimeoutOrNull(timeoutMs) {
            VisionBotSDKManager.events.filterIsInstance<GeoVisionEvent.Disconnected>().first()
            true
        } != null
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
            val sent = VisionBotSDKManager.sendFile(app, file, GeoProtocol.TOPIC_FILE)
            log(if (sent) "Sent: ${file.name}" else "Send failed: ${file.name}")
            file.delete()
        }
    }

    /**
     * Sends a user message as an RPC, matching the consumer app's
     * `BAiActionDispatcher.dispatchAction(TextSubmitted)`. The agent listens for
     * `te_send_message` — a text stream on `lk_va_publish` is not an equivalent transport.
     */
    private fun sendChatMessage(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            // Check before echoing the bubble: an RPC with no agent in the room is dropped, and
            // a transcript entry for it would read as delivered.
            if (VisionBotSDKManager.getRemoteParticipants().isEmpty()) {
                log("Cannot send chat: agent not in room")
                return@launch
            }
            addChatMessage(message, isLocal = true, sender = "You")
            val payload = JSONObject().apply { put(GeoProtocol.KEY_TEXT, message) }.toString()
            if (!launchRpcCall(GeoProtocol.TE_SEND_MESSAGE, payload).await()) {
                log("Chat send failed: ${GeoProtocol.TE_SEND_MESSAGE} not delivered")
            }
        }
    }

    /**
     * Performs an RPC against the agent (the sole remote participant).
     *
     * Returns a [Deferred] rather than awaiting so callers can fire several in parallel and
     * bound the whole set with one timeout.
     */
    private fun launchRpcCall(method: String, payload: String): Deferred<Boolean> =
        viewModelScope.async(Dispatchers.IO) {
            val localParticipant = VisionBotSDKManager.getLocalParticipant()
            val remoteIdentity = VisionBotSDKManager.getRemoteParticipants()
                .values.firstOrNull()?.identity
            if (remoteIdentity == null) {
                log("RPC $method skipped: no remote participant")
                return@async false
            }
            if (localParticipant == null) {
                log("RPC $method skipped: no local participant")
                return@async false
            }
            try {
                localParticipant.performRpc(
                    destinationIdentity = remoteIdentity,
                    method = method,
                    payload = payload,
                )
                true
            } catch (e: IllegalStateException) {
                log("RPC $method skipped (room disposed): ${e.message}")
                false
            } catch (e: Exception) {
                log("RPC $method failed: ${e.message}")
                false
            }
        }

    /**
     * Publishes an event on the agent data channel. Payload shape mirrors the consumer app's
     * `GeoAIPayload`: `{"event":…, "juno":{…}, "va_data":{…}}`.
     */
    private fun launchPublishEvent(event: String, topic: String): Deferred<Boolean> =
        viewModelScope.async(Dispatchers.IO) {
            val localParticipant = VisionBotSDKManager.getLocalParticipant()
            if (localParticipant == null) {
                log("Publish $event skipped: no local participant")
                return@async false
            }
            try {
                val payload = JSONObject().apply {
                    put(GeoProtocol.KEY_EVENT, event)
                    put(GeoProtocol.KEY_JUNO, JSONObject())
                    put(GeoProtocol.KEY_VA_DATA, JSONObject())
                }.toString()
                localParticipant.publishData(
                    data = payload.toByteArray(Charsets.UTF_8),
                    reliability = DataPublishReliability.RELIABLE,
                    topic = topic,
                )
                true
            } catch (e: IllegalStateException) {
                log("Publish $event skipped (room disposed): ${e.message}")
                false
            } catch (e: Exception) {
                log("Publish $event failed: ${e.message}")
                false
            }
        }

    private fun selectEnvSource(mode: SessionMode, useProd: Boolean) {
        updateEnvConfig(mode) { it.copy(useProd = useProd) }
        log(
            "${mode.name} deployment set to ${_state.value.socketUrlFor(mode)} " +
                "(applies on next connect)"
        )
    }

    private fun saveStagingConfig(mode: SessionMode, socketUrl: String, apiKey: String) {
        val trimmedUrl = socketUrl.trim()
        val trimmedKey = apiKey.trim()
        if (!GeoEnv.isValidSocketUrl(trimmedUrl) || trimmedKey.isEmpty()) {
            log("Ignoring invalid staging config for ${mode.name}: $trimmedUrl")
            return
        }
        updateEnvConfig(mode) {
            it.copy(stagingSocketUrl = trimmedUrl, stagingApiKey = trimmedKey)
        }
        log("${mode.name} staging deployment saved: $trimmedUrl (applies on next connect)")
    }

    private fun updateEnvConfig(mode: SessionMode, transform: (ModeEnvConfig) -> ModeEnvConfig) {
        val updated = transform(_state.value.envFor(mode))
        configStore.saveEnvConfig(mode, updated)
        _state.update {
            when (mode) {
                SessionMode.Video -> it.copy(voiceEnv = updated)
                SessionMode.Chat -> it.copy(chatEnv = updated)
            }
        }
    }

    private fun resetConfig() {
        configStore.clear()
        _state.update {
            it.copy(
                voiceEnv = configStore.envConfig(SessionMode.Video),
                chatEnv = configStore.envConfig(SessionMode.Chat),
            )
        }
        log("Deployment config reset to defaults")
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
                    // Keep the Reconnecting label so recovery stays distinguishable from a
                    // first connect — shouldRecoverFrom depends on that distinction.
                    if (it.isReconnecting) it
                    else it.copy(phase = ConnectionPhase.Connecting, connectionStatus = "Connecting...")
                }
            }

            is GeoVisionEvent.Connected -> {
                log("Connected: ${event.roomName} as ${event.localParticipant.identity}")
                consecutiveRecoveryAttempts = 0
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
                applyRemoteAudioSubscription()
                runSessionBootstrapIfRequired()
            }

            is GeoVisionEvent.Disconnected -> {
                log("Disconnected: ${event.reason ?: "Client initiated"}")
                val teardownReason = pendingTeardownReason
                pendingTeardownReason = null
                hasBootstrappedSession = false
                // Decide before mutating phase — the recovery rule reads the phase we were in.
                val shouldRecover = teardownReason == null && shouldRecoverFrom(_state.value.phase)
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
                        // A reconnect or session renewal continues the same conversation, so the
                        // transcript survives. Ending the session or switching agents clears it.
                        chatMessages = if (teardownReason?.endsConversation == true) {
                            emptyList()
                        } else {
                            it.chatMessages
                        },
                    )
                }
                if (shouldRecover) onUnexpectedRoomLoss()
            }

            is GeoVisionEvent.ParticipantJoined -> {
                log("Participant joined: ${event.participant.identity}")
                announceBotConnected()
                applyRemoteAudioSubscription()
            }

            is GeoVisionEvent.ParticipantLeft ->
                log("Participant left: ${event.participant.identity}")

            is GeoVisionEvent.ParticipantAttributesChanged -> {
                event.changedAttributes[GeoProtocol.ATTR_AGENT_STATE]?.let { agentState ->
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

            is GeoVisionEvent.TrackSubscribed -> {
                log("Remote track: ${event.track.name} from ${event.participant.identity}")
                // The agent can start publishing audio at any point, not just at join.
                applyRemoteAudioSubscription()
            }

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
                parseChatEventMessage(event.topic.orEmpty(), event.message)
            }

            is GeoVisionEvent.Error -> {
                log("ERROR: ${event.message}")
                // Error is the SDK's catch-all: a camera/mic toggle failure, a bad frame, or
                // "not connected" all arrive here alongside genuine room loss. Recover only
                // when the room is actually gone, otherwise an incidental failure would tear
                // down a healthy conversation (and a replayed Error would do it on restore).
                val roomState = VisionBotSDKManager.getCurrentroom()?.state
                val roomLost = roomState == null || roomState == GeoVisionRoomState.DISCONNECTED
                val shouldRecover = roomLost && shouldRecoverFrom(_state.value.phase)
                _state.update {
                    when {
                        // Incidental failure on a live room — the session is still fine.
                        !roomLost -> it
                        // No session to fail, e.g. the error follows an explicit disconnect.
                        !it.hasActiveSession -> it
                        // Recovery takes over and sets its own phase.
                        shouldRecover -> it
                        else -> it.copy(phase = ConnectionPhase.Error, connectionStatus = "Error")
                    }
                }
                if (shouldRecover) {
                    hasBootstrappedSession = false
                    onUnexpectedRoomLoss()
                }
            }
        }
    }

    /**
     * Whether an unrequested disconnect/error should trigger a token refresh and reconnect.
     *
     * Only a room we believed was live (or was already recovering) is worth recovering. A drop
     * during the initial connect is treated as transient — the SDK emits one while swapping
     * rooms — and recovering from it would fight the connect already in flight.
     */
    private fun shouldRecoverFrom(phase: ConnectionPhase): Boolean = when (phase) {
        ConnectionPhase.Connected, ConnectionPhase.Reconnecting -> true
        ConnectionPhase.Connecting, ConnectionPhase.Disconnected, ConnectionPhase.Error -> false
    }

    /**
     * Once-per-room setup, mirroring `BAiChatSessionController.runSessionBootstrapIfRequired`:
     * ask the agent for the session snapshot and expose the RPC it uses to end the room.
     */
    private fun runSessionBootstrapIfRequired() {
        if (hasBootstrappedSession) return
        hasBootstrappedSession = true
        launchPublishEvent(GeoProtocol.VAL_GET_SESSION_INFO, GeoProtocol.TOPIC_PUBLISH)
        registerRpcHandlers()
    }

    private fun registerRpcHandlers() {
        val localParticipant = VisionBotSDKManager.getLocalParticipant() ?: return
        runCatching {
            localParticipant.registerRpcMethod(GeoProtocol.FE_RPC_ROOM_DISCONNECTION) {
                // LiveKit dispatches RPC handlers on Dispatchers.Default; hop to the ViewModel
                // scope so the session flags are only ever written from one dispatcher.
                viewModelScope.launch {
                    log("Room disconnection requested by agent")
                    disconnect()
                }
                ""
            }
        }.onFailure { log("Failed to register ${GeoProtocol.FE_RPC_ROOM_DISCONNECTION}: ${it.message}") }
    }

    /**
     * The room dropped without us asking. Re-runs token + connect on the same session source,
     * matching the consumer app's `onUnexpectedRoomDisconnectFromSdk`.
     */
    private fun onUnexpectedRoomLoss() {
        // A handover owns its own teardown and reconnect.
        if (handoverJob?.isActive == true) return
        if (consecutiveRecoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            onSessionError("Lost connection after $MAX_RECOVERY_ATTEMPTS reconnect attempts")
            return
        }
        consecutiveRecoveryAttempts += 1
        val mode = _state.value.activeMode
        log("Unexpected room loss, reconnecting (${mode.name}), attempt $consecutiveRecoveryAttempts")
        _state.update {
            it.copy(phase = ConnectionPhase.Reconnecting, connectionStatus = "Reconnecting...")
        }
        // Drop the dead room first: connectToGeoVisionRoom refuses to start while one is still
        // CONNECTED/CONNECTING and reports it as an Error, which would loop back into here.
        VisionBotSDKManager.releaseRoomResources()
        connect(mode, sourceEvent = lastSourceEvent ?: mode.defaultSourceEvent)
    }

    /**
     * Backend ended the agent session (e.g. inactivity). Same recovery as a room loss, but we
     * initiate the disconnect so the SDK event is not double-counted as an unexpected drop.
     */
    private fun onBackendSessionEnded(reason: String?) {
        log("Backend session ended: ${reason ?: "no reason given"}")
        if (!_state.value.hasActiveSession) return
        if (handoverJob?.isActive == true) return
        val mode = _state.value.activeMode
        _state.update {
            it.copy(phase = ConnectionPhase.Reconnecting, connectionStatus = "Reconnecting...")
        }
        pendingTeardownReason = TeardownReason.SessionRenewal
        hasBootstrappedSession = false
        viewModelScope.launch {
            if (!disconnectAndAwait()) {
                // Leaving a half-open room behind makes the next connect fail its
                // already-connected guard, which would bounce back through recovery.
                log("Session renewal: disconnect timed out, forcing cleanup")
                VisionBotSDKManager.releaseRoomResources()
            }
            // Restore the reconnecting label: the disconnect above reset it to Disconnected.
            _state.update {
                it.copy(phase = ConnectionPhase.Reconnecting, connectionStatus = "Reconnecting...")
            }
            connect(mode, sourceEvent = lastSourceEvent ?: mode.defaultSourceEvent)
        }
    }

    /**
     * Stops the agent's voice from playing in chat mode.
     *
     * Disabling the local microphone only controls what we *send*. The agent's audio track is
     * auto-subscribed and played by LiveKit, so a chat session keeps hearing TTS unless the
     * subscription is dropped. Unsubscribing (rather than muting) also stops the server
     * sending the stream at all.
     *
     * The production app never needs this because its chat tab connects to a dedicated chat
     * deployment whose agent publishes no audio. This sample points both modes at one
     * environment, so it has to enforce the mode itself.
     */
    private fun applyRemoteAudioSubscription() {
        val shouldSubscribe = _state.value.activeMode == SessionMode.Video
        VisionBotSDKManager.getRemoteParticipants().values.forEach { participant ->
            participant.trackPublications.values
                .filterIsInstance<RemoteTrackPublication>()
                .filter { it.kind == Track.Kind.AUDIO }
                .forEach { publication ->
                    if (publication.subscribed == shouldSubscribe) return@forEach
                    publication.setSubscribed(shouldSubscribe)
                    log(
                        "Remote audio ${if (shouldSubscribe) "subscribed" else "unsubscribed"} " +
                            "(${participant.identity?.value ?: "agent"})"
                    )
                }
        }
    }

    /**
     * Sample-only handshake on a non-protocol topic. The production app never sends it; its
     * chat bootstrap is `val_get_session_info` alone. Restricted to voice mode so it cannot
     * nudge a chat session into speaking.
     */
    private fun announceBotConnected() {
        if (_state.value.activeMode != SessionMode.Video) return
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
                agentState = localParticipant?.attributes?.get(GeoProtocol.ATTR_AGENT_STATE) ?: "",
                localVideoTrack = cameraTrack,
            )
        }
    }

    /**
     * Routes an agent data-channel message, mirroring `BAiChatEventHandler.handleCustomMessage`:
     * session-end is a control event, otherwise pull display text out of the payload.
     */
    private fun parseChatEventMessage(topic: String, rawMessage: String?) {
        if (rawMessage.isNullOrBlank()) return
        try {
            val root = JSONObject(rawMessage)
            val eventName = root.optString(GeoProtocol.KEY_EVENT, "")
            val vaData = root.optString(GeoProtocol.KEY_VA_DATA, "")

            if (eventName.trim() == GeoProtocol.BA_SESSION_END) {
                onBackendSessionEnded(
                    root.optString(GeoProtocol.KEY_REASON).takeIf { it.isNotBlank() }
                )
                return
            }

            val text = extractDisplayText(root) ?: extractDisplayText(vaData)
            if (!text.isNullOrBlank()) {
                addChatMessage(text, isLocal = false, sender = "Agent")
                return
            }

            if (eventName.isNotBlank()) {
                addChatMessage("[$eventName] $vaData", isLocal = false, sender = "Agent")
            } else {
                addChatMessage(rawMessage, isLocal = false, sender = topic)
            }
        } catch (_: Exception) {
            addChatMessage(rawMessage, isLocal = false, sender = topic)
        }
    }

    /** Text resolution order matches the consumer app's chat payload parser. */
    private fun extractDisplayText(json: JSONObject): String? {
        return json.optString(GeoProtocol.KEY_TEXT).takeIf { it.isNotBlank() }
            ?: json.optString(GeoProtocol.KEY_MESSAGE).takeIf { it.isNotBlank() }
            ?: json.optString(GeoProtocol.KEY_LABEL).takeIf { it.isNotBlank() }
            ?: json.optString(GeoProtocol.KEY_TITLE).takeIf { it.isNotBlank() }
    }

    private fun extractDisplayText(vaData: String): String? {
        if (vaData.isBlank() || !vaData.trim().startsWith("{")) return null
        return try {
            extractDisplayText(JSONObject(vaData))
        } catch (_: Exception) {
            null
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
