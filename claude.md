# GeoIQ Vision Android SDK — Project Context

## Project Overview

This repo is a LiveKit-based Android SDK + sample app for real-time audio/video communication with an AI Vision Bot.

- **SDK module:** `GEOIQ-ANDROID-LK-VISION-BOT-SDK/`
- **Sample app:** `app/`
- **Distributed via:** JitPack (`com.github.geoiq-tech-team:geoiq-vision-android-sdk:TAG`)
- **Active branch:** `siva/refactor`

### Local build setup

- `local.properties` is **not tracked** — each developer creates their own with `sdk.dir=/Users/<you>/Library/Android/sdk`. Android Studio generates it on first open.
- Requires **JDK 17+** (AGP requirement). Set `org.gradle.java.home` in your *user-level* `~/.gradle/gradle.properties`, not in the repo, so no machine path is committed.
- `.gradle/` and `build/` are untracked as of `eba0660`.

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

### Prefer the type aliases where they exist (but they are NOT isolation)

Consumers should prefer aliases from `com.geoiq.geoiq_android_lk_vision_bot_sdk` over raw
`io.livekit.*` imports — but be clear about what this does and doesn't buy:

- **What actually lets consumers skip the LiveKit dependency:** `api("io.livekit:livekit-android")`
  in the SDK's `build.gradle.kts`, not the aliases. `api` puts LiveKit on every consumer's
  compile classpath transitively.
- **Typealiases provide zero encapsulation.** They expand at compile time — `com.geoiq…Track`
  *is* `io.livekit…Track`, same class, same bytecode. Nothing is wrapped or swappable.
- **The alias set is incomplete, so "no direct imports" is not achievable today.** Missing:
  `Participant`, `TrackPublication`, `LocalTrackPublication`, `RoomException`,
  `SurfaceViewRenderer`, `TextureViewRenderer`, `RendererCommon`, `StreamBytesOptions`.
  The sample app is forced to import two of these directly
  (`SDKInteractionScreen.kt:56-57`) to use `initializeVideoRenderer()`.

Real isolation would require wrapper types (a genuine facade), not aliases. That is a large
change and probably not worth it unless the transport is ever swapped.

### Type alias naming quirks (do not rename — SOURCE compat)
- `GeoVisionRoomOptions` → maps to `RoomOptions` (avoids clash)
- `GeoVisionRoom` → maps to `Room`
- `GeoVisionRoomState` → maps to `Room.State`
- `audioTrackPublishDefaults` → lowercase, maps to `AudioTrackPublishDefaults`
- `videoTrackPublishDefaults` → lowercase, maps to `VideoTrackPublishDefaults`

> Note: typealiases have **no bytecode representation** — they live only in Kotlin metadata.
> Renaming breaks **source** compatibility for consumers who recompile; already-compiled
> consumer bytecode is unaffected. The "binary compat" wording in the header comment of
> `GeoVisionTypeAliases.kt` is inaccurate. The conclusion (don't rename) still stands, and
> the lowercase names violate Kotlin type-naming conventions — candidates for a
> `@Deprecated` alias pointing at properly-cased names at the next major version.

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
        SurfaceViewRenderer(ctx).apply { rendererRef.value = this }
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
- LiveKit Android SDK (exposed to consumers as well)
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
