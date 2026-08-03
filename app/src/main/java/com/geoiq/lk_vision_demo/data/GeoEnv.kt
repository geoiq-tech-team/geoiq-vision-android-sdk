package com.geoiq.lk_vision_demo.data

import com.geoiq.lk_vision_demo.BuildConfig
import com.geoiq.lk_vision_demo.SessionMode

/**
 * Per-mode environment resolution.
 *
 * Voice and chat are served by *different agents on different LiveKit deployments*, so each mode
 * resolves its own socket. Sharing one endpoint is what makes a chat session behave like a voice
 * session (the voice agent publishes TTS audio and expects a microphone).
 *
 * Each mode picks between two sources:
 * - **Production** — a fixed, dedicated deployment per mode. Keys come from `local.properties`
 *   (gitignored) because this repository is public:
 *   ```
 *   geoiq.voice.prod.apiKey=<voice prod key>
 *   geoiq.chat.prod.apiKey=<chat prod key>
 *   ```
 * - **Staging** — a user-editable socket + key pair, for pointing at any staging host.
 *
 * The token host is always derived from the resolved socket, mirroring the consumer app's
 * `GeoIQConfigHelper.resolveBaseUrlForSocket`. Keeping the key paired with its socket is what
 * prevents the mismatch the old single-key config allowed.
 */
object GeoEnv {

    /** Dedicated voice deployment (`b_voice_assist`). */
    const val SOCKET_VOICE_PROD = "wss://lk-prod.diq.geoiq.ai"

    /** Dedicated chat deployment (`b_chat_assist`) — deliberately a different host. */
    const val SOCKET_CHAT_PROD = "wss://b-assist-lk-prod.diq.geoiq.ai"

    const val TOKEN_BASE_URL_PROD = "https://lk-va-prod-token.diq.geoiq.ai/prod"
    const val TOKEN_BASE_URL_STAGING = "https://lk-va-token.diq.geoiq.ai/stg"

    const val PATH_TOKEN = "/v1/token"
    const val PATH_SERVICE_UP = "/v1/serviceup"

    /** Starting point for the editable staging entry, paired with [DEFAULT_STAGING_API_KEY]. */
    const val DEFAULT_STAGING_SOCKET = "wss://lk-stg4.diq.geoiq.ai"

    /** Staging only — safe to keep in source. */
    const val DEFAULT_STAGING_API_KEY = "eyshaG9sbGVzX2Fwa1DopV9rCV12FwaV9rZXk6cassmmjas"

    fun prodSocketUrl(mode: SessionMode): String = when (mode) {
        SessionMode.Video -> SOCKET_VOICE_PROD
        SessionMode.Chat -> SOCKET_CHAT_PROD
    }

    fun prodApiKey(mode: SessionMode): String = when (mode) {
        SessionMode.Video -> BuildConfig.GEO_VOICE_PROD_API_KEY
        SessionMode.Chat -> BuildConfig.GEO_CHAT_PROD_API_KEY
    }

    /** False when the production key is absent, so the UI can point at the missing property. */
    fun isProdConfigured(mode: SessionMode): Boolean = prodApiKey(mode).isNotBlank()

    /** Which local.properties entry to set when a production key is missing. */
    fun missingKeyPropertyName(mode: SessionMode): String = when (mode) {
        SessionMode.Video -> "geoiq.voice.prod.apiKey"
        SessionMode.Chat -> "geoiq.chat.prod.apiKey"
    }

    /** A production socket implies the production token host; everything else is staging. */
    fun tokenBaseUrl(socketUrl: String): String =
        if (socketUrl == SOCKET_VOICE_PROD || socketUrl == SOCKET_CHAT_PROD) {
            TOKEN_BASE_URL_PROD
        } else {
            TOKEN_BASE_URL_STAGING
        }

    fun isValidSocketUrl(socketUrl: String): Boolean =
        socketUrl.startsWith("wss://") || socketUrl.startsWith("ws://")
}
