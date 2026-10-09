# Consumer ProGuard rules — bundled into the AAR and applied automatically when
# a consuming app runs R8/ProGuard.
#
# Intentionally empty. Do not add blanket `-keep class … { *; }` rules here:
# they apply to every consuming app and stop R8 from shrinking/optimizing that code.
#
# - SDK public API (VisionBotSDKManager, GeoVisionEvent): called directly by consumers,
#   so R8 traces it. No reflection is used on these types.
# - LiveKit / WebRTC: livekit-android bundles its own consumer rules (JNI
#   @CalledByNative callbacks, kotlinx.serialization, protobuf, SDP parser).
#   WebRTC classes live under livekit.org.webrtc.*, not org.webrtc.*.
#
# Only add a rule here for code the SDK reaches via reflection or JNI, and keep it narrow.
