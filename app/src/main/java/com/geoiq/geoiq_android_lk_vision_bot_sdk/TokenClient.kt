package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class GeoVisionToken(
    val accessToken: String,
    val roomName: String,
    val identity: String,
)

object TokenClient {

    private const val TAG = "TokenClient"

    suspend fun fetch(tokenUrl: String, apiKey: String): GeoVisionToken? = withContext(Dispatchers.IO) {
        var conn: HttpsURLConnection? = null
        try {
            val metadata = buildMetadata()
            conn = (URL(tokenUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("metadata", metadata.toString())
                doOutput = true
            }
            val body = JSONObject().apply { put("metadata", metadata) }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            GeoVisionToken(
                accessToken = json.getString("accessToken"),
                roomName = json.getString("room_name"),
                identity = json.getString("identity"),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token fetch failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildMetadata() = JSONObject().apply {
        put("event", "vaep_addon_lens_package_page")
        put("juno", JSONObject().apply {
            put("result", JSONObject(mapOf(
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
                "x-app-version" to "5.2.9 (251120001)"
            )))
        })
        put("va_data", JSONObject().apply {
            put("aiContext", JSONObject().apply {
                put("context", "package")
                put("contextId", 142515.0)
                put("filters", JSONObject().apply { put("powerType", "single_vision") })
            })
            put("deviceLanguage", "en")
        })
        put("pid", 131)
        put("job_id", "vision_AJ_zxR4vDaUn6MZ")
        put("room_name", "BNDW5@BLD$")
    }
}
