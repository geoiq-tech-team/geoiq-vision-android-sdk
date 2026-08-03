package com.geoiq.lk_vision_demo.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class LocaleConfigItem(
    val code: String,
    val label: String,
    val isDefault: Boolean,
)

data class SessionToken(
    val accessToken: String,
    val roomName: String,
    val identity: String,
    val localeConfig: List<LocaleConfigItem> = emptyList(),
)

/** Outcome of the pre-token `/v1/serviceup` gate. */
sealed interface ServiceUpResult {
    data object Up : ServiceUpResult

    /** Backend reachable but refusing sessions. 503 = update required, 426 = upgrade needed. */
    data class Unavailable(val code: Int) : ServiceUpResult

    data object NetworkError : ServiceUpResult
}

object TokenClient {

    private const val TAG = "TokenClient"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Mirrors the consumer app's `getGeoIqServiceUp` gate, which runs before every token
     * fetch (initial connect and reconnect alike). A non-2xx here means the backend is
     * refusing sessions, so connecting would fail anyway.
     *
     * @param baseUrl token host for the selected deployment, from [GeoEnv.tokenBaseUrl].
     */
    suspend fun serviceUp(baseUrl: String, apiKey: String): ServiceUpResult =
        withContext(Dispatchers.IO) {
            var conn: HttpsURLConnection? = null
            try {
                val url = baseUrl.trimEnd('/') + GeoEnv.PATH_SERVICE_UP
                conn = (URL(url).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("x-api-key", apiKey)
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }
                when (val code = conn.responseCode) {
                    in 200..299 -> ServiceUpResult.Up
                    // A backend without this endpoint must not block connecting.
                    HttpsURLConnection.HTTP_NOT_FOUND -> ServiceUpResult.Up
                    else -> ServiceUpResult.Unavailable(code)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Service-up check failed", e)
                ServiceUpResult.NetworkError
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * Fetches an access token for a new room.
     *
     * @param event session source. Either an entry point (`vaep_*`) or a handover marker
     *   (`handover_from_voice` / `handover_from_assist`) so the backend can carry context
     *   across a mode switch.
     * @param agentType routes the session to the voice or chat agent.
     * @param selectedLanguage previously chosen locale code, replayed so the agent does not
     *   re-prompt for a language on every new session.
     */
    suspend fun fetch(
        baseUrl: String,
        apiKey: String,
        event: String,
        agentType: AgentType,
        selectedLanguage: String? = null,
    ): SessionToken? = withContext(Dispatchers.IO) {
        var conn: HttpsURLConnection? = null
        try {
            val metadata = buildMetadata(event, agentType, selectedLanguage)
            val url = baseUrl.trimEnd('/') + GeoEnv.PATH_TOKEN
            conn = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("metadata", metadata.toString())
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }
            // The consumer app sends agent_type both inside metadata and at the top level.
            val body = JSONObject().apply {
                put(GeoProtocol.KEY_METADATA, metadata)
                put(GeoProtocol.KEY_AGENT_TYPE, agentType.wireValue)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Token fetch returned HTTP $code: ${error.orEmpty().take(500)}")
                return@withContext null
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            SessionToken(
                accessToken = json.getString(GeoProtocol.KEY_ACCESS_TOKEN),
                roomName = json.getString(GeoProtocol.KEY_ROOM_NAME),
                identity = json.getString(GeoProtocol.KEY_IDENTITY),
                localeConfig = parseLocaleConfig(json.optJSONArray(GeoProtocol.KEY_LOCALE_CONFIG)),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token fetch failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** `locale_config` drives the language picker; absent on backends with a single locale. */
    private fun parseLocaleConfig(array: JSONArray?): List<LocaleConfigItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val code = item.optString(GeoProtocol.KEY_CODE).takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            LocaleConfigItem(
                code = code,
                label = item.optString(GeoProtocol.KEY_LABEL),
                isDefault = item.optBoolean(GeoProtocol.KEY_IS_DEFAULT, false),
            )
        }
    }

    private fun buildMetadata(
        event: String,
        agentType: AgentType,
        selectedLanguage: String?,
    ) = JSONObject().apply {
        put(GeoProtocol.KEY_EVENT, event)
        put(GeoProtocol.KEY_AGENT_TYPE, agentType.wireValue)
        put(GeoProtocol.KEY_JUNO, JSONObject().apply {
            put(GeoProtocol.KEY_RESULT, JSONObject(DEMO_REQUEST_HEADERS))
        })
        put(GeoProtocol.KEY_VA_DATA, JSONObject().apply {
            put(GeoProtocol.KEY_AI_CONTEXT, JSONObject().apply {
                put("context", "package")
                put("contextId", 142515.0)
                put("filters", JSONObject().apply { put("powerType", "single_vision") })
            })
            put(GeoProtocol.KEY_DEVICE_LANGUAGE, "en")
            if (!selectedLanguage.isNullOrBlank()) {
                put(GeoProtocol.KEY_SELECTED_LANGUAGE, selectedLanguage)
            }
        })
        put("pid", 131)
        put("job_id", "vision_AJ_zxR4vDaUn6MZ")
        put(GeoProtocol.KEY_ROOM_NAME, "BNDW5@BLD$")
    }

    /**
     * Stand-in for the consumer app's `RequestConfig.defaultRequestConfig.headers`, which
     * carries real device/session context. Fixed sample values here.
     */
    private val DEMO_REQUEST_HEADERS = mapOf(
        "x-country-code-override" to "IN",
        "accept-language" to "en",
        "x-customer-type" to "REPEAT",
        "x-session-token" to "8e6fb0d3-5e8d-46e6-93af-ffcba73fb261",
        "appversion" to "5.2.9 (251120001)",
        "Accept-Encoding" to "gzip",
        "X-Build-Version" to "251120001",
        "api_key" to "valyoo123",
        "x-accept-language" to "en",
        "x-api-client" to "android",
        "model" to "moto g35 5G",
        "udid" to "2b7b7751b6fa6f4c",
        "x-country-code" to "IN",
        "brand" to "motorola",
        "Content-Type" to "application/json",
        "x-app-version" to "5.2.9 (251120001)",
    )
}
