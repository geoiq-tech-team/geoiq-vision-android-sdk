package com.geoiq.geoiq_android_lk_vision_bot_sdk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.livekit.android.renderer.SurfaceViewRenderer
import livekit.org.webrtc.RendererCommon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults

/**
 * @author Sayak Mondal.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SDKInteractionScreen(viewModel: MainViewModel = viewModel()) {
    val rendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    // Side effect: attach the local video track whenever it changes
    val localVideoTrack = viewModel.localVideoTrack
    LaunchedEffect(localVideoTrack, rendererRef.value) {
        val renderer = rendererRef.value ?: return@LaunchedEffect
        if (localVideoTrack != null) {
            VisionBotSDKManager.initializeVideoRenderer(renderer)
            localVideoTrack.addRenderer(renderer)
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && viewModel.isConnected) viewModel.sendFile(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoIQ Vision SDK") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            key(viewModel.rendererSession) {
                VideoPreviewCard(rendererRef, viewModel.isCameraEnabled)
            }

            StatusCard(
                status = viewModel.connectionStatus,
                isConnected = viewModel.isConnected,
                isConnecting = viewModel.isConnecting,
                quality = viewModel.connectionQuality,
                isSpeaking = viewModel.isSpeaking,
                agentState = viewModel.agentState
            )

            ConnectionControls(
                isConnecting = viewModel.isConnecting,
                isConnected = viewModel.isConnected,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() }
            )

            MediaControls(
                enabled = viewModel.isConnected,
                isCameraEnabled = viewModel.isCameraEnabled,
                isMicrophoneEnabled = viewModel.isMicrophoneEnabled,
                isFlipping = viewModel.isFlippingCamera,
                onToggleCamera = { viewModel.toggleCamera() },
                onToggleMicrophone = { viewModel.toggleMicrophone() },
                onFlipCamera = {
                    viewModel.flipCamera { isFront ->
                        rendererRef.value?.setMirror(!isFront)
                    }
                }
            )

            OutlinedButton(
                onClick = { pickFileLauncher.launch("image/*") },
                enabled = viewModel.isConnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send Image")
            }

            EventLogCard(events = viewModel.eventLog)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VideoPreviewCard(rendererRef: MutableState<SurfaceViewRenderer?>, isCameraEnabled: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        rendererRef.value = this
                        setEnableHardwareScaler(true)
                        setMirror(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.release() }
            )
            if (!isCameraEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = "Camera off",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    quality: ConnectionQuality,
    isSpeaking: Boolean,
    agentState: String
) {
    val dotColor = when {
        isConnected -> Color(0xFF4CAF50)
        isConnecting -> Color(0xFFFFC107)
        else -> Color(0xFF9E9E9E)
    }
    val qualityColor = when (quality) {
        ConnectionQuality.EXCELLENT, ConnectionQuality.GOOD -> Color(0xFF4CAF50)
        ConnectionQuality.POOR -> Color(0xFFFF9800)
        ConnectionQuality.LOST -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).background(qualityColor, CircleShape))
                    Row {
                        Text(
                            "Quality: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$quality",
                            style = MaterialTheme.typography.bodySmall,
                            color = qualityColor
                        )
                    }
                }
                if (isSpeaking) {
                    Text(
                        "● Speaking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (agentState.isNotEmpty()) {
                if(isConnected) {
                    Text(
                        "Agent: $agentState",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionControls(
    isConnecting: Boolean,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val canDisconnect = isConnected || isConnecting
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onConnect,
            enabled = !isConnecting && !isConnected,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            )
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isConnecting) "Connecting..." else "Connect")
        }
        OutlinedButton(
            onClick = onDisconnect,
            enabled = canDisconnect,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
            border = BorderStroke(1.dp, if (canDisconnect) Color(0xFFC62828) else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f))
        ) {
            Text("Disconnect")
        }
    }
}

@Composable
private fun MediaControls(
    enabled: Boolean,
    isCameraEnabled: Boolean,
    isMicrophoneEnabled: Boolean,
    isFlipping: Boolean,
    onToggleCamera: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onFlipCamera: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MediaButton(
                icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (isCameraEnabled) "Camera On" else "Camera Off",
                enabled = enabled,
                onClick = onToggleCamera
            )
            MediaButton(
                icon = if (isMicrophoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                label = if (isMicrophoneEnabled) "Mic On" else "Mic Off",
                enabled = enabled,
                onClick = onToggleMicrophone
            )
            MediaButton(
                icon = Icons.Default.FlipCameraAndroid,
                label = "Flip",
                enabled = enabled && !isFlipping && isCameraEnabled,
                onClick = onFlipCamera
            )
        }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EventLogCard(events: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Event Log",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.height(240.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(events) { entry ->
                    Text(
                        entry,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
