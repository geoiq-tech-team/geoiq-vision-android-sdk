# Consumer ProGuard rules — bundled into the AAR and applied automatically when
# a consuming app runs R8/ProGuard.
#
# No keep rules. Do not add blanket `-keep class … { *; }` rules here:
# they apply to every consuming app and stop R8 from shrinking/optimizing that code.
#
# - SDK public API (VisionBotSDKManager, GeoVisionEvent): called directly by consumers,
#   so R8 traces it. No reflection is used on these types.
# - LiveKit / WebRTC: livekit-android bundles its own consumer rules (JNI
#   @CalledByNative callbacks, kotlinx.serialization, protobuf, SDP parser).
#   WebRTC classes live under livekit.org.webrtc.*, not org.webrtc.*.
#
# Only add a rule here for code the SDK reaches via reflection or JNI, and keep it narrow.

# ── Missing classes from LiveKit's bundled SIP stack ─────────────────────────
# android.gov.nist.javax.sip.stack.sctp.* references the JDK-only com.sun.nio.sctp API,
# which doesn't exist on Android. R8 in AGP 8.x fails the build on these ("Missing class
# com.sun.nio.sctp.…") even though the code is never reached; AGP 9's R8 tolerates it,
# so the sample app won't catch a regression — Lenskart (AGP 8.13) will.
-dontwarn com.sun.nio.sctp.MessageInfo
-dontwarn com.sun.nio.sctp.NotificationHandler
-dontwarn com.sun.nio.sctp.SctpChannel
-dontwarn com.sun.nio.sctp.SctpServerChannel
