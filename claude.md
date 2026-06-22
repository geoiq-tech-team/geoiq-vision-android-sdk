# GeoIQ Vision Android SDK — Project Context

## Project Overview

This repo is a LiveKit-based Android SDK + sample app for real-time audio/video communication with an AI Vision Bot.

- **SDK module:** `GEOIQ-ANDROID-LK-VISION-BOT-SDK/`
- **Sample app:** `app/`
- **Distributed via:** JitPack (`com.github.geoiq-tech-team:geoiq-vision-android-sdk:TAG`)
- **Active branch:** `Sayak/Refactoring-SDK`

---

## Current State

Refactoring complete and committed. All video rendering bugs fixed. README updated.

### SDK Module — 3 files

| File | Contents |
|---|---|
| `VisionBotSDKManager.kt` | Singleton: connection, media controls, renderer registry, file streaming |
| `GeoVisionEvent.kt` | Sealed event hierarchy with full KDoc |
| `GeoVisionTypeAliases.kt` | All LiveKit type re-exports under the SDK package |

### Sample App — 3 files (was 1 monolithic file, 687 lines)

| File | Contents |
|---|---|
| `MainActivity.kt` | 39 lines — permissions + `setContent` only |
| `SDKInteractionScreen.kt` | All Compose UI: VideoPreviewCard, StatusCard, ConnectionControls, MediaControls, EventLogCard |
| `MainViewModel.kt` | All state + SDK event collection + `rendererSession` counter |

---

## Key Decisions & Constraints

### CRITICAL: No direct LiveKit imports in the app/client
Consumers (including the sample app) must ONLY use type aliases from `com.geoiq.geoiq_android_lk_vision_bot_sdk`. No exceptions.

### Type alias naming quirks (do not rename — binary compat)
- `GeoVisionRoomOptions` → maps to `RoomOptions` (avoids clash)
- `GeoVisionRoom` → maps to `Room`
- `GeoVisionRoomState` → maps to `Room.State`
- `audioTrackPublishDefaults` → lowercase, maps to `AudioTrackPublishDefaults`
- `videoTrackPublishDefaults` → lowercase, maps to `VideoTrackPublishDefaults`

### Renderer registry
`VisionBotSDKManager` uses a `WeakHashMap`-backed registry to track renderers — prevents leaks across sessions.

---

## Bugs Fixed (Video Rendering)

Three interconnected bugs in the reconnect flow, all now fixed:

### 1. Video not showing intermittently
- **Cause:** `LaunchedEffect` keyed only on `localVideoTrack` — didn't re-fire when renderer was replaced.
- **Fix:** Dual-key `LaunchedEffect(localVideoTrack, rendererRef.value)` in `SDKInteractionScreen`.

### 2. Frozen frame after reconnect
- **Cause:** Re-using same `SurfaceViewRenderer` after `release()` + `init()` — `surfaceCreated` doesn't re-fire, EGL has no bound surface, last compositor buffer stays frozen.
- **Fix:** `key(viewModel.rendererSession)` wraps `VideoPreviewCard`. `rendererSession` increments on every `GeoVisionEvent.Disconnected`, forcing a fresh renderer each session.

### 3. Camera feed broken after renderer re-creation
- **Cause:** `onRelease` was setting `rendererRef.value = null`. Compose runs the new `factory` **before** old `onRelease` when `key()` changes — null overwrote the newly-created renderer.
- **Fix:** Removed null assignment from `onRelease`; it only calls `it.release()`.

---

## Code Snippets

### Renderer pattern in `SDKInteractionScreen.kt`
```kotlin
// Dual-key LaunchedEffect — re-fires when either track or renderer changes
LaunchedEffect(localVideoTrack, rendererRef.value) {
    val renderer = rendererRef.value ?: return@LaunchedEffect
    if (localVideoTrack != null) {
        VisionBotSDKManager.initializeVideoRenderer(renderer)
        localVideoTrack.addRenderer(renderer)
    }
}

// key() forces fresh SurfaceViewRenderer on each reconnect
key(viewModel.rendererSession) {
    VideoPreviewCard(rendererRef, viewModel.isCameraEnabled)
}

// onRelease must NOT null out rendererRef — Compose calls factory BEFORE onRelease on key change
AndroidView(
    factory = { ctx ->
        SurfaceViewRenderer(ctx).apply { rendererRef.value = this ... }
    },
    onRelease = { it.release() }  // no rendererRef.value = null here
)
```

### rendererSession counter in `MainViewModel.kt`
```kotlin
var rendererSession by mutableIntStateOf(0)
    private set

// Inside Disconnected event handler:
rendererSession++
```

---

## Tech Stack

- Kotlin + Coroutines + `SharedFlow`
- Jetpack Compose (Material3)
- LiveKit Android SDK (wrapped via type aliases)
- `AndroidViewModel` / `viewModelScope`
- JitPack for distribution

---

## Endpoints (Sample App)

- **Token server:** `https://lk-va-token.diq.geoiq.ai/stg/v1/token`
- **LiveKit server:** `wss://lk-stg4.diq.geoiq.ai`
- **Auth header:** `x-api-key` (value in `MainViewModel.xApiKey`)

---

## Next Steps

- [x] PR raised: `Sayak/Refactoring-SDK` → `main`
- [ ] Consider publishing a new JitPack release tag after merge

---

## Known Issues / Watch-outs

- `MainViewModel.kt` hardcodes `xApiKey`, `geoVisionUrl`, and `buildTokenMetadata()` — these need to be updated per environment before running.
- `syncConnectionStateOnInit()` reads `VisionBotSDKManager.currentRoom` to recover state if ViewModel is recreated while connected (e.g. screen rotation).
- `GeoVisionEvent.events` has `replay = 1` — late collectors always get the last event immediately. Be careful not to act on stale events (e.g. a stale `Connected` after config change).
