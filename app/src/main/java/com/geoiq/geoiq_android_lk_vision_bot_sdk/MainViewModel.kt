package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class ChatMessage(
    val text: String,
    val isLocal: Boolean,
    val sender: String?,
    val timestamp: String,
)

/**
* @author Sayak Mondal.
 */

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val CHAT_TOPIC = "chat"
        const val PREFS_NAME = "geo_vision_config"
        const val PREF_API_KEY = "api_key"
        const val PREF_BASE_URL = "base_url"
        const val PREF_TOKEN_URL = "token_url"
    }

    private val prefs =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Committed config: persisted overrides of the BuildConfig values. SettingsScreen edits
    // a draft copy and commits via saveConfig(); connect()/fetchToken() only ever read these,
    // so an unsaved draft never affects a connection.
    var xApiKey by mutableStateOf(prefs.getString(PREF_API_KEY, null) ?: BuildConfig.API_KEY)
        private set
    var geoVisionUrl by mutableStateOf(prefs.getString(PREF_BASE_URL, null) ?: BuildConfig.BASE_URL)
        private set
    var tokenUrl by mutableStateOf(prefs.getString(PREF_TOKEN_URL, null) ?: BuildConfig.TOKEN_URL)
        private set

    fun saveConfig(url: String, apiKey: String, tokenEndpoint: String) {
        geoVisionUrl = url
        xApiKey = apiKey
        tokenUrl = tokenEndpoint
        prefs.edit()
            .putString(PREF_BASE_URL, url)
            .putString(PREF_API_KEY, apiKey)
            .putString(PREF_TOKEN_URL, tokenEndpoint)
            .apply()
        log("Config saved")
    }

    fun resetConfigToDefaults() {
        xApiKey = BuildConfig.API_KEY
        geoVisionUrl = BuildConfig.BASE_URL
        tokenUrl = BuildConfig.TOKEN_URL
        prefs.edit()
            .remove(PREF_API_KEY)
            .remove(PREF_BASE_URL)
            .remove(PREF_TOKEN_URL)
            .apply()
    }

    val isConfigModified: Boolean
        get() = xApiKey != BuildConfig.API_KEY ||
            geoVisionUrl != BuildConfig.BASE_URL ||
            tokenUrl != BuildConfig.TOKEN_URL

    // Connection state
    var connectionStatus by mutableStateOf("Disconnected")
        private set
    var isConnecting by mutableStateOf(false)
        private set
    var isConnected by mutableStateOf(false)
        private set
    var connectionQuality by mutableStateOf(ConnectionQuality.UNKNOWN)
        private set

    // Media state
    var isCameraEnabled by mutableStateOf(false)
        private set
    var isMicrophoneEnabled by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var isFlippingCamera by mutableStateOf(false)
        private set

    // Agent state
    var agentState by mutableStateOf("")
        private set

    // Video side effects consumed by the composable
    var localVideoTrack by mutableStateOf<LocalVideoTrack?>(null)
        private set
    var rendererSession by mutableIntStateOf(0)
        private set

    val eventLog = mutableStateListOf<String>()

    val chatMessages = mutableStateListOf<ChatMessage>()

    init {
        syncConnectionStateOnInit()
        collectSdkEvents()
    }

    private fun collectSdkEvents() {
        viewModelScope.launch {
            VisionBotSDKManager.events.collect { event ->
                when (event) {
                    is GeoVisionEvent.Connecting -> {
                        log("Connecting to ${event.url}")
                        connectionStatus = "Connecting..."
                        isConnecting = true
                        isConnected = false
                    }
                    is GeoVisionEvent.Connected -> {
                        log("Connected: ${event.roomName} as ${event.localParticipant.identity}")
                        connectionStatus = "Connected: ${event.roomName}"
                        isConnecting = false
                        isConnected = true
                        syncMediaState()
                    }
                    is GeoVisionEvent.Disconnected -> {
                        log("Disconnected: ${event.reason ?: "Client initiated"}")
                        connectionStatus = "Disconnected"
                        isConnecting = false
                        isConnected = false
                        connectionQuality = ConnectionQuality.UNKNOWN
                        isSpeaking = false
                        agentState = ""
                        localVideoTrack = null
                        rendererSession++
                        chatMessages.clear()
                        syncMediaState()
                    }
                    is GeoVisionEvent.ParticipantJoined -> {
                        log("Participant joined: ${event.participant.identity}")
                        viewModelScope.launch {
                            VisionBotSDKManager.getLocalParticipant()?.publishData(
                                "BOT_CONNECTED".toByteArray(Charsets.UTF_8),
                                DataPublishReliability.RELIABLE,
                                "vinay_bot_connected"
                            )
                        }
                    }
                    is GeoVisionEvent.ParticipantLeft -> {
                        log("Participant left: ${event.participant.identity}")
                    }
                    is GeoVisionEvent.ParticipantAttributesChanged -> {
                        if ("lk.agent.state" in event.changedAttributes) {
                            agentState = event.changedAttributes["lk.agent.state"].toString()
                            log("Agent state: $agentState")
                        }
                    }
                    is GeoVisionEvent.LocalTrackSubscribed -> {
                        log("Local track live: ${event.publication.source.name}")
                        when (event.publication.source?.name?.lowercase()) {
                            "camera" -> {
                                isCameraEnabled = true
                                val track = VisionBotSDKManager.getLocalParticipant()
                                    ?.getTrackPublication(event.publication.source)?.track
                                if (track is LocalVideoTrack) localVideoTrack = track
                            }
                            "microphone" -> isMicrophoneEnabled = true
                        }
                    }
                    is GeoVisionEvent.TrackPublished -> {
                        log("Track published: ${event.publication.source}")
                    }
                    is GeoVisionEvent.TrackUnpublished -> {
                        log("Track unpublished: ${event.publication.source}")
                        when (event.publication.source.name.lowercase()) {
                            "camera" -> { isCameraEnabled = false; localVideoTrack = null }
                            "microphone" -> isMicrophoneEnabled = false
                        }
                    }
                    is GeoVisionEvent.TrackSubscribed -> {
                        log("Remote track: ${event.track.name} from ${event.participant.identity}")
                    }
                    is GeoVisionEvent.TrackUnsubscribed -> {
                        log("Track unsubscribed: ${event.track.name}")
                    }
                    is GeoVisionEvent.ConnectionQualityChanged -> {
                        if (event.participant is LocalParticipant) {
                            connectionQuality = event.quality
                            log("Quality: ${event.quality}")
                        }
                    }
                    is GeoVisionEvent.ActiveSpeakersChanged -> {
                        isSpeaking = VisionBotSDKManager.getIsSpeaking()
                    }
                    is GeoVisionEvent.TranscriptionReceived -> {
                        if (event.isFinal) {
                            log("Transcript: ${event.message}")
                            addChatMessage(event.message, isLocal = false, sender = "Agent")
                        }
                    }
                    is GeoVisionEvent.CustomMessageReceived -> {
                        log("[${event.topic}] ${event.message}")
                        addChatMessage(event.message, isLocal = false, sender = event.topic)
                    }
                    is GeoVisionEvent.Error -> {
                        log("ERROR: ${event.message}")
                        if (!isConnected) {
                            connectionStatus = "Error"
                            isConnecting = false
                        }
                    }
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            val result = fetchToken()
            if (result != null) {
                val (token, roomName, identity) = result
                log("Connecting to $roomName as $identity")
                val options = GeoVisionRoomOptions(
                    videoTrackCaptureDefaults = LocalVideoTrackOptions(position = CameraPosition.FRONT)
                )
                VisionBotSDKManager.connectToGeoVisionRoom(getApplication(), geoVisionUrl, token, options)
            } else {
                log("Failed to fetch token")
            }
        }
    }


    fun disconnect() {
        VisionBotSDKManager.disconnectFromGeoVisionRoom()
    }

    fun toggleCamera() {
        viewModelScope.launch {
            val newEnabled = !VisionBotSDKManager.isCameraEnabled()
            VisionBotSDKManager.setCameraEnabled(newEnabled)
            isCameraEnabled = newEnabled
        }
    }

    fun toggleMicrophone() {
        viewModelScope.launch {
            val newEnabled = !VisionBotSDKManager.isMicrophoneEnabled()
            VisionBotSDKManager.setMicrophoneEnabled(newEnabled)
            isMicrophoneEnabled = newEnabled
        }
    }

    fun flipCamera(onResult: (isFrontCamera: Boolean) -> Unit) {
        viewModelScope.launch {
            isFlippingCamera = true
            try {
                if (VisionBotSDKManager.flipCameraPosition()) {
                    val track = VisionBotSDKManager.getLocalParticipant()?.getOrCreateDefaultVideoTrack()
                    onResult(track?.options?.position == CameraPosition.FRONT)
                    log("Camera flipped")
                } else {
                    log("Failed to flip camera")
                }
            } finally {
                isFlippingCamera = false
            }
        }
    }

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val file = createFileFromUri(app, uri) ?: run {
                log("Failed to read selected file")
                return@launch
            }
            log("Sending: ${file.name}")
            val success = VisionBotSDKManager.sendFile(app, file, "send-file")
            log(if (success) "Sent: ${file.name}" else "Send failed: ${file.name}")
            file.delete()
        }
    }

    fun sendChatMessage(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            val localParticipant = VisionBotSDKManager.getLocalParticipant() ?: run {
                log("Cannot send chat: not connected")
                return@launch
            }
            // Echoed locally — LiveKit does not deliver a participant's own publishData back.
            addChatMessage(message, isLocal = true, sender = "You")
            try {
                localParticipant.publishData(
                    message.toByteArray(Charsets.UTF_8),
                    DataPublishReliability.RELIABLE,
                    CHAT_TOPIC
                )
            } catch (e: Exception) {
                log("Chat send failed: ${e.message}")
            }
        }
    }

    private fun addChatMessage(text: String?, isLocal: Boolean, sender: String?) {
        if (text.isNullOrBlank()) return
        chatMessages.add(
            ChatMessage(
                text = text,
                isLocal = isLocal,
                sender = sender,
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            )
        )
    }

    private fun syncMediaState() {
        isCameraEnabled = VisionBotSDKManager.isCameraEnabled()
        isMicrophoneEnabled = VisionBotSDKManager.isMicrophoneEnabled()
    }

    private fun syncConnectionStateOnInit() {
        val room = VisionBotSDKManager.currentRoom ?: return
        if (room.state == GeoVisionRoomState.CONNECTED) {
            isConnected = true
            connectionStatus = "Connected: ${room.name ?: "Unknown Room"}"
            syncMediaState()
            val localPart = VisionBotSDKManager.getLocalParticipant()
            val track = localPart?.videoTrackPublications?.firstOrNull()?.second
            if (track is LocalVideoTrack) localVideoTrack = track
        }
    }

    private fun log(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventLog.add(0, "[$ts] $message")
    }

    private suspend fun fetchToken(): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        var conn: HttpsURLConnection? = null
        // URL() and openConnection() are inside the try because tokenUrl is user-editable:
        // a malformed value throws MalformedURLException and a non-https one fails the
        // HttpsURLConnection cast. Uncaught, either would crash viewModelScope.
        try {
            val metadata = buildTokenMetadata()
            conn = (URL(tokenUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", xApiKey)
                setRequestProperty("metadata", metadata.toString())
                doOutput = true
            }
            val body = JSONObject().apply { put("metadata", metadata) }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            Triple(json.getString("accessToken"), json.getString("room_name"), json.getString("identity"))
        } catch (e: Exception) {
            Log.e("MainViewModel", "Token fetch failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildTokenMetadata() = JSONObject().apply {
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

    private fun createFileFromUri(context: Context, uri: Uri): File? {
        var fileName = "temp_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) cursor.getString(idx)?.let { fileName = it }
            }
        }
        val tempFile = File(context.cacheDir, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: IOException) {
            if (tempFile.exists()) tempFile.delete()
            null
        }
    }
}
