package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.content.Context
import android.net.Uri
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.annotations.Beta
import io.livekit.android.room.Room
import io.livekit.android.room.RoomException
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import io.livekit.android.room.datastream.StreamBytesOptions
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.rpc.RpcError
import kotlinx.coroutines.withContext
import java.io.InputStream


typealias Track = Track
typealias VideoTrack = VideoTrack
typealias LocalVideoTrack = LocalVideoTrack
typealias DataPublishReliability = DataPublishReliability
typealias RpcError = RpcError
typealias ConnectionQuality = ConnectionQuality
typealias LocalParticipant = LocalParticipant
typealias RemoteParticipant = RemoteParticipant
typealias GeoVisionRoomOptions = RoomOptions
typealias LocalAudioTrackOptions = LocalAudioTrackOptions
typealias LocalVideoTrackOptions = LocalVideoTrackOptions
typealias audioTrackPublishDefaults = AudioTrackPublishDefaults
typealias videoTrackPublishDefaults= VideoTrackPublishDefaults
typealias CameraPosition = CameraPosition


sealed class GeoVisionEvent {
    data class Connecting(val url: String, val tokenSnippet: String) : GeoVisionEvent()
    data class Connected(val roomName: String, val localParticipant: LocalParticipant) :
        GeoVisionEvent()

    data class Disconnected(val reason: String?) : GeoVisionEvent()
    data class ParticipantJoined(val participant: RemoteParticipant) : GeoVisionEvent()
    data class ParticipantLeft(val participant: RemoteParticipant) : GeoVisionEvent()
    data class TrackPublished(
        val publication: TrackPublication, val participant: LocalParticipant
    ) : GeoVisionEvent()

    data class TrackUnpublished(
        val publication: TrackPublication, val participant: RemoteParticipant
    ) : GeoVisionEvent()

    data class TrackSubscribed(
        val track: Track, val publication: TrackPublication, val participant: RemoteParticipant
    ) : GeoVisionEvent()

    data class TrackUnsubscribed(
        val track: Track, val publication: TrackPublication, val participant: RemoteParticipant
    ) : GeoVisionEvent()

    data class ConnectionQualityChanged(
        val quality: ConnectionQuality, val participant: Participant
    ) : GeoVisionEvent()

    data class ActiveSpeakersChanged(val speakers: List<Participant>) : GeoVisionEvent()
    data class Error(val message: String, val exception: Throwable?) : GeoVisionEvent()
    data class ParticipantAttributesChanged(
        val participant: Participant, val changedAttributes: Map<String, String>
    ) : GeoVisionEvent()


    data class TranscriptionReceived(
        val senderId: String?,
        val message: String?,
        val isFinal: Boolean = false // Optional: if you want to mark messages
    ) : GeoVisionEvent()

    data class CustomMessageReceived(
        val senderId: String?, val message: String?, // This will be the JSON string
        val topic: String?,    // Topic of the data message
        val isCritical: Boolean = false // Optional: if you want to mark messages
    ) : GeoVisionEvent()
}


object VisionBotSDKManager {

    private const val TAG = "GeoI_VB_SDK"
    public var currentRoom: Room? = null
    private var roomEventsJob: Job? = null
    private val sdkScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _events = MutableSharedFlow<GeoVisionEvent>(replay = 1, extraBufferCapacity = 5)
    val events: SharedFlow<GeoVisionEvent> = _events.asSharedFlow()

