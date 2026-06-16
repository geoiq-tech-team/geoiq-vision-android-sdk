package com.geoiq.geoiq_android_lk_vision_bot_sdk

// Re-exports LiveKit types under the SDK's own package so that consumers never need a
// direct dependency on livekit internals — a single com.geoiq.* import covers everything.
//
// Most aliases share the LiveKit name (identity re-export). Exceptions:
//   GeoVisionRoomOptions  → avoids clashing with livekit.RoomOptions in consumer imports.
//   audioTrackPublishDefaults / videoTrackPublishDefaults  → lowercase names preserved for
//       binary compatibility with v1.0.8 of the published SDK; do not rename.

import io.livekit.android.RoomOptions
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.rpc.RpcError

// ── Track types ───────────────────────────────────────────────────────────────
typealias Track = Track
typealias VideoTrack = VideoTrack
typealias LocalVideoTrack = LocalVideoTrack
typealias DataPublishReliability = DataPublishReliability

// ── Participant types ──────────────────────────────────────────────────────────
typealias LocalParticipant = LocalParticipant
typealias RemoteParticipant = RemoteParticipant
typealias ConnectionQuality = ConnectionQuality

// ── RPC ───────────────────────────────────────────────────────────────────────
typealias RpcError = RpcError

// ── Camera ────────────────────────────────────────────────────────────────────
typealias CameraPosition = CameraPosition

// ── Track & publish options ───────────────────────────────────────────────────
typealias LocalAudioTrackOptions = LocalAudioTrackOptions
typealias LocalVideoTrackOptions = LocalVideoTrackOptions
typealias audioTrackPublishDefaults = AudioTrackPublishDefaults
typealias videoTrackPublishDefaults = VideoTrackPublishDefaults

// ── Room options ──────────────────────────────────────────────────────────────
typealias GeoVisionRoomOptions = RoomOptions
