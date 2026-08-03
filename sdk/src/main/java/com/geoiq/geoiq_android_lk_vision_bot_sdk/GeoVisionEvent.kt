package com.geoiq.geoiq_android_lk_vision_bot_sdk

import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication

/**
 * All events emitted by [VisionBotSDKManager].
 *
 * Collect from [VisionBotSDKManager.events] in a coroutine scope tied to your UI lifecycle:
 *
 * ```kotlin
 * lifecycleScope.launch {
 *     VisionBotSDKManager.events.collect { event ->
 *         when (event) {
 *             is GeoVisionEvent.Connected -> { ... }
 *             is GeoVisionEvent.Error     -> { ... }
 *             else -> {}
 *         }
 *     }
 * }
 * ```
 *
 * The flow has `replay = 1`, so a new collector always receives the most recent event
 * immediately — useful when your UI subscribes after the room is already connected.
 *
 * @author Sayak Mondal
 */
sealed interface GeoVisionEvent {

    /** Emitted immediately when [VisionBotSDKManager.connectToGeoVisionRoom] is called,
     *  before the WebRTC handshake begins. [tokenSnippet] is the last 10 chars of the
     *  access token — safe to log, never the full token. */
    data class Connecting(val url: String, val tokenSnippet: String) : GeoVisionEvent

    /** Emitted once the room is fully established and the local participant is ready.
     *  After this event it is safe to enable camera/microphone. */
    data class Connected(val roomName: String, val localParticipant: LocalParticipant) :
        GeoVisionEvent

    /** Emitted when the room disconnects for any reason.
     *  [reason] is "Client initiated" for a normal disconnect, or an error message
     *  if the server dropped the connection. */
    data class Disconnected(val reason: String?) : GeoVisionEvent

    data class ParticipantJoined(val participant: RemoteParticipant) : GeoVisionEvent

    data class ParticipantLeft(val participant: RemoteParticipant) : GeoVisionEvent

    /** Emitted when the server confirms the local participant's own published track is
     *  live and accessible. Fires after [TrackPublished]; prefer this as the reliable
     *  signal that the local stream is actually flowing. */
    data class LocalTrackSubscribed(
        val publication: LocalTrackPublication, val participant: LocalParticipant
    ) : GeoVisionEvent

    /** Emitted when a local track is added to the room (sent to the server).
     *  The track may not be live yet — wait for [LocalTrackSubscribed] to confirm. */
    data class TrackPublished(
        val publication: TrackPublication, val participant: LocalParticipant
    ) : GeoVisionEvent

    /** Emitted when a remote participant stops publishing a track. */
    data class TrackUnpublished(
        val publication: TrackPublication, val participant: RemoteParticipant
    ) : GeoVisionEvent

    /** Emitted when a remote track becomes available for rendering or playback. */
    data class TrackSubscribed(
        val track: Track, val publication: TrackPublication, val participant: RemoteParticipant
    ) : GeoVisionEvent

    data class TrackUnsubscribed(
        val track: Track, val publication: TrackPublication, val participant: RemoteParticipant
    ) : GeoVisionEvent

    data class ConnectionQualityChanged(
        val quality: ConnectionQuality, val participant: Participant
    ) : GeoVisionEvent

    data class ActiveSpeakersChanged(val speakers: List<Participant>) : GeoVisionEvent

    /** [exception] may be null for SDK-originated errors that have no underlying exception. */
    data class Error(val message: String, val exception: Throwable?) : GeoVisionEvent

    data class ParticipantAttributesChanged(
        val participant: Participant, val changedAttributes: Map<String, String>
    ) : GeoVisionEvent

    /** Emitted once per transcription segment.
     *  [isFinal] = false → segment is still being dictated (interim result).
     *  [isFinal] = true  → segment is complete and will not be updated again. */
    data class TranscriptionReceived(
        val senderId: String?, val message: String?, val isFinal: Boolean = false
    ) : GeoVisionEvent

    /** Emitted for both incoming text-stream messages (on the SDK's internal topic) and
     *  legacy binary DataReceived messages (decoded as UTF-8). [topic] identifies the
     *  source channel. [isCritical] is reserved for future use and is always false. */
    data class CustomMessageReceived(
        val senderId: String?,
        val message: String?,
        val topic: String?,
        val isCritical: Boolean = false
    ) : GeoVisionEvent
}
