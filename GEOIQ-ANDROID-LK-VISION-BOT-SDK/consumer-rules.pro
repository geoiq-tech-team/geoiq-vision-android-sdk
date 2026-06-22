# Consumer ProGuard rules — bundled into the AAR and applied automatically when
# a consuming app runs R8/ProGuard. These are the rules that protect SDK users.

# ── GeoIQ Vision SDK public API ───────────────────────────────────────────────
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager { public *; }
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionEvent { *; }
-keep public class com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionEvent$* { *; }

# ── WebRTC JNI ────────────────────────────────────────────────────────────────
# Native code calls back into these; stripping them breaks audio/video at runtime.
-keepclasseswithmembernames class org.webrtc.** { native <methods>; }
-keepclassmembers class org.webrtc.** {
    @org.webrtc.CalledByNative *;
    @org.webrtc.CalledByNativeUnchecked *;
}
-keep class org.webrtc.EglBase { *; }
-keep class org.webrtc.SurfaceViewRenderer { *; }

# ── LiveKit — only the types exposed via public type aliases ──────────────────
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
-keep class io.livekit.android.RoomOptions { *; }
-keep class io.livekit.android.rpc.RpcError { *; }

# ── Kotlin metadata ───────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes Signature

# ── Suppress known-safe warnings ─────────────────────────────────────────────
-dontwarn com.sun.nio.sctp.MessageInfo
-dontwarn com.sun.nio.sctp.NotificationHandler
-dontwarn com.sun.nio.sctp.SctpChannel
-dontwarn com.sun.nio.sctp.SctpServerChannel
