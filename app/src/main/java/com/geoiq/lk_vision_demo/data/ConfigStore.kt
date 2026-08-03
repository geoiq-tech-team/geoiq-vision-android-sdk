package com.geoiq.lk_vision_demo.data

import android.content.Context
import androidx.core.content.edit
import com.geoiq.lk_vision_demo.ModeEnvConfig
import com.geoiq.lk_vision_demo.SessionMode

/**
 * Persists each mode's deployment choice: production vs staging, plus the editable staging
 * socket + key pair.
 *
 * Production values are never stored — they come from BuildConfig via [GeoEnv], so rotating a key
 * in local.properties takes effect without stale prefs overriding it.
 */
class ConfigStore(context: Context) {

    private companion object {
        const val PREFS_NAME = "lk_sample_config"
        const val SUFFIX_USE_PROD = "_use_prod"
        const val SUFFIX_STAGING_SOCKET = "_staging_socket_url"
        const val SUFFIX_STAGING_KEY = "_staging_api_key"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun envConfig(mode: SessionMode): ModeEnvConfig {
        val prefix = prefix(mode)
        return ModeEnvConfig(
            // Default to production only when its key is actually configured.
            useProd = prefs.getBoolean(
                prefix + SUFFIX_USE_PROD,
                GeoEnv.isProdConfigured(mode),
            ),
            stagingSocketUrl = prefs.getString(prefix + SUFFIX_STAGING_SOCKET, null)
                ?.takeIf { it.isNotBlank() }
                ?: GeoEnv.DEFAULT_STAGING_SOCKET,
            stagingApiKey = prefs.getString(prefix + SUFFIX_STAGING_KEY, null)
                ?.takeIf { it.isNotBlank() }
                ?: GeoEnv.DEFAULT_STAGING_API_KEY,
        )
    }

    fun saveEnvConfig(mode: SessionMode, config: ModeEnvConfig) {
        val prefix = prefix(mode)
        prefs.edit {
            putBoolean(prefix + SUFFIX_USE_PROD, config.useProd)
            putString(prefix + SUFFIX_STAGING_SOCKET, config.stagingSocketUrl)
            putString(prefix + SUFFIX_STAGING_KEY, config.stagingApiKey)
        }
    }

    fun clear() {
        prefs.edit {
            SessionMode.entries.forEach { mode ->
                val prefix = prefix(mode)
                remove(prefix + SUFFIX_USE_PROD)
                remove(prefix + SUFFIX_STAGING_SOCKET)
                remove(prefix + SUFFIX_STAGING_KEY)
            }
        }
    }

    private fun prefix(mode: SessionMode): String = when (mode) {
        SessionMode.Video -> "voice"
        SessionMode.Chat -> "chat"
    }
}
