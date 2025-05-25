# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep public classes in your SDK's main package
-keep class com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionEvent.** { *; }
-keep class com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager.** { *; }

# Required to prevent LiveKit / WebRTC from being stripped (if you're exposing them)
-keep class org.webrtc.** { *; }
-keep class io.livekit.** { *; }

# Optional: keep Kotlin metadata (helps with reflection)
-keep class kotlin.Metadata { *; }

# Keep annotations
-keep @interface **.**