    @OptIn(Beta::class)
    fun connectToGeoVisionRoom(
        context: Context,
        socketUrl: String,
        accessToken: String,
        roomOptions: GeoVisionRoomOptions = GeoVisionRoomOptions()
    ) {
        if (currentRoom != null && (currentRoom?.state == Room.State.CONNECTED || currentRoom?.state == Room.State.CONNECTING)) {
//            Log.w(
//                TAG,
//                "Already connected or connecting to a room. Call disconnectFromGeoVisionRoom() first."
//            )
            _events.tryEmit(GeoVisionEvent.Error("Already connected or connecting.", null))
            return
        }

        roomEventsJob?.cancel()

        currentRoom = LiveKit.create(appContext = context.applicationContext, options = roomOptions)

        val roomInstance = currentRoom ?: run {
//            Log.e(TAG, "Failed to create LiveKit Room object.")
            _events.tryEmit(GeoVisionEvent.Error("Failed to create Room object.", null))
            return
        }

        roomEventsJob = sdkScope.launch {

            roomInstance.events.collect { event -> // Corrected line
//                Log.d(TAG, "Received RoomEvent: ${event::class.java.simpleName}")
                when (event) {
                    is RoomEvent.Connected -> {
//                        Log.i(
//                            TAG,
//                            "Successfully connected to room: ${roomInstance.name}. Did reconnect: $event"
//                        )
                        _events.tryEmit(
                            GeoVisionEvent.Connected(
                                roomInstance.name ?: "Unknown Room", roomInstance.localParticipant
                            )
                        )
                    }

                    is RoomEvent.Disconnected -> {
//                        Log.i(
//                            TAG,
//                            "Disconnected from room: ${roomInstance.name}. Reason: ${event.error?.message ?: "Client initiated"}"
//                        )
                        _events.tryEmit(
                            GeoVisionEvent.Disconnected(
                                event.error?.message ?: "Client initiated"
                            )
                        )
//                        cleanupRoomResources()
                    }

                    is RoomEvent.FailedToConnect -> {
//                        Log.e(TAG, "Failed to connect to room: ${roomInstance.name}", event.error)
                        _events.tryEmit(
                            GeoVisionEvent.Error(
                                "Failed to connect: ${event.error.message}", event.error
                            )
                        )
//                        cleanupRoomResources()
                    }

                    is RoomEvent.ParticipantConnected -> {
//                        Log.i(TAG, "Participant joined: ${event.participant.identity}")
                        if (event.participant is RemoteParticipant) {
                            _events.tryEmit(GeoVisionEvent.ParticipantJoined(event.participant))
                        }
                    }

                    is RoomEvent.ParticipantAttributesChanged -> {
                        val participant = event.participant
                        val attributes = event.changedAttributes
//                        Log.i(
//                            TAG,
//                            "Participant attributes changed for ${participant.identity}: $attributes"
//                        )
                        _events.tryEmit(
                            GeoVisionEvent.ParticipantAttributesChanged(
                                participant, attributes
                            )
                        )
                    }

                    is RoomEvent.ParticipantDisconnected -> {
//                        Log.i(TAG, "Participant left: ${event.participant.identity}")
                        if (event.participant is RemoteParticipant) {
                            _events.tryEmit(GeoVisionEvent.ParticipantLeft(event.participant))
                        }
                    }

                    is RoomEvent.TrackPublished -> {
                        if (event.participant is LocalParticipant) {
//                            Log.i(
//                                TAG,
//                                "Local track published: ${event.publication.source} by ${event.participant.identity}"
//                            )
                            _events.tryEmit(
                                GeoVisionEvent.TrackPublished(
                                    event.publication, event.participant as LocalParticipant
                                )
                            )
                        }
                    }

                    is RoomEvent.TrackUnpublished -> {
//                        Log.i(
//                            TAG,
//                            "Track unpublished: ${event.publication.source}  from ${event.participant.identity}"
//                        )
                        if (event.participant is RemoteParticipant) {
                            _events.tryEmit(
                                GeoVisionEvent.TrackUnpublished(
                                    event.publication, event.participant as RemoteParticipant
                                )
                            )
                        }
                    }

                    is RoomEvent.TrackSubscribed -> {
//                        Log.i(
//                            TAG,
//                            "Remote track subscribed: ${event.publication.source} (${event.track.sid}) from ${event.participant.identity}"
//                        )
                        if (event.participant is RemoteParticipant) {
                            _events.tryEmit(
                                GeoVisionEvent.TrackSubscribed(
                                    event.track, event.publication, event.participant
                                )
                            )
                        }
                    }

                    is RoomEvent.TrackUnsubscribed -> {
//                        Log.i(
//                            TAG,
//                            "Remote track unsubscribed: ${event} (${event.track.sid}) from ${event.participant.identity}"
//                        )
                        if (event.participant is RemoteParticipant) {
                            _events.tryEmit(
                                GeoVisionEvent.TrackUnsubscribed(
                                    event.track, event.publications, event.participant
                                )
                            )
                        }
                    }

                    is RoomEvent.ConnectionQualityChanged -> {
//                        Log.i(
//                            TAG,
//                            "Connection Quality Changed : ${event.participant.identity} ${event.quality} "
//                        )
                        _events.tryEmit(
                            GeoVisionEvent.ConnectionQualityChanged(
                                event.quality, event.participant
                            )
                        )
                    }


                    is RoomEvent.ActiveSpeakersChanged -> {
                        val speakerIdentities = event.speakers.mapNotNull { it.identity }
//                        Log.i(TAG, "Active speakers changed: $speakerIdentities")
                        _events.tryEmit(GeoVisionEvent.ActiveSpeakersChanged(event.speakers))
                    }

                    is RoomEvent.DataReceived -> {
                        val senderId =
                            event.participant?.identity?.toString() // Can be null if sent by server directly
                        val topic = event.topic
                        try {
                            val message = event.data.toString(Charsets.UTF_8)
//                            Log.i(
//                                TAG,
//                                "DataReceived on topic '$topic' from ${senderId ?: "Server"}: $message"
//                            )
                            _events.tryEmit(
                                GeoVisionEvent.CustomMessageReceived(
                                    senderId, message, topic
                                )
                            )
                        } catch (e: Exception) {
//                            Log.e(TAG, "Error decoding DataReceived on topic '$topic'", e)
                            _events.tryEmit(
                                GeoVisionEvent.Error(
                                    "Failed to decode incoming data for topic '$topic'", e
                                )
                            )
                        }
                    }


                    is RoomEvent.TranscriptionReceived -> {
                        val participantId = event.participant?.identity?.toString()
//                        Log.i(
//                            TAG,
//                            "TranscriptionReceived from ${participantId ?: "Unknown Participant"}"
//                        )
                        event.transcriptionSegments.forEach { segment ->

                            val senderIdentity = segment.id ?: participantId ?: "Unknown Sender"
                            val text = segment.text
                            val isFinal =
                                segment.final // Assuming TranscriptionSegment has a 'final' property

//                            Log.i(
//                                TAG,
//                                "Transcription segment from $senderIdentity (final: $isFinal): \"$text\""
//                            )
                            _events.tryEmit(
                                GeoVisionEvent.TranscriptionReceived(
                                    senderIdentity, text, isFinal
                                )
                            )
                        }
                    }


                    else -> {
                        // Log unhandled events or ignore
                        Log.d(TAG, "Unhandled RoomEvent: ${event::class.java.simpleName}")
                    }
                }
            }
        }

//        Log.i(TAG, "Attempting to connect to LiveKit URL: $socketUrl")
        _events.tryEmit(GeoVisionEvent.Connecting(socketUrl, accessToken.takeLast(10)))

        sdkScope.launch {
            try {
                roomInstance.connect(
                    url = socketUrl,
                    token = accessToken,
                )
            } catch (e: RoomException.ConnectException) { // More specific exception for connection issues
//                Log.e(TAG, "Connection setup failed (ConnectException): ${e.message}", e)
                _events.tryEmit(GeoVisionEvent.Error("Connection setup failed: ${e.message}", e))
//                cleanupRoomResources()
            } catch (e: Exception) { // Generic fallback
//                Log.e(TAG, "Generic connection setup failed: ${e.message}", e)
                _events.tryEmit(GeoVisionEvent.Error("Connection setup failed: ${e.message}", e))
//                cleanupRoomResources()
            }
        }
    }

