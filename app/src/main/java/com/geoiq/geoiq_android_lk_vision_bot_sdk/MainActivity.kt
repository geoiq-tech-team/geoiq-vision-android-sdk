package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.geoiq.geoiq_android_lk_vision_bot_sdk.ui.theme.GEOIQANDROIDLKVISIONBOTSDKTheme
import io.livekit.android.renderer.SurfaceViewRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livekit.org.webrtc.RendererCommon
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection


class MainActivity : ComponentActivity() {

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("MainActivityPermissions", "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsToRequest = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
        )
        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNotGranted.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsNotGranted.toTypedArray())
        }

        setContent {
            GEOIQANDROIDLKVISIONBOTSDKTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    SDKInteractionScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VisionBotSDKManager.shutdown()
        Log.d("MainActivity", "VisionBotSDKManager shutdown called.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SDKInteractionScreen(modifier: Modifier = Modifier) {
    val surfaceRendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var eventLog by remember { mutableStateOf(listOf<String>()) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectQuality by remember { mutableStateOf<ConnectionQuality>(ConnectionQuality.UNKNOWN) }
    var isCameraEnabledUi by remember { mutableStateOf(VisionBotSDKManager.isCameraEnabled()) }
    var isMicrophoneEnabledUi by remember { mutableStateOf(VisionBotSDKManager.isMicrophoneEnabled()) }
    var isSpeaking by remember { mutableStateOf(VisionBotSDKManager.getIsSpeaking()) }
    var agentState by remember { mutableStateOf("hi") }
    val xApiKey = "eyc546bb1d98c097899a2c474e0b77f8e45a23ff729c"
    val geoVisionUrl = "wss://lk-stg7.diq.geoiq.ai"
//    val xApiKey = "eyshaG9sbGVzX2Fwa1DopV9rCV12FwaV9rZXk6cassmmjas"
//    val geoVisionUrl = "wss://lk-stg4.diq.geoiq.ai"
//    val xApiKey = "eyshaG9sbGVzX2FwaV9rZXk6c2VjaLl8jhss"
//    val geoVisionUrl = "wss://lk-stg2.diq.geoiq.ai"

    var isFlipping by remember { mutableStateOf(false) }

    suspend fun fetchToken(xApiKey: String): Triple<String, String, String>? =
        withContext(Dispatchers.IO) {

            val newMetadataJson = JSONObject()

            newMetadataJson.put("event", "vaep_home_page")

            val vaDataJson = JSONObject()
            val locationAddressJson = JSONObject()
            val addressLinesJson = JSONObject()
            addressLinesJson.put("0", "560102, Bengaluru, Karnataka, 560102")
            locationAddressJson.put("addressLines", addressLinesJson)
            locationAddressJson.put("adminArea", "")
            locationAddressJson.put("countryCode", "")
            locationAddressJson.put("countryName", "")
            locationAddressJson.put("featureName", "")
            locationAddressJson.put("hasLatitude", true)
            locationAddressJson.put("hasLongitude", true)
            locationAddressJson.put("isUserManualPreference", false)
            locationAddressJson.put("latitude", 12.910355908206624)
            locationAddressJson.put("locality", "")
            locationAddressJson.put("longitude", 77.64460510218187)
            locationAddressJson.put("maxAddressLineIndex", 0)
            locationAddressJson.put("postalCode", "560102")
            locationAddressJson.put("premises", "Bengaluru, Karnataka")
            locationAddressJson.put("subAdminArea", "")
            locationAddressJson.put("subLocality", "")
            locationAddressJson.put("thoroughfare", "")
            vaDataJson.put("locationAddress", locationAddressJson)
            newMetadataJson.put("va_data", vaDataJson)

            val junoMap = mapOf(
                "X-Session-Token" to "b6da70eb-c979-4ddf-9b65-7ca9b591cd80",
                "x-api-client" to "mobilesite",
                "X-Country-Code" to "IN",
                "x-country-code-override" to "IN",
                "accept-language" to "en",
                "appversion" to "0",
                "x-customer-type" to "REPEAT",
                "x-customer-tier-name" to ""
            )
            val junoJson = JSONObject()
            junoJson.put("result", JSONObject(junoMap))
            junoJson.put("status", 200)
            junoJson.put("trace_id", "")
            newMetadataJson.put("juno", junoJson)


//            val url = URL("https://beapis-in.staging.geoiq.ai/vision/user/v2.0/getsdkaccesstoken")
            val url = URL("https://lk-va-token.diq.geoiq.ai/stg/v1/token")
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", xApiKey)
            conn.setRequestProperty("metadata", newMetadataJson.toString())
            conn.doOutput = true


            val finalBody = JSONObject()
            finalBody.put("metadata", newMetadataJson)

            conn.outputStream.bufferedWriter().use {
                it.write(finalBody.toString())
            }


            try {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                Triple(
                    json.getString("accessToken"),
                    json.getString("room_name"),
                    json.getString("identity")
                )
            } catch (e: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventLog = eventLog + "[$timestamp] $message"
        Log.d("SDKInteractionScreen", message)
    }

    fun createFileFromUri(context: Context, uri: Uri): File? {
        var fileName = "temp_picked_file" // Default filename
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (name != null) fileName = name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("createFileFromUri", "Could not get original filename for URI: $uri", e)
        }

        val tempFile = File(context.cacheDir, fileName)

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("createFileFromUri", "Could not open input stream for URI: $uri")
                return null
            }
            outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            return tempFile
        } catch (e: IOException) {
            Log.e("createFileFromUri", "Error copying file from URI: $uri", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return null
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("createFileFromUri", "Error closing streams", e)
            }
        }
    }


    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(), onResult = { fileUri: Uri? ->
            if (fileUri != null) {
                if (isConnected) {
                    coroutineScope.launch {
                        addLog("File URI selected: $fileUri")
                        val tempFile: File? = createFileFromUri(context, fileUri)

                        if (tempFile != null && tempFile.exists()) {
                            addLog("Temporary file created: ${tempFile.absolutePath}")
                            addLog("Attempting to send file: ${tempFile.name}")

                            val success =
                                VisionBotSDKManager.sendFile(context, tempFile, topic = "send-file")

                            if (success) {
                                addLog("File sending initiated successfully: ${tempFile.name}")
                            } else {
                                addLog("Failed to initiate file sending: ${tempFile.name}")
                            }

                            if (tempFile.exists()) {
                                if (tempFile.delete()) {
                                    addLog("Temporary file ${tempFile.name} deleted after sending.")
                                } else {
                                    addLog("Failed to delete temporary file ${tempFile.name} after sending.")
                                }
                            }

                        } else {
                            addLog("Failed to create a temporary file from the selected URI.")
                        }
                    }
                } else {
                    addLog("Cannot send file: Not connected to a room.")
                }
            } else {
                addLog("No file selected.")
            }
        })


    suspend fun performRpcCall() {

        val room = VisionBotSDKManager.getCurrentroom()
        val remoteIdentity = VisionBotSDKManager.getRemoteParticipants().keys.firstOrNull()

        try {
            val response = remoteIdentity?.let {
                room?.localParticipant?.performRpc(
                    destinationIdentity = it,
                    method = "display_item_detail_rpc",
                    payload = JSONObject().apply {
                        put("reference", "217768")
                    }.toString()
                )
            }
            println("RPC response: $response")
        } catch (e: RpcError) {
            println("RPC call failed: $e")
        }
    }

    fun attachLocalVideo(videoTrack: LocalVideoTrack) {
        val renderer = surfaceRendererRef.value
        Log.d(
            "VisionSDK", "Attaching local video track: ${videoTrack.name}, renderer :: ${renderer}"
        )
        if (renderer == null) {
            Log.e("VisionSDK", "Renderer is not initialized")
            return
        }
        try {
            videoTrack.addRenderer(renderer)
            videoTrack.addRenderer(renderer)
            Log.d("VisionSDK", "Track attached")
        } catch (e: Exception) {
            Log.e("VisionSDK", "Error attaching renderer: ${e.localizedMessage}")
        }
        try {
            VisionBotSDKManager.initializeVideoRenderer(renderer)
            Log.d("VisionSDK", "Renderer initialized")

        } catch (e: Exception) {
            Log.e("VisionSDK", "Error initializing renderer: ${e.localizedMessage}")
            return 
        }
    }




    LaunchedEffect(Unit) {
        addLog("Initial camera state: camera enabled = ${VisionBotSDKManager.isCameraEnabled()}, mic enabled = ${VisionBotSDKManager.isMicrophoneEnabled()}")

        VisionBotSDKManager.events.collect { event ->
            addLog("Event: $event")
            when (event) {
                is GeoVisionEvent.Connecting -> {
                    addLog("Connecting to ${event.url} with token ending in ...${event.tokenSnippet}")
                    connectionStatus = "Connecting..."
                    isConnecting = true
                    isConnected = false
                }

                is GeoVisionEvent.Connected -> {
                    addLog("Connected to room: ${event.roomName}. Local User: ${event.localParticipant.identity}")
                    connectionStatus = "Connected: ${event.roomName}"
                    isConnecting = false
                    isConnected = true
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()
                }

                is GeoVisionEvent.Disconnected -> {
                    addLog("Disconnected. Reason: ${event.reason ?: "Client initiated"}")
                    connectionStatus = "Disconnected"
                    isConnecting = false
                    isConnected = false
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()

                    // *** THE FIX IS HERE ***
                    // On disconnect, just clear the image from the renderer.
                    // DO NOT release it, because the view is still on the screen
                    // and we want to reuse it for the next connection.
                    surfaceRendererRef.value?.let { renderer ->
                        try {
                            renderer.clearImage()
                        } catch (e: Exception) {
                            Log.e(
                                "SDKInteractionScreen",
                                "Error clearing renderer: ${e.localizedMessage}"
                            )
                        }
                    }
                }

                is GeoVisionEvent.ParticipantJoined -> {
                    addLog("Participant joined: ${event.participant.attributes}")

                    coroutineScope.launch {
                        addLog("Sending messsage on Connect")
                        VisionBotSDKManager.getLocalParticipant()?.publishData(
                            "BOT_CONNECTED".toByteArray(Charsets.UTF_8),
                            DataPublishReliability.RELIABLE,
                            "vinay_bot_connected"
                        )
                    }
                }

                is GeoVisionEvent.ParticipantAttributesChanged -> {
                    addLog("Participant attributes changed: ${event.participant.identity}")

                    val participant = event.participant
                    val changedAttributes = event.changedAttributes

                    // Check if the agent state attribute was the one that changed
                    if ("lk.agent.state" in changedAttributes) {
                        agentState = changedAttributes["lk.agent.state"].toString()

                        // You can also get it directly from the participant's attributes
                        val currentAgentState = participant.attributes["lk.agent.state"]

                        // Update your UI based on the new state
                        // Example:
                        if (currentAgentState == "thinking") {
                            // Show a "typing" or "thinking" indicator
                        }
                    }
                }

                is GeoVisionEvent.ParticipantLeft -> {
                    addLog("Participant left: ${event.participant.identity}")
                }

                is GeoVisionEvent.TrackPublished -> {
                    addLog("Local track published: ${event.publication.source} by ${event.participant.identity}")

//                    if (event.publication.source?.name?.lowercase() == "camera") {
//                        val localParticipant =
//                            VisionBotSDKManager.getCurrentroom()?.localParticipant
//                        val localTrack =
//                            localParticipant?.getTrackPublication(event.publication.source)?.track
//
//                        if (localTrack is LocalVideoTrack) {
//                            attachLocalVideo(localTrack)
//                            isCameraEnabledUi = true
//                        } else {
//                            addLog("Expected LocalVideoTrack but got ${localTrack?.javaClass?.simpleName}")
//                        }
//                    } else if (event.publication.source?.name?.lowercase() == "microphone") {
//
//
//                        isMicrophoneEnabledUi = true // Reflect mic is published
//                    }
                }


                is GeoVisionEvent.LocalTrackSubscribed ->{
                    addLog("Local track subscribed: ${event.publication.source.name} by ${event.participant.identity}")
                    if (event.publication.source?.name?.lowercase() == "camera") {
                        val localParticipant =
                            VisionBotSDKManager.getCurrentroom()?.localParticipant
                        val localTrack =
                            localParticipant?.getTrackPublication(event.publication.source)?.track

                        if (localTrack is LocalVideoTrack) {
                            attachLocalVideo(localTrack)
                            isCameraEnabledUi = true
                        } else {
                            addLog("Expected LocalVideoTrack but got ${localTrack?.javaClass?.simpleName}")
                        }
                    } else if (event.publication.source?.name?.lowercase() == "microphone") {


                        isMicrophoneEnabledUi = true // Reflect mic is published
                    }
                }

                is GeoVisionEvent.TrackUnpublished -> {
                    addLog("Vinay Local track unpublished: ${event.publication.source} by ${event.participant.identity}")
                    if (event.publication.source.name.lowercase() == "camera") {
                        isCameraEnabledUi = false
                    } else if (event.publication.source.name.lowercase() == "microphone") {
                        isMicrophoneEnabledUi = false
                    }
                }

                is GeoVisionEvent.TrackSubscribed -> {
                    addLog("Track subscribed: ${event.track.name} from ${event.participant.identity}")
                }

                is GeoVisionEvent.TrackUnsubscribed -> {
                    addLog("Track unsubscribed: ${event.track.name} from ${event.participant.identity}")
                }

                is GeoVisionEvent.ConnectionQualityChanged -> {
                    addLog("Connection Quality changed: ${event.quality} for ${event.participant.identity}")
                    if (event.participant is LocalParticipant) connectQuality = event.quality
                }

                is GeoVisionEvent.ActiveSpeakersChanged -> {
                    isSpeaking = VisionBotSDKManager.getIsSpeaking()
                    addLog("Active speakers: ${event.speakers.joinToString { it.identity?.toString() ?: "N/A" }}")
                }

                is GeoVisionEvent.TranscriptionReceived -> {
                    addLog("Transcription: ${event.senderId} ${event.message}")
                }

                is GeoVisionEvent.Error -> {
                    addLog("ERROR: ${event.message} ${event.exception?.localizedMessage ?: ""}")
                    if (event.message.contains("Failed to connect") || event.message.contains("Connection setup failed")) {
                        isConnecting = false
                        isConnected = false
                        connectionStatus = "Error Connecting"
                    }
                }

                is GeoVisionEvent.CustomMessageReceived -> {
                    addLog("Received message on topic '${event.topic}': ${event.message}")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AndroidView(factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                surfaceRendererRef.value = this
                setEnableHardwareScaler(true)
                setMirror(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            }
        }, modifier = Modifier
            .fillMaxWidth()
            .height(200.dp), onRelease = { renderer ->
            // This is the correct place to release the renderer, when the view is destroyed.
            try {
                renderer.release()
            } catch (e: Exception) {
                Log.e("SDKInteractionScreen", "Error releasing renderer: ${e.localizedMessage}")
            }
        })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        val tokenResult = fetchToken(xApiKey)
                        if (tokenResult != null) {
                            val (accessToken, roomName, identity) = tokenResult
                            val roomOptions = GeoVisionRoomOptions(
                                videoTrackCaptureDefaults = LocalVideoTrackOptions(
                                    position = CameraPosition.FRONT
                                )
                            )
                            VisionBotSDKManager.connectToGeoVisionRoom(
                                context, geoVisionUrl, accessToken, roomOptions
                            )
                            addLog("Connecting to $roomName as $identity")
                        } else {
                            addLog("Failed to fetch token from API")
                        }
                    }
                }, enabled = !isConnecting && !isConnected
            ) {
                Text("Connect")
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        VisionBotSDKManager.disconnectFromGeoVisionRoom()
                    }
                }, enabled = isConnected || isConnecting
            ) {
                Text("Disconnect")
            }
        }

        Text("Status: $connectionStatus")
        Text("Speaking: $isSpeaking")
        Text("Connection quality: ${connectQuality}")
        Text("Agent Connection: ${agentState}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                coroutineScope.launch {
                    val success =
                        VisionBotSDKManager.setCameraEnabled(!VisionBotSDKManager.isCameraEnabled())
                    addLog("Set Camera toggle: SDK reported $success. New SDK state: ${VisionBotSDKManager.isCameraEnabled()}")
                }
            }) {
                Text(if (isCameraEnabledUi) "Turn Camera Off" else "Turn Camera On")
            }

            Button(onClick = {
                coroutineScope.launch {
                    val success =
                        VisionBotSDKManager.setMicrophoneEnabled(!VisionBotSDKManager.isMicrophoneEnabled())
                    addLog("Set Mic toggle: SDK reported $success. New SDK state: ${VisionBotSDKManager.isMicrophoneEnabled()}")
                }
            }) {
                Text(if (isMicrophoneEnabledUi) "Mute Mic" else "Unmute Mic")
            }
            Button(
                enabled = !isFlipping, onClick = {
                    coroutineScope.launch {
                        isFlipping = true
                        try {
                            val success = VisionBotSDKManager.flipCameraPosition()
                            if (success) {
                                val videoTrack = VisionBotSDKManager.getLocalParticipant()
                                    ?.getOrCreateDefaultVideoTrack()
                                if (videoTrack != null) {

                                    Log.d(
                                        "SDKInteractionScreen",
                                        "Flipped camera. Mirroring set to: ${videoTrack.options.position}"
                                    )

                                    val isFrontCamera =
                                        videoTrack.options.position == CameraPosition.FRONT
                                    surfaceRendererRef.value?.setMirror(!isFrontCamera)
                                    Log.d(
                                        "SDKInteractionScreen",
                                        "Flipped camera. Mirroring set to: $isFrontCamera"
                                    )
                                    addLog("Camera flipped successfully.")
                                } else {
                                    addLog("Flipped camera, but failed to get video track.")
                                }
                            } else {
                                addLog("Failed to flip camera.")
                            }
                        } catch (e: Exception) {
                            addLog("An error occurred while flipping camera: ${e.message}")
                            Log.e("SDKInteractionScreen", "Flip camera error", e)
                        } finally {
                            isFlipping = false
                        }
                    }
                }) {
                Text("Flip")
            }
        }

        Text("Camera: ${if (isCameraEnabledUi) "ON" else "OFF"} | Mic: ${if (isMicrophoneEnabledUi) "ON" else "OFF"}")

        Button(
            onClick = {
                if (isConnected) {
                    pickFileLauncher.launch("image/*")
                } else {
                    addLog("Connect to a room before sending an image.")
                }
            }, enabled = isConnected
        ) {
            Text("Pick & Send Image")
        }

        Button(
            onClick = {
                val renderer = surfaceRendererRef.value

                if (renderer == null) {
                    Log.e("VisionSDK", "Renderer is not initialized")
                    return@Button
                }

                VisionBotSDKManager.initializeVideoRenderer(renderer)
            }, enabled = isConnected
        ) {
            Text("Vinay Checks")
        }
    }
}
