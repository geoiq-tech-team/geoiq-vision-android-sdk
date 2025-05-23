package com.geoiq.geoiq_android_lk_vision_bot_sdk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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


class MainActivity : ComponentActivity() {

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("MainActivityPermissions", "${it.key} = ${it.value}")
            }
            // Handle permission results if needed, e.g., show a message if not granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        val permissionsToRequest = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
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
        // Important: Shutdown the SDK when the activity is destroyed
        // to release resources and cancel coroutines.
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
    var socketUrl by remember { mutableStateOf("wss://lk.diq.geoiq.ai") } // TODO: Replace with your URL
    var accessToken by remember { mutableStateOf("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoiOUFPNlRDRko0JCIsInZpZGVvIjp7InJvb21Kb2luIjp0cnVlLCJyb29tIjoiOUFPNlRDRko0JCIsImNhblB1Ymxpc2giOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWV9LCJzdWIiOiJOSlVCeE53S1A4IiwiaXNzIjoiQVBJZ1BYNG9hOU1VOUd0IiwibmJmIjoxNzQ3OTk5MjcxLCJleHAiOjE3NDgwMjA4NzF9.8Jsl4LfuKLB_7CbYOyseouijGkDM-0UuCltCduXjGuI") } // TODO: Replace with your token


//    val socketUrl by remember { mutableStateOf("wss://tusheet-website-agent-m4pgool9.livekit.cloud") } // TODO: Replace with your URL
//    val accessToken by remember { mutableStateOf("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoiYXNnYWciLCJ2aWRlbyI6eyJyb29tSm9pbiI6dHJ1ZSwicm9vbSI6ImFzZ2FnIiwiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuU3Vic2NyaWJlIjp0cnVlLCJjYW5QdWJsaXNoRGF0YSI6dHJ1ZX0sInN1YiI6ImF2aWEiLCJpc3MiOiJBUEl1QkE1RkR5aEpOUGMiLCJuYmYiOjE3NDc5MzQ3OTksImV4cCI6MTc0Nzk1NjM5OX0.vJWji62l4x-wr-E50CjsXoje4lX8FICSY2WONCpAwEs") } // TODO: Replace with your token
    var eventLog by remember { mutableStateOf(listOf<String>()) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    // Use mutableStateOf(VisionBotSDKManager.isCameraEnabled()) to initialize with the current state
    var isCameraEnabledUi by remember { mutableStateOf(VisionBotSDKManager.isCameraEnabled()) }
    // Similarly initialize isMicrophoneEnabledUi
    var isMicrophoneEnabledUi by remember { mutableStateOf(VisionBotSDKManager.isMicrophoneEnabled()) }

    var isSpeaking by remember { mutableStateOf(VisionBotSDKManager.getIsSpeaking()) }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventLog = eventLog + "[$timestamp] $message"
        Log.d("SDKInteractionScreen", message)
    }

    fun attachLocalVideo(videoTrack: LocalVideoTrack) {
        val renderer = surfaceRendererRef.value
        if (renderer != null) {
            VisionBotSDKManager.getCurrentroom()?.initVideoRenderer(renderer)
            videoTrack.addRenderer(renderer)
        } else {
            Log.w("SDK", "Renderer is null; cannot attach video track")
        }
    }

    // Collect events from the SDK
    LaunchedEffect(Unit) {
        addLog("Initial camera state: camera enabled = ${VisionBotSDKManager.isCameraEnabled()}, mic enabled = ${VisionBotSDKManager.isMicrophoneEnabled()}")

        VisionBotSDKManager.events.collect { event ->
            addLog("Vinay Event: $event")
            val res = VisionBotSDKManager.currentRoom?.audioTrackCaptureDefaults?.toString()
            val res2 = VisionBotSDKManager.currentRoom?.videoTrackCaptureDefaults?.toString()
            addLog("Vinay Audio Track Capture Defaults: $res, $res2")
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
                    // Update UI based on initial SDK state after connection
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()

                    /* coroutineScope.launch {
                         val success = VisionBotSDKManager.setMicrophoneEnabled(!isMicrophoneEnabledUi)
                         isMicrophoneEnabledUi= !isMicrophoneEnabledUi
                         addLog("Set Mic to ${!isMicrophoneEnabledUi}: SDK reported $success")
                         // isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled() // Or wait for event
                     }*/
                }
                is GeoVisionEvent.Disconnected -> {
                    addLog("Vinay Disconnected. Reason: ${event.reason ?: "Client initiated"}")
                    connectionStatus = "Disconnected"
                    isConnecting = false
                    isConnected = false

                    // Update UI based on SDK state after disconnection
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()
                }
                is GeoVisionEvent.ParticipantJoined -> {
                    addLog("Vinay Participant joined: ${event.participant.identity}")
                }
                is GeoVisionEvent.ParticipantAttributesChanged -> {
                    addLog("Vinay Participant joined: ${event.participant.identity}")
                }
                is GeoVisionEvent.ParticipantLeft -> {
                    addLog("Vinay Participant left: ${event.participant.identity}")
                }
                is GeoVisionEvent.TrackPublished -> {
                    addLog("Vinay Local track published: ${event.publication.source} by ${event.participant.identity}")
                    VisionBotSDKManager.setMicrophoneEnabled(true)

                    // Only handle video tracks
                    addLog("Vinay Track published: ${event.publication.source} by ${event.participant.identity}")

                    if (event.publication.source?.name?.lowercase() == "camera") {
                        val localParticipant = VisionBotSDKManager.getCurrentroom()?.localParticipant

                        val localTrack = localParticipant
                            ?.getTrackPublication(event.publication.source)
                            ?.track

                        if (localTrack is LocalVideoTrack) {
                            attachLocalVideo(localTrack)
                        } else {
                            addLog("Expected LocalVideoTrack but got ${localTrack?.javaClass?.simpleName}")
                        }
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
                    if ( event.message.contains("Failed to connect") || event.message.contains("Connection setup failed")) {
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
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    surfaceRendererRef.value = this
                    setEnableHardwareScaler(true)
                    // Properly initialize
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            onRelease = {
                it.release() // Properly release renderer
                surfaceRendererRef.value = null
            }
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
                    VisionBotSDKManager.disconnectFromGeoVisionRoom()
                },
                enabled = isConnected || isConnecting
            ) {
                Text("Disconnect")
            }
        }

        Text("Status: $connectionStatus")

        Text(
            text = "Speaking: ${
                isSpeaking
            }",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                coroutineScope.launch {
                    val success = VisionBotSDKManager.setCameraEnabled(!isCameraEnabledUi)
                    isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled()
                    addLog("Set Camera to ${!isCameraEnabledUi}: SDK reported $success")
                    // Optimistic update, real update comes from TrackPublished/Unpublished event
                    // or you can refresh state after the call if SDK allows synchronous check
                    // isCameraEnabledUi = VisionBotSDKManager.isCameraEnabled() // Or wait for event
                }
            }) {
                Text(if (isCameraEnabledUi) "Turn Camera Off" else "Turn Camera On")
            }

            Button(onClick = {
                coroutineScope.launch {
                    val success = VisionBotSDKManager.setMicrophoneEnabled(!isMicrophoneEnabledUi)
                    isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled()
                    addLog("Set Mic to ${!isMicrophoneEnabledUi}: SDK reported $success")
                    //isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled() // Or wait for event
                }
            }) {
                Text(if (isMicrophoneEnabledUi) "Mute Mic" else "Unmute Mic")
            }
        }

        Text("Camera: ${if (isCameraEnabledUi) "ON" else "OFF"} | Mic: ${if (isMicrophoneEnabledUi) "ON" else "OFF"}")

        Button(onClick = {
            coroutineScope.launch {
                addLog("Vinay Sending messsage ")
                VisionBotSDKManager.getLocalParticipant()?.publishData(
                    "cart".toByteArray(Charsets.UTF_8),
                    DataPublishReliability.RELIABLE,
                    "transfer_to_agent_"
                )

                // isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled() // Or wait for event
            }

            addLog("Vinay Sent message on my-topic")
        }) {
            Text("Send Message")
        }

        Button(onClick = {
            coroutineScope.launch {
                VisionBotSDKManager.shutdown()
                addLog("Vinay Shutdown ")
                // isMicrophoneEnabledUi = VisionBotSDKManager.isMicrophoneEnabled() // Or wait for event
            }
        }) {
            Text("Shutdown SDK")
        }
    }
}