    private fun cleanupRoomResources() {
//        Log.d(TAG, "Cleaning up room resources.")
        roomEventsJob?.cancel() // Cancel the event collection coroutine
        roomEventsJob = null
        currentRoom?.release() // Release LiveKit room resources
        currentRoom = null

    }

    fun releaseRoomResources() {
        currentRoom?.release() // Release LiveKit room resources
        currentRoom = null
    }

    fun disconnectFromGeoVisionRoom() {
        val roomToDisconnect = currentRoom
        if (roomToDisconnect == null) {
//            Log.w(TAG, "Not connected to any room.")
            _events.tryEmit(GeoVisionEvent.Error("Not connected to any room.", null))
            return
        }
//        Log.i(TAG, "Disconnecting from room: ${roomToDisconnect.name}")
        sdkScope.launch {
//            setCameraEnabled(false) // Ensure camera is off before disconnecting
//            setMicrophoneEnabled(false) // Ensure microphone is off before disconnecting
            roomToDisconnect.disconnect()
            // The Disconnected event from roomInstance.events.collect will handle cleanup
        }
    }


    suspend fun setCameraEnabled(enable: Boolean): Boolean { // Made it suspend as LiveKit's setCameraEnabled is suspend
        val localParticipant = currentRoom?.localParticipant ?: run {
//            Log.w(TAG, "Cannot toggle camera: Not connected or local participant not found.")
            _events.tryEmit(GeoVisionEvent.Error("Cannot toggle camera: Not connected.", null))
            return false
        }

        return try {
//            Log.i(TAG, "Setting camera enabled: $enable")
            localParticipant.setCameraEnabled(enable)
            true // LiveKit's setCameraEnabled returns Unit, so we infer success if no exception
        } catch (e: Exception) {
//            Log.e(TAG, "Failed to set camera enabled: ${e.message}", e)
            _events.tryEmit(GeoVisionEvent.Error("Failed to set camera: ${e.message}", e))
            false
        }
    }

