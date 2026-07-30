package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.annotations.Beta
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.RoomException
import io.livekit.android.room.datastream.StreamBytesOptions
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.util.LoggingLevel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceViewRenderer
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.WeakHashMap


/**
 * Singleton entry point for the GeoIQ Vision Bot SDK.
 *
 * Typical usage:
 * 1. Collect [events] in a lifecycle-aware scope.
 * 2. Call [connectToGeoVisionRoom] to start a session.
 * 3. Use media/participant helpers during the session.
 * 4. Call [disconnectFromGeoVisionRoom] to end a session, or [shutdown] to fully tear down the SDK.
 */
object VisionBotSDKManager {

    private const val TAG = "GeoI_VB_SDK"
    private const val TEXT_STREAM_TOPIC = "lk_va_publish"
    private const val DEFAULT_FILE_SEND_TOPIC = "send-file"
    private const val FILE_BUFFER_SIZE = 4096

    // ── State ──────────────────────────────────────────────────────────────────

    /** The active LiveKit room. Null when not connected. */
    // Safe: LiveKit.create() is always passed context.applicationContext, so no
    // Activity is retained. Lint can't see through the constructor to verify this.
    @SuppressLint("StaticFieldLeak")
    var currentRoom: Room? = null
        private set
    private var roomEventsJob: Job? = null

    // WeakHashMap lets renderers be GC'd when no longer referenced externally,
    // preventing leaks when views are recycled or destroyed between sessions.
    private val initializedRenderers: MutableSet<RendererCommon.RendererEvents> =
        Collections.newSetFromMap(WeakHashMap())

    // ── Coroutine Infrastructure ───────────────────────────────────────────────

