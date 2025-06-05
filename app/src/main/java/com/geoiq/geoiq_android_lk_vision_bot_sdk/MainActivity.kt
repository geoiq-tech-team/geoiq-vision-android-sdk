package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.geoiq.geoiq_android_lk_vision_bot_sdk.ui.theme.GEOIQANDROIDLKVISIONBOTSDKTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.SurfaceViewRenderer
import java.io.File
import android.content.Context
import android.provider.OpenableColumns
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import org.json.JSONObject


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
            // Manifest.permission.READ_EXTERNAL_STORAGE // Maybe for older devices accessing gallery
            // Manifest.permission.READ_MEDIA_IMAGES // For Android 13+ Photo Picker
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
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
    var isRendererInitialized by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var socketUrl by remember { mutableStateOf("wss://lk-stg1.diq.geoiq.ai") }
    var accessToken by remember { mutableStateOf("eyJhbGciOiJIUzI1NiJ9.eyJ2aWRlbyI6eyJyb29tIjoicm9vbS1JQ0x2LW1HcUwiLCJyb29tSm9pbiI6dHJ1ZSwiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZX0sImlzcyI6IkFQSUJGUVFSNHJ4RnpwaCIsImV4cCI6MTc0OTEzOTkwOCwibmJmIjowLCJzdWIiOiJpZGVudGl0eS1SdzhwIn0.WPHfV5gJYXlFgvhWLRUStWSix5tY_USH4PIGpVw4w2Q") } // TODO: Replace with your valid token
    var eventLog by remember { mutableStateOf(listOf<String>()) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    var isCameraEnabledUi by remember { mutableStateOf(VisionBotSDKManager.isCameraEnabled()) }
    var isMicrophoneEnabledUi by remember { mutableStateOf(VisionBotSDKManager.isMicrophoneEnabled()) }
    var isSpeaking by remember { mutableStateOf(VisionBotSDKManager.getIsSpeaking()) }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventLog = eventLog + "[$timestamp] $message"
        Log.d("SDKInteractionScreen", message)
    }

    fun createFileFromUri(context: Context, uri: Uri): File? {
        var fileName = "temp_picked_file" // Default filename
        // Try to get the actual file name from the URI
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
            // Clean up the temporary file if an error occurs during copy
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
        contract = ActivityResultContracts.GetContent(),
        onResult = { fileUri: Uri? ->
            if (fileUri != null) {
                if (isConnected) { // Check if connected to the room
                    coroutineScope.launch {
                        addLog("File URI selected: $fileUri")

                        // Create a File object from the Uri
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
        }
    )



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
        Log.d("VisionSDK", "Attaching local video track: ${videoTrack.name}, renderer :: ${renderer}")
        if (renderer == null) {
            Log.e("VisionSDK", "Renderer is not initialized")
            return
        }
        try {
            videoTrack.addRenderer(renderer)
            Log.d("VisionSDK", "Track attached")
        } catch (e: Exception) {
            Log.e("VisionSDK", "Error attaching renderer: ${e.localizedMessage}")
        }
        try {
            // Re-init renderer every time it's a new instance (safe and idempotent)
                VisionBotSDKManager.getCurrentroom()?.initVideoRenderer(renderer)
                Log.d("VisionSDK", "Renderer initialized")

        } catch (e: Exception) {
            Log.e("VisionSDK", "Error initializing renderer: ${e.localizedMessage}")
            return
        }
    }




    LaunchedEffect(Unit) {
        addLog("Initial camera state: camera enabled = ${VisionBotSDKManager.isCameraEnabled()}, mic enabled = ${VisionBotSDKManager.isMicrophoneEnabled()}")

        VisionBotSDKManager.events.collect { event ->
            addLog("Vinay Event: $event")

            when (event) {
                is GeoVisionEvent.Connecting -> {
                    addLog("Vinay Connecting to ${event.url} with token ending in ...${event.tokenSnippet}")
                    connectionStatus = "Connecting..."
                    isConnecting = true
                    isConnected = false
                }

                is GeoVisionEvent.Connected -> {
                    addLog("Vinay Connected to room: ${event.roomName}. Local User: ${event.localParticipant.identity}")
                    connectionStatus = "Connected: ${event.roomName}"
                    isConnecting = false
                    isConnected = true
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()

                    // Initialize renderer on successful connection
                }

                is GeoVisionEvent.Disconnected -> {
                    addLog("Vinay Disconnected. Reason: ${event.reason ?: "Client initiated"}")
                    connectionStatus = "Disconnected"
                    isConnecting = false
                    isConnected = false
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()

//
//                    // Reset renderer state on disconnection
                    surfaceRendererRef.value?.let { renderer ->
                        try {
                            // Clear any existing video tracks
                            renderer.clearImage()
                            renderer.release()

                        } catch (e: Exception) {
                            Log.e("SDKInteractionScreen", "Error clearing renderer: ${e.localizedMessage}")
                        }
                    }

                    // Mark that renderer needs reset for next connection
//                    isRendererInitialized = false
//                    rendererNeedsReset = true

                }

                is GeoVisionEvent.ParticipantJoined -> {
                    addLog("Vinay Participant joined: ${event.participant.identity}")
                    coroutineScope.launch {
                        addLog("Vinay Sending messsage on Connect  ")
                        VisionBotSDKManager.getLocalParticipant()?.publishData(
                            "BOT_CONNECTED".toByteArray(Charsets.UTF_8),
                            DataPublishReliability.RELIABLE, // Ensure this enum is correctly imported/available
                            "vinay_bot_connected" // topic
                        )
                    }
                }

                is GeoVisionEvent.ParticipantAttributesChanged -> {
                    addLog("Vinay Participant joined: ${event.participant.identity}")
                }

                is GeoVisionEvent.ParticipantLeft -> {
                    addLog("Vinay Participant left: ${event.participant.identity}")
                }

                is GeoVisionEvent.TrackPublished -> {
                    addLog("Local track published: ${event.publication.source} by ${event.participant.identity}")
                    // Consider if VisionBotSDKManager.setMicrophoneEnabled(true) is always desired here.
                    // It might be better to let the user control it or handle based on specific track types.

                    if (event.publication.source?.name?.lowercase() == "camera") {
                        val localParticipant =
                            VisionBotSDKManager.getCurrentroom()?.localParticipant
                        val localTrack = localParticipant
                            ?.getTrackPublication(event.publication.source)
                            ?.track

                        if (localTrack is LocalVideoTrack) {
                            attachLocalVideo(localTrack)
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
                    addLog("VinayTrack subscribed: ${event.track.name} from ${event.participant.identity}")
                }

                is GeoVisionEvent.TrackUnsubscribed -> {
                    addLog("Vinay Track unsubscribed: ${event.track.name} from ${event.participant.identity}")
                }

                is GeoVisionEvent.ActiveSpeakersChanged -> {
                    isSpeaking = VisionBotSDKManager.getIsSpeaking()
                    addLog("Vinay Active speakers: ${event.speakers.joinToString { it.identity?.toString() ?: "N/A" }}")
                }

                is GeoVisionEvent.TranscriptionReceived -> {
                    addLog("Vinay Transcription: ${event.senderId} ${event.message}")

                }

                is GeoVisionEvent.Error -> {
                    addLog("Vinay ERROR: ${event.message} ${event.exception?.localizedMessage ?: ""}")
                    if (event.message.contains("Failed to connect") || event.message.contains("Connection setup failed")) {
                        isConnecting = false
                        isConnected = false
                        connectionStatus = "Error Connecting"
                    }
                }

                is GeoVisionEvent.CustomMessageReceived -> {
                    addLog("Vinay GeoIQ Received message on my-topic: ${event.message}")
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
        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    surfaceRendererRef.value = this
                    setEnableHardwareScaler(true)

                    setMirror(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
//            onRelease = { renderer ->
//                try {
//                    renderer.release()
//                } catch (e: Exception) {
//                    Log.e("SDKInteractionScreen", "Error releasing renderer: ${e.localizedMessage}")
//                }
////                surfaceRendererRef.value = null
////                isRendererInitialized = false
//            }
        )

//        OutlinedTextField(
//            value = socketUrl,
//            onValueChange = { socketUrl = it },
//            label = { Text("Socket URL") }
//        )
//        OutlinedTextField(
//            value = accessToken,
//            onValueChange = { accessToken = it },
//            label = { Text("Access Token") }
//        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (socketUrl.isNotBlank() && accessToken.isNotBlank()) {
                        VisionBotSDKManager.connectToGeoVisionRoom(context, socketUrl, accessToken)
                    } else {
                        addLog("URL or Token is empty!")
                    }
                },
                enabled = !isConnecting && !isConnected
            ) {
                Text("Connect")
            }
            Button(
                onClick = {
                    coroutineScope.launch { // disconnectFromGeoVisionRoom is now suspend
                        VisionBotSDKManager.disconnectFromGeoVisionRoom()
                    }
                },
                enabled = isConnected || isConnecting
            ) {
                Text("Disconnect")
            }
        }

        Text("Status: $connectionStatus")
        Text("Speaking: $isSpeaking")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                coroutineScope.launch {
                    // val newCameraState = !isCameraEnabledUi // UI state might be stale
                    // It's safer to toggle based on the SDK's current reported state if possible,
                    // or just send the toggle command and let events update the UI.
                    val success =
                        VisionBotSDKManager.setCameraEnabled(!VisionBotSDKManager.isCameraEnabled())
                    // isCameraEnabledUi will be updated by TrackPublished/Unpublished events
                    addLog("Set Camera toggle: SDK reported $success. New SDK state: ${VisionBotSDKManager.isCameraEnabled()}")
                }
            }) {
                Text(if (isCameraEnabledUi) "Turn Camera Off" else "Turn Camera On")
            }

            Button(onClick = {
                coroutineScope.launch {
                    val success =
                        VisionBotSDKManager.setMicrophoneEnabled(!VisionBotSDKManager.isMicrophoneEnabled())
                    // isMicrophoneEnabledUi will be updated by events
                    addLog("Set Mic toggle: SDK reported $success. New SDK state: ${VisionBotSDKManager.isMicrophoneEnabled()}")
                }
            }) {
                Text(if (isMicrophoneEnabledUi) "Mute Mic" else "Unmute Mic")
            }
        }

        Text("Camera: ${if (isCameraEnabledUi) "ON" else "OFF"} | Mic: ${if (isMicrophoneEnabledUi) "ON" else "OFF"}")


        // Button to pick and send an image
        Button(
            onClick = {
                if (isConnected) {
                    pickFileLauncher.launch("image/*") // Launches the image picker
                } else {
                    addLog("Connect to a room before sending an image.")
                }
            },
            enabled = isConnected // Only enable if connected
        ) {
            Text("Pick & Send Image")
        }

        Button(
            onClick = {
                if (isConnected) {
                    coroutineScope.launch {
                        addLog("Attempting to send a file via RPC call")
                        try {
                            performRpcCall()
                            addLog("RPC call performed successfully.")
                        } catch (e: Exception) {
                            addLog("RPC call failed: ${e.localizedMessage}")
                        }
                    }
                } else {
                    addLog("Connect to a room before sending an image.")
                }
            },
            enabled = isConnected // Only enable if connected
        ) {
            Text("Perform RPC")
        }


        Button(onClick = {
            coroutineScope.launch {
                VisionBotSDKManager.shutdown()
                addLog("Shutdown SDK called from button")
            }
        }) {
            Text("Shutdown SDK")
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(eventLog.reversed()) { logEntry -> // Reversed to show newest logs first
                Text(logEntry, style = MaterialTheme.typography.bodySmall)
                Divider()
            }
        }
    }
}