    suspend fun setMicrophoneEnabled(enable: Boolean): Boolean { // Made it suspend
        val localParticipant = currentRoom?.localParticipant ?: run {
//            Log.w(TAG, "Cannot toggle microphone: Not connected or local participant not found.")
            _events.tryEmit(GeoVisionEvent.Error("Cannot toggle microphone: Not connected.", null))
            return false
        }
        return try {
//            Log.i(TAG, "Setting microphone enabled: $enable")
            localParticipant.setMicrophoneEnabled(enable)
            true // LiveKit's setMicrophoneEnabled returns Unit
        } catch (e: Exception) {
//            Log.e(TAG, "Failed to set microphone enabled: ${e.message}", e)
            _events.tryEmit(GeoVisionEvent.Error("Failed to set microphone: ${e.message}", e))
            false
        }
    }

    fun isCameraEnabled(): Boolean {

//        Log.d(
//            TAG,
//            "Checking if camera is enabled ${currentRoom?.localParticipant?.isCameraEnabled()}"
//        )
        return currentRoom?.localParticipant?.isCameraEnabled() == true
    }

    fun isMicrophoneEnabled(): Boolean {
        return currentRoom?.localParticipant?.isMicrophoneEnabled() == true
    }

    fun getRemoteParticipants(): Map<Participant.Identity, RemoteParticipant> {
        return currentRoom?.remoteParticipants ?: emptyMap()
    }

    fun getLocalParticipant(): LocalParticipant? {
        return currentRoom?.localParticipant
    }

    fun getIsSpeaking(): Boolean {
//        Log.e(
//            "Vinay Remote Speaker",
//            currentRoom?.remoteParticipants?.values?.firstOrNull()?.name.toString()
//        )
        return currentRoom?.remoteParticipants?.values?.firstOrNull()?.isSpeaking ?: false
    }


    fun getCurrentroom(): Room? {
        return currentRoom
    }


    suspend fun sendFile(context: Context, file: File, topic: String = "send-file"): Boolean {
        val room = getCurrentroom()
        val localParticipant = room?.localParticipant
        if (localParticipant == null) {
            Log.e(
                TAG, "Cannot send image, not connected to a room or no local participant."
            )
            return false
        }


        if (!file.exists()) {
//            Log.e(
//                "VisionBotSDKManager",
//                "Failed to read file: ${file.absolutePath}. File does not exist."
//            )
            return false
        }

        var inputStream: InputStream? = null
        return try {
//            Log.d("VisionBotSDKManager", "Attempting to send file: ${file.name} on topic: $topic")

            val streamOptions = StreamBytesOptions(
                topic = topic,
                name = file.name,
                mimeType = context.contentResolver.getType(Uri.fromFile(file))
                    ?: "application/octet-stream",
                totalSize = file.length(),
                attributes = mapOf("fileName" to file.name, "fileSize" to file.length().toString()),
            )
//            Log.d("VisionBotSDKManager", "Stream options created: $streamOptions")

            val writer = localParticipant.streamBytes(streamOptions)

//            Log.d("VisionBotSDKManager", "Opened byte stream writer with ID: ${writer.info}")


            inputStream = file.inputStream() // Open InputStream from the temporary file

            withContext(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(4096) // Or another appropriate buffer size
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Write only the number of bytes read.
                        // If bytesRead is less than buffer.size, we send a smaller array.
                        writer.write(buffer.copyOf(bytesRead))
                    }
                    // The stream must be explicitly closed when you are done sending data [2]
                    writer.close()
//                    Log.d(
//                        "VisionBotSDKManager",
//                        "Image file stream sent and writer closed for: ${file.name}"
//                    )

                } catch (e: Exception) {
                    Log.e(
                        TAG, "Error writing image file to stream: ${e.message}", e
                    )
                    writer.close() // Attempt to close writer on error too
                    throw e
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendImageFile: ${e.message}", e)
            false
        } finally {
            inputStream?.close() // Ensure InputStream is closed
        }
    }

    fun shutdown() {
//        Log.i(TAG, "Shutting down VisionBotSDKManager.")

        // Use runBlocking to ensure disconnectFromGeoVisionRoom completes before cancellation
        kotlinx.coroutines.runBlocking {
            val roomToDisconnect = currentRoom
            if (roomToDisconnect != null) {
//                Log.i(TAG, "Disconnecting from room: ${roomToDisconnect.name}")
                setCameraEnabled(false) // Ensure camera is off before disconnecting
                setMicrophoneEnabled(false) // Ensure microphone is off before disconnecting

                roomToDisconnect.disconnect()
                cleanupRoomResources() // Ensure cleanup is called after disconnect
            } else {
//                Log.w(TAG, "Not connected to any room.")
                _events.tryEmit(GeoVisionEvent.Error("Not connected to any room.", null))
            }
        }

        sdkScope.cancel() // Cancel all coroutines started by this SDK
    }

}