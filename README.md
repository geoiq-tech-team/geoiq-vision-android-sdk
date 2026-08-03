# GeoIQ Android Vision Bot SDK

This repository contains:

* **GeoIQ Android SDK** — real-time audio/video communication, event management, and file streaming.
* **Sample Android app** — a Jetpack Compose demo app showing a complete integration of the SDK.

---

## SDK Features

`VisionBotSDKManager` provides:

* Connect/disconnect to a LiveKit room with a single call.
* Camera and microphone controls (enable/disable, front/back flip).
* Local video renderer initialisation and lifecycle management.
* Participant and track management (local and remote).
* File streaming to all room participants via LiveKit byte streams.
* Send and receive custom data messages over named topics.
* Real-time transcription event support.
* Kotlin `SharedFlow`-based event stream with replay, safe for late subscribers.

---

## SDK Installation (via JitPack)

The SDK is available via [JitPack](https://jitpack.io/#geoiq-tech-team/geoiq-vision-android-sdk).

### Step 1: Add JitPack to your repository settings

<details>
<summary>Groovy (settings.gradle)</summary>

```groovy
dependencyResolutionManagement {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

</details>

<details>
<summary>Kotlin DSL (settings.gradle.kts)</summary>

```kotlin
dependencyResolutionManagement {
    repositories {
        ...
        maven("https://jitpack.io")
    }
}
```

</details>

---

### Step 2: Add the SDK dependency to your app-level `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.geoiq-tech-team:geoiq-vision-android-sdk:TAG'
}
```

> 🔖 Replace `TAG` with the latest release version from [JitPack releases](https://jitpack.io/#geoiq-tech-team/geoiq-vision-android-sdk).

---

## 🔌 Quick Start

### 1. Connect to a Room

```kotlin
import com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager
import com.geoiq.geoiq_android_lk_vision_bot_sdk.GeoVisionRoomOptions
import com.geoiq.geoiq_android_lk_vision_bot_sdk.LocalVideoTrackOptions
import com.geoiq.geoiq_android_lk_vision_bot_sdk.CameraPosition

val options = GeoVisionRoomOptions(
    videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT)
)

VisionBotSDKManager.connectToGeoVisionRoom(
    context = applicationContext,
    socketUrl = "wss://your-...-server",
    accessToken = "your-jwt-token",
    roomOptions = options         // optional, defaults to GeoVisionRoomOptions()
)
```

Connection is asynchronous. Listen to the event stream for the result:

* `GeoVisionEvent.Connecting` — emitted immediately when `connectToGeoVisionRoom` is called.
* `GeoVisionEvent.Connected` — room is ready; safe to enable camera/microphone.
* `GeoVisionEvent.Error` — connection failed.

---

### 2. Listen to Events

```kotlin
lifecycleScope.launch {
    VisionBotSDKManager.events.collect { event ->
        when (event) {
            is GeoVisionEvent.Connecting   -> { /* handshake started */ }
            is GeoVisionEvent.Connected    -> { /* room ready */ }
            is GeoVisionEvent.Disconnected -> { /* session ended */ }
            is GeoVisionEvent.ParticipantJoined -> { /* remote participant arrived */ }
            is GeoVisionEvent.ParticipantLeft   -> { /* remote participant left */ }
            is GeoVisionEvent.LocalTrackSubscribed -> { /* local track is live */ }
            is GeoVisionEvent.TrackPublished    -> { /* local track sent to server */ }
            is GeoVisionEvent.TrackUnpublished  -> { /* local track removed */ }
            is GeoVisionEvent.TrackSubscribed   -> { /* remote track available */ }
            is GeoVisionEvent.TrackUnsubscribed -> { /* remote track gone */ }
            is GeoVisionEvent.ConnectionQualityChanged -> { /* signal quality update */ }
            is GeoVisionEvent.ActiveSpeakersChanged    -> { /* speaking participants changed */ }
            is GeoVisionEvent.ParticipantAttributesChanged -> { /* e.g. agent state */ }
            is GeoVisionEvent.TranscriptionReceived -> { /* speech-to-text segment */ }
            is GeoVisionEvent.CustomMessageReceived -> { /* data / text stream message */ }
            is GeoVisionEvent.Error -> { /* SDK or connection error */ }
        }
    }
}
```

The flow has `replay = 1`, so a new collector always receives the most recent event immediately — safe to subscribe after the room is already connected.

---

### 3. Media Controls

```kotlin
// Camera
VisionBotSDKManager.setCameraEnabled(true)   // suspend fun — call from a coroutine
VisionBotSDKManager.setCameraEnabled(false)

// Microphone
VisionBotSDKManager.setMicrophoneEnabled(true)
VisionBotSDKManager.setMicrophoneEnabled(false)

// Flip between front and back camera
val flipped = VisionBotSDKManager.flipCameraPosition()  // suspend fun; returns true on success

