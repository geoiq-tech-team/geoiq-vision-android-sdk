# GeoIQ Android Vision Bot SDK

This repository contains:

* ✅ **GeoIQ Android SDK** to interact for real-time audio/video communication and event management.
* 📱 **Sample Android app** built using Jetpack Compose demonstrating SDK usage and integration.

---

## 📆 SDK Features

The `VisionBotSDKManager` provides:

* 🔌 Connect/disconnect to a Room with ease.
* 📵 Camera and 🎤 microphone control.
* 👤 Participant and track management (remote & local).
* 📡 Send and receive custom data messages.
* 🚣️ Real-time transcription event support.
* 📍 Kotlin `SharedFlow`-based event stream.

---

## 🧱‍💻 SDK Installation (via JitPack)

The SDK is available via [JitPack](https://jitpack.io/#geoiq-tech-team/geoiq-vision-android-sdk).

### Step 1: Add JitPack to your root `build.gradle`:

<details>
<summary>Groovy</summary>

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

</details>

<details>
<summary>Kotlin DSL</summary>

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

### 1. Connect to Room

```kotlin
VisionBotSDKManager.connectToGeoVisionRoom(
    context = applicationContext,
    socketUrl = "wss://your-...-server",
    accessToken = "your-jwt-token"
)
```

### 2. Listen to Events

```kotlin
lifecycleScope.launch {
    VisionBotSDKManager.events.collect { event ->
        when (event) {
            is GeoVisionEvent.Connected -> { /* Room connected */ }
            is GeoVisionEvent.ParticipantJoined -> { /* New participant */ }
            is GeoVisionEvent.CustomMessageReceived -> { /* Data received */ }
            is GeoVisionEvent.TranscriptionReceived -> { /* Transcription text */ }
        }
    }
}
```

### 3. Media Controls

```kotlin
VisionBotSDKManager.setCameraEnabled(true)
VisionBotSDKManager.setMicrophoneEnabled(false)
```

### 4. Disconnect

```kotlin
VisionBotSDKManager.disconnectFromGeoVisionRoom()
```

---

## 📱 Sample App

The sample Jetpack Compose app shows real-time audio/video & transcription integration using the SDK.

### Run Locally

1. Clone the repo:

   ```bash
   git clone https://github.com/geoiq-tech-team/geoiq-vision-android-sdk.git
   cd geoiq-vision-android-sdk
   ```

2. Open in Android Studio.

3. Update the `socketUrl` and `accessToken` in `MainActivity.kt`.

4. Run on emulator/device.

---

## 📂 Repo Structure

```
🔹 sample-app/                        # Jetpack Compose Android demo app
└── MainActivity.kt
🔹 sdk/                              # VisionBot SDK source
└── VisionBotSDKManager.kt
🔹 README.md
```

---

## 🛠️ Built With

* Kotlin Coroutines & SharedFlow
* Jetpack Compose UI
* JitPack for distribution

---