    // Last-resort safety net: catches any exception that escapes a sdkScope.launch block
    // without a local try-catch, preventing a fatal crash and surfacing it as an error event.
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _events.tryEmit(GeoVisionEvent.Error("Unexpected SDK error: ${throwable.message}", throwable))
    }
    private val sdkScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

    // ── Events ─────────────────────────────────────────────────────────────────

    private val _events = MutableSharedFlow<GeoVisionEvent>(
        replay = 1,             // Late subscribers immediately receive the last emitted event.
        extraBufferCapacity = 5 // Absorbs bursts so fast emission never drops events.
    )

    /** Hot flow of all SDK events. Collect in a lifecycle-aware scope. See [GeoVisionEvent]. */
    val events: SharedFlow<GeoVisionEvent> = _events.asSharedFlow()

    // ── Connection ─────────────────────────────────────────────────────────────

    /**
     * Creates a LiveKit room and begins connecting to [socketUrl] with [accessToken].
     *
     * Asynchronous — listen to [events] for [GeoVisionEvent.Connected] on success or
     * [GeoVisionEvent.Error] on failure. [GeoVisionEvent.Connecting] is emitted immediately.
     *
     * If a session is already active this emits [GeoVisionEvent.Error] and returns without
     * creating a new room. Call [disconnectFromGeoVisionRoom] first.
     *
     * @param roomOptions Defaults to a standard [GeoVisionRoomOptions] if not provided.
     */
    @OptIn(Beta::class)
    fun connectToGeoVisionRoom(
        context: Context,
        socketUrl: String,
        accessToken: String,
        roomOptions: GeoVisionRoomOptions = GeoVisionRoomOptions(),
        isLoggingEnabled : Boolean = false
    ) {
        if (currentRoom?.state == Room.State.CONNECTED || currentRoom?.state == Room.State.CONNECTING) {
            _events.tryEmit(GeoVisionEvent.Error("Already connected or connecting.", null))
            return
        }

        roomEventsJob?.cancel()
        currentRoom = LiveKit.create(appContext = context.applicationContext, options = roomOptions)
        if (isLoggingEnabled) LiveKit.loggingLevel = LoggingLevel.VERBOSE

        val roomInstance = currentRoom ?: run {
            _events.tryEmit(GeoVisionEvent.Error("Failed to create Room object.", null))
            return
        }

        startCollectingRoomEvents(roomInstance)
        _events.tryEmit(GeoVisionEvent.Connecting(socketUrl, accessToken.takeLast(10)))

        sdkScope.launch {
            try {
                roomInstance.connect(url = socketUrl, token = accessToken)
            } catch (e: Exception) {
                _events.tryEmit(GeoVisionEvent.Error("Connection setup failed: ${e.message}", e))
            }
        }
    }

    /**
     * Gracefully disconnects from the current room.
     *
     * Asynchronous — [GeoVisionEvent.Disconnected] fires when the server confirms disconnect.
     * Emits [GeoVisionEvent.Error] and returns immediately if no room is active.
     */
    fun disconnectFromGeoVisionRoom() {
        val roomToDisconnect = currentRoom ?: run {
            _events.tryEmit(GeoVisionEvent.Error("Not connected to any room.", null))
            return
        }
        sdkScope.launch {
            try {
                initializedRenderers.clear()
                roomToDisconnect.unregisterTextStreamHandler(topic = TEXT_STREAM_TOPIC)
                withContext(Dispatchers.IO) { roomToDisconnect.disconnect() }
            } catch (e: Exception) {
                _events.tryEmit(
                    GeoVisionEvent.Error("Disconnect completed with exception: ${e.message}", e)
                )
            }
        }
    }

    /**
     * Releases the current room's native resources and clears [currentRoom].
     *
     * This is a *partial* cleanup — it does not cancel the event collection coroutine.
     * Suitable for freeing resources immediately after receiving [GeoVisionEvent.Disconnected].
     * For a full teardown (including stopping event collection), use [shutdown] instead.
     */
    fun releaseRoomResources() {
        currentRoom?.release()
        currentRoom = null
    }

    // ── Room Event Collection ──────────────────────────────────────────────────

    private fun startCollectingRoomEvents(room: Room) {
        roomEventsJob = sdkScope.launch {
            room.events.collect { event -> handleRoomEvent(event, room) }
        }
    }

    // ── Room Event Dispatching ─────────────────────────────────────────────────

    @OptIn(Beta::class)
    private fun handleRoomEvent(event: RoomEvent, room: Room) {
        when (event) {
            is RoomEvent.Connected -> onConnected(room)
            is RoomEvent.Disconnected -> onDisconnected(event)
            is RoomEvent.FailedToConnect -> onFailedToConnect(event)
            is RoomEvent.ParticipantConnected -> onParticipantConnected(event)
            is RoomEvent.ParticipantDisconnected -> onParticipantDisconnected(event)
            is RoomEvent.ParticipantAttributesChanged -> onParticipantAttributesChanged(event)
            is RoomEvent.LocalTrackSubscribed -> onLocalTrackSubscribed(event)
            is RoomEvent.TrackPublished -> onTrackPublished(event)
            is RoomEvent.TrackUnpublished -> onTrackUnpublished(event)
            is RoomEvent.TrackSubscribed -> onTrackSubscribed(event)
            is RoomEvent.TrackUnsubscribed -> onTrackUnsubscribed(event)
            is RoomEvent.ConnectionQualityChanged -> onConnectionQualityChanged(event)
            is RoomEvent.ActiveSpeakersChanged -> onActiveSpeakersChanged(event)
            is RoomEvent.DataReceived -> onDataReceived(event)
            is RoomEvent.TranscriptionReceived -> onTranscriptionReceived(event)
            else -> Log.d(TAG, "Unhandled RoomEvent: ${event::class.java.simpleName}")
        }
    }

    private fun onConnected(room: Room) {
        // Registered here — after the WebRTC PeerConnection publisher is established — to avoid
        // RoomException$ConnectException crashes. Re-registers automatically on every reconnect.
        registerIncomingTextStreamHandler(room)
        _events.tryEmit(
            GeoVisionEvent.Connected(room.name ?: "Unknown Room", room.localParticipant)
        )
    }

    private fun registerIncomingTextStreamHandler(room: Room) {
        room.registerTextStreamHandler(topic = TEXT_STREAM_TOPIC) { reader, info ->
            sdkScope.launch {
                try {
                    val allText = reader.readAll()
                    _events.tryEmit(
                        GeoVisionEvent.CustomMessageReceived(
                            senderId = info.toString(),
                            message = allText.joinToString(""),
                            topic = reader.info.topic,
                        )
                    )
                } catch (e: Exception) {
                    // reader.readAll() can throw StreamException$TerminatedException
                    // if the stream is cut mid-read (e.g., remote disconnects unexpectedly).
                    _events.tryEmit(
                        GeoVisionEvent.Error("Failed to read incoming text stream: ${e.message}", e)
                    )
                }
            }
        }
    }

    private fun onDisconnected(event: RoomEvent.Disconnected) {
        _events.tryEmit(GeoVisionEvent.Disconnected(event.error?.message ?: "Client initiated"))
    }

    private fun onFailedToConnect(event: RoomEvent.FailedToConnect) {
        _events.tryEmit(
            GeoVisionEvent.Error("Failed to connect: ${event.error.message}", event.error)
        )
    }

    private fun onParticipantConnected(event: RoomEvent.ParticipantConnected) {
        if (event.participant is RemoteParticipant) {
            _events.tryEmit(GeoVisionEvent.ParticipantJoined(event.participant))
        }
    }

    private fun onParticipantDisconnected(event: RoomEvent.ParticipantDisconnected) {
        if (event.participant is RemoteParticipant) {
            _events.tryEmit(GeoVisionEvent.ParticipantLeft(event.participant))
        }
    }

    private fun onParticipantAttributesChanged(event: RoomEvent.ParticipantAttributesChanged) {
        _events.tryEmit(
            GeoVisionEvent.ParticipantAttributesChanged(event.participant, event.changedAttributes)
        )
    }

    private fun onLocalTrackSubscribed(event: RoomEvent.LocalTrackSubscribed) {
        _events.tryEmit(GeoVisionEvent.LocalTrackSubscribed(event.publication, event.participant))
    }

    private fun onTrackPublished(event: RoomEvent.TrackPublished) {
        if (event.participant is LocalParticipant) {
            _events.tryEmit(
                GeoVisionEvent.TrackPublished(
                    event.publication, event.participant as LocalParticipant
                )
            )
        }
    }

    private fun onTrackUnpublished(event: RoomEvent.TrackUnpublished) {
        if (event.participant is RemoteParticipant) {
            _events.tryEmit(
                GeoVisionEvent.TrackUnpublished(
                    event.publication, event.participant as RemoteParticipant
                )
            )
        }
    }

    private fun onTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        if (event.participant is RemoteParticipant) {
            _events.tryEmit(
                GeoVisionEvent.TrackSubscribed(event.track, event.publication, event.participant)
            )
        }
    }

    private fun onTrackUnsubscribed(event: RoomEvent.TrackUnsubscribed) {
        if (event.participant is RemoteParticipant) {
            _events.tryEmit(
                GeoVisionEvent.TrackUnsubscribed(event.track, event.publications, event.participant)
            )
        }
    }

    private fun onConnectionQualityChanged(event: RoomEvent.ConnectionQualityChanged) {
        _events.tryEmit(GeoVisionEvent.ConnectionQualityChanged(event.quality, event.participant))
    }

    private fun onActiveSpeakersChanged(event: RoomEvent.ActiveSpeakersChanged) {
        _events.tryEmit(GeoVisionEvent.ActiveSpeakersChanged(event.speakers))
    }

    private fun onDataReceived(event: RoomEvent.DataReceived) {
        val senderId = event.participant?.identity?.toString()
        val topic = event.topic
        try {
            val message = event.data.toString(Charsets.UTF_8)
            _events.tryEmit(GeoVisionEvent.CustomMessageReceived(senderId, message, topic))
        } catch (e: Exception) {
            _events.tryEmit(
                GeoVisionEvent.Error("Failed to decode incoming data for topic '$topic'", e)
            )
        }
    }

    @OptIn(Beta::class)
    private fun onTranscriptionReceived(event: RoomEvent.TranscriptionReceived) {
        event.transcriptionSegments.forEach { segment ->
            _events.tryEmit(
                GeoVisionEvent.TranscriptionReceived(
                    senderId = segment.id,
                    message = segment.text,
                    isFinal = segment.final
                )
            )
        }
    }

    // ── Media Controls ─────────────────────────────────────────────────────────

    /**
     * Enables or disables the local camera.
     * @return true on success, false on failure (a [GeoVisionEvent.Error] is also emitted).
     */
    suspend fun setCameraEnabled(enable: Boolean): Boolean {
        val localParticipant = currentRoom?.localParticipant ?: run {
            _events.tryEmit(GeoVisionEvent.Error("Cannot toggle camera: Not connected.", null))
            return false
        }
        return try {
            localParticipant.setCameraEnabled(enable)
            true
        } catch (e: Exception) {
            _events.tryEmit(GeoVisionEvent.Error("Failed to set camera: ${e.message}", e))
            false
        }
    }

    /**
     * Enables or disables the local microphone.
     * @return true on success, false on failure (a [GeoVisionEvent.Error] is also emitted).
     */
    suspend fun setMicrophoneEnabled(enable: Boolean): Boolean {
        val localParticipant = currentRoom?.localParticipant ?: run {
            _events.tryEmit(GeoVisionEvent.Error("Cannot toggle microphone: Not connected.", null))
            return false
        }
        return try {
            localParticipant.setMicrophoneEnabled(enable)
            true
        } catch (e: Exception) {
            _events.tryEmit(GeoVisionEvent.Error("Failed to set microphone: ${e.message}", e))
            false
        }
    }

    fun isCameraEnabled(): Boolean = currentRoom?.localParticipant?.isCameraEnabled == true

    fun isMicrophoneEnabled(): Boolean = currentRoom?.localParticipant?.isMicrophoneEnabled == true

    /**
     * Toggles the local camera between front and back.
     *
     * Requires an active camera track — call [setCameraEnabled] with true first.
     * @return true on success, false if not connected, no camera track found, or an error occurs.
     */
    suspend fun flipCameraPosition(): Boolean {
        val localParticipant = currentRoom?.localParticipant ?: run {
            _events.tryEmit(GeoVisionEvent.Error("Cannot flip camera: Not connected.", null))
            return false
        }
        val cameraTrack =
            localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                ?: run {
                    _events.tryEmit(
                        GeoVisionEvent.Error("Cannot flip camera: No local camera track found.", null)
                    )
                    return false
                }
        return try {
            val newPosition = if (cameraTrack.options.position == CameraPosition.FRONT) {
                CameraPosition.BACK
            } else {
                CameraPosition.FRONT
            }
            cameraTrack.switchCamera(position = newPosition)
            true
        } catch (e: Exception) {
            _events.tryEmit(GeoVisionEvent.Error("Failed to flip camera: ${e.message}", e))
            false
        }
    }

    // ── Participant Queries ────────────────────────────────────────────────────

    /** Returns all currently connected remote participants, or an empty map if not connected. */
    fun getRemoteParticipants(): Map<Participant.Identity, RemoteParticipant> =
        currentRoom?.remoteParticipants ?: emptyMap()

    /** Returns the local participant, or null if not connected. */
    fun getLocalParticipant(): LocalParticipant? = currentRoom?.localParticipant

    /** Returns true if the first remote participant is currently speaking. */
    fun getIsSpeaking(): Boolean =
        currentRoom?.remoteParticipants?.values?.firstOrNull()?.isSpeaking ?: false

    fun getCurrentroom(): Room? = currentRoom

    // ── File Operations ────────────────────────────────────────────────────────

    /**
     * Streams [file] to all room participants as a LiveKit byte stream.
     *
     * IO-heavy — reads and sends in [FILE_BUFFER_SIZE]-byte chunks on [Dispatchers.IO].
     * Safe to call from any coroutine context.
     *
     * @param topic Channel identifier for the receiver to route the file. Defaults to "send-file".
     * @return true on success, false if not connected, file doesn't exist, or a send error occurs.
     */
    suspend fun sendFile(
        context: Context,
        file: File,
        topic: String = DEFAULT_FILE_SEND_TOPIC
    ): Boolean {
        val localParticipant = currentRoom?.localParticipant ?: run {
            Log.e(TAG, "Cannot send file: Not connected to a room or no local participant.")
            return false
        }
        if (!file.exists()) return false

        var inputStream: InputStream? = null
        return try {
            val streamOptions = StreamBytesOptions(
                topic = topic,
                name = file.name,
                mimeType = context.contentResolver.getType(Uri.fromFile(file))
                    ?: "application/octet-stream",
                totalSize = file.length(),
                attributes = mapOf(
                    "fileName" to file.name,
                    "fileSize" to file.length().toString()
                ),
            )
            val writer = localParticipant.streamBytes(streamOptions)
            inputStream = file.inputStream()
            withContext(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(FILE_BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        writer.write(buffer.copyOf(bytesRead))
                    }
                    writer.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing file to stream: ${e.message}", e)
                    writer.close()
                    throw e
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendFile: ${e.message}", e)
            false
        } finally {
            inputStream?.close()
        }
    }

    // ── Renderer Management ────────────────────────────────────────────────────

    /**
     * Initialises a [SurfaceViewRenderer] or [TextureViewRenderer] for video rendering.
     *
     * Must be called before attaching any video track to a renderer. Idempotent — safe to call
     * multiple times on the same instance; subsequent calls are no-ops. The renderer registry is
     * cleared on [disconnectFromGeoVisionRoom], so re-initialisation on reconnect is automatic.
     */
    fun initializeVideoRenderer(renderer: RendererCommon.RendererEvents) {
        val room = currentRoom ?: return
        if (initializedRenderers.contains(renderer)) return

        try {
            when (renderer) {
                is SurfaceViewRenderer -> room.initVideoRenderer(renderer)
                is TextureViewRenderer -> room.initVideoRenderer(renderer)
                else -> {
                    Log.w(TAG, "Unsupported renderer type: ${renderer::class.java.simpleName}")
                    return
                }
            }
            initializedRenderers.add(renderer)
        } catch (e: Exception) {
            if (e.message?.contains("already initialized") == true) {
                initializedRenderers.add(renderer) // Renderer was set up outside the SDK — sync tracking state.
            } else {
                Log.e(TAG, "Failed to initialize renderer: ${e.message}")
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Fully tears down the SDK: disables media, disconnects, and cancels all coroutines.
     *
     * Do not call any SDK methods after this. To end a session while keeping the SDK usable,
     * use [disconnectFromGeoVisionRoom] instead.
     *
     * Cleanup runs on a dedicated IO scope so the calling thread is never blocked; [sdkScope]
     * is cancelled only after cleanup completes via [invokeOnCompletion].
     */
    fun shutdown() {
        val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        cleanupScope.launch {
            try {
                val room = currentRoom
                if (room != null) {
                    setCameraEnabled(false)
                    setMicrophoneEnabled(false)
                    room.disconnect()
                    cleanupRoomResources()
                }
            } catch (e: Exception) {
                // Best-effort cleanup — errors during shutdown are intentionally swallowed.
            }
        }.invokeOnCompletion {
            sdkScope.cancel()
            cleanupScope.cancel()
        }
    }

    // Unlike the public releaseRoomResources(), this also cancels the event collection job —
    // used by shutdown() which needs a full internal teardown, not a partial resource release.
    private fun cleanupRoomResources() {
        roomEventsJob?.cancel()
        roomEventsJob = null
        currentRoom?.release()
        currentRoom = null
    }
}