// Query current state (non-suspend)
val cameraOn = VisionBotSDKManager.isCameraEnabled()
val micOn    = VisionBotSDKManager.isMicrophoneEnabled()
```

---

### 4. Video Rendering

Use `initializeVideoRenderer` before attaching a `LocalVideoTrack` to a renderer. The call is idempotent — safe to call multiple times on the same renderer instance.

```kotlin
// In a Jetpack Compose AndroidView factory:
AndroidView(
    factory = { ctx ->
        SurfaceViewRenderer(ctx).apply {
            VisionBotSDKManager.initializeVideoRenderer(this)
            localVideoTrack?.addRenderer(this)
        }
    },
    onRelease = { it.release() }
)
```

Both `SurfaceViewRenderer` and `TextureViewRenderer` are supported.

---

### 5. Send a File

```kotlin
val success = VisionBotSDKManager.sendFile(
    context = applicationContext,
    file    = fileToSend,         // java.io.File
    topic   = "send-file"         // optional; identifies the channel for the receiver
)
```

File data is streamed in 4 KB chunks on `Dispatchers.IO` — safe to call from any coroutine context.

---

### 6. Disconnect

```kotlin
VisionBotSDKManager.disconnectFromGeoVisionRoom()
// GeoVisionEvent.Disconnected fires when the server confirms.
```

To fully tear down the SDK (e.g. from `Activity.onDestroy`):

```kotlin
VisionBotSDKManager.shutdown()
// Do not call any SDK methods after this.
```

---

## Sample App

The `app/` module is a self-contained Jetpack Compose app demonstrating the full SDK lifecycle.

### Run Locally

1. Clone the repo:

   ```bash
   git clone https://github.com/geoiq-tech-team/geoiq-vision-android-sdk.git
   cd geoiq-vision-android-sdk
   ```

2. Create `local.properties` in the project root pointing at **your own** Android SDK:

   ```properties
   sdk.dir=/Users/<you>/Library/Android/sdk
   ```

   This file is git-ignored and intentionally not tracked — it holds a machine-specific
   path, so each developer maintains their own. Opening the project in Android Studio
   generates it automatically.

3. Ensure Gradle runs on **JDK 17 or newer** (the Android Gradle Plugin requires it).
   Android Studio's bundled JDK works out of the box. For command-line builds, set it
   in your *user-level* `~/.gradle/gradle.properties` so no machine path is committed:

   ```properties
   org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
   ```

4. Open in Android Studio.

5. Update `xApiKey`, `geoVisionUrl`, and `buildTokenMetadata()` in `app/.../MainViewModel.kt` with your server credentials.

6. Run on a physical device or emulator (camera/microphone require a real device for best results).

> **Upgrading from an older clone?** `local.properties`, `.gradle/`, and `build/` used to be
> tracked in git and were removed. Pulling this change deletes your local copies. Gradle
> regenerates `.gradle/` and `build/` on the next build, but you must recreate
> `local.properties` yourself using step 2 above — otherwise the build fails with
> *"SDK location not found."*

---

## Performance

The SDK ships with a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview) that pre-compiles critical code paths (SDK init, LiveKit renderer setup, WebRTC EGL surface creation) ahead of time. This means consumers get optimised startup performance from the very first app launch — no warmup runs needed.

**Cold start improvement (measured on POCO 25028PC03I, Android 15):**

| Compilation mode | Time to initial display |
|---|---|
| No compilation (first install, no profile) | 885 ms |
| With baseline profile | 677 ms (**-23.5%**) |

The profile is bundled inside the AAR and merged into the consumer app's profile automatically by AGP at build time. The SDK includes `profileinstaller` as a transitive dependency, so no additional setup is required.

### Running benchmarks

The `benchmark/` module contains macrobenchmark tests to measure startup and frame timing. Run on a physical device for reliable results:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.geoiq.benchmark.StartupBenchmark
```

To run a single test:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.geoiq.benchmark.StartupBenchmark#startupBaselineProfile
```

Results are written to `benchmark/build/outputs/connected_android_test_additional_output/`.

### Regenerating the baseline profile

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.geoiq.benchmark.BaselineProfileGenerator
```

The generated profile is at `benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/BaselineProfileGenerator_generate-startup-prof.txt`. Filter for SDK-relevant classes and copy to `sdk/src/main/baseline-prof.txt`.

---

## Repo Structure

```
sdk/                                    # SDK module
├── VisionBotSDKManager.kt              # Singleton SDK entry point
├── GeoVisionEvent.kt                   # Sealed event hierarchy
├── GeoVisionTypeAliases.kt             # Re-exported LiveKit type aliases
└── baseline-prof.txt                   # Baseline profile (ships in AAR)

app/                                    # Sample Jetpack Compose app
├── MainActivity.kt                     # Entry point, permission handling
├── VoiceScreen.kt                      # Video UI: preview, controls, event log
└── MainViewModel.kt                    # State holder, event collection, SDK calls

benchmark/                              # Macrobenchmark module
├── StartupBenchmark.kt                 # Cold start timing across compilation modes
├── FrameTimingBenchmark.kt             # Frame timing during scroll interactions
└── BaselineProfileGenerator.kt         # Generates baseline profile rules

README.md
```

---

## 🛠️ Built With

* Kotlin Coroutines & `SharedFlow`
* Jetpack Compose UI
* JitPack for distribution

---
