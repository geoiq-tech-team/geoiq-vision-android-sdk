# SDK module ProGuard rules.
# NOTE: isMinifyEnabled = false in build.gradle.kts, so this file does NOT run today.
# It is kept accurate so enabling minification in the future is safe.
# Rules that affect SDK consumers live in consumer-rules.pro.

# ── SDK public API ────────────────────────────────────────────────────────────
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager { public *; }
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager$Companion { public *; }
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionEvent { *; }
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionEvent$* { *; }

# ── WebRTC JNI ────────────────────────────────────────────────────────────────
# Native code calls back into these; stripping breaks audio/video at runtime.
-keepclasseswithmembernames class org.webrtc.** { native <methods>; }
-keepclassmembers class org.webrtc.** {
    @org.webrtc.CalledByNative *;
    @org.webrtc.CalledByNativeUnchecked *;
}
-keep class org.webrtc.PeerConnectionFactory { *; }
-keep class org.webrtc.EglBase { *; }
-keep class org.webrtc.SurfaceViewRenderer { *; }

# ── LiveKit — only the types exposed via public type aliases ──────────────────
-keep class io.livekit.android.LiveKit { *; }
-keep class io.livekit.android.RoomOptions { *; }
-keep class io.livekit.android.room.Room { *; }
-keep class io.livekit.android.room.Room$State { *; }
-keep class io.livekit.android.room.participant.LocalParticipant { *; }
-keep class io.livekit.android.room.participant.RemoteParticipant { *; }
-keep class io.livekit.android.room.participant.ConnectionQuality { *; }
-keep class io.livekit.android.room.participant.AudioTrackPublishDefaults { *; }
-keep class io.livekit.android.room.participant.VideoTrackPublishDefaults { *; }
-keep class io.livekit.android.room.track.Track { *; }
-keep class io.livekit.android.room.track.VideoTrack { *; }
-keep class io.livekit.android.room.track.LocalVideoTrack { *; }
-keep class io.livekit.android.room.track.LocalVideoTrackOptions { *; }
-keep class io.livekit.android.room.track.LocalAudioTrackOptions { *; }
-keep class io.livekit.android.room.track.CameraPosition { *; }
-keep class io.livekit.android.room.track.DataPublishReliability { *; }
-keep class io.livekit.android.rpc.RpcError { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ── Suppress known-safe warnings ─────────────────────────────────────────────
-dontwarn com.sun.nio.sctp.**
