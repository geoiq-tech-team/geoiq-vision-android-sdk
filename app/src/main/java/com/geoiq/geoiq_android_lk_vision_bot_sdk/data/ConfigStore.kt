package com.geoiq.geoiq_android_lk_vision_bot_sdk.data

import android.content.Context
import androidx.core.content.edit
import com.geoiq.geoiq_android_lk_vision_bot_sdk.BuildConfig

class ConfigStore(context: Context) {

    private companion object {
        const val PREFS_NAME = "lk_sample_config"
        const val PREF_API_KEY = "api_key"
        const val PREF_BASE_URL = "base_url"
        const val PREF_TOKEN_URL = "token_url"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val apiKey: String get() = prefs.getString(PREF_API_KEY, null) ?: BuildConfig.API_KEY
    val livekitUrl: String get() = prefs.getString(PREF_BASE_URL, null) ?: BuildConfig.BASE_URL
    val tokenUrl: String get() = prefs.getString(PREF_TOKEN_URL, null) ?: BuildConfig.TOKEN_URL

    fun save(livekitUrl: String, apiKey: String, tokenUrl: String) {
        prefs.edit {
            putString(PREF_BASE_URL, livekitUrl)
            putString(PREF_API_KEY, apiKey)
            putString(PREF_TOKEN_URL, tokenUrl)
        }
    }

    fun clear() {
        prefs.edit {
            remove(PREF_API_KEY)
            remove(PREF_BASE_URL)
            remove(PREF_TOKEN_URL)
        }
    }
}
