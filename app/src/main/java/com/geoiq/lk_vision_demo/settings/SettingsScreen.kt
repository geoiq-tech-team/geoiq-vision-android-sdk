package com.geoiq.lk_vision_demo.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geoiq.lk_vision_demo.MainIntent
import com.geoiq.lk_vision_demo.MainViewModel
import com.geoiq.lk_vision_demo.ModeEnvConfig
import com.geoiq.lk_vision_demo.SessionMode
import com.geoiq.lk_vision_demo.agentType
import com.geoiq.lk_vision_demo.data.GeoEnv

/** Editable staging fields for one mode, plus whether they differ from what is persisted. */
private class StagingDraft(
    initial: ModeEnvConfig,
) {
    var socketUrl by mutableStateOf(initial.stagingSocketUrl)
    var apiKey by mutableStateOf(initial.stagingApiKey)

    val socketUrlInvalid: Boolean
        get() = socketUrl.isNotBlank() && !GeoEnv.isValidSocketUrl(socketUrl.trim())

    val isValid: Boolean
        get() = !socketUrlInvalid && socketUrl.isNotBlank() && apiKey.isNotBlank()

    fun isDirty(persisted: ModeEnvConfig): Boolean =
        socketUrl.trim() != persisted.stagingSocketUrl || apiKey.trim() != persisted.stagingApiKey

    fun syncFrom(persisted: ModeEnvConfig) {
        socketUrl = persisted.stagingSocketUrl
        apiKey = persisted.stagingApiKey
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val voiceDraft = remember { StagingDraft(state.voiceEnv) }
    val chatDraft = remember { StagingDraft(state.chatEnv) }

    // Pull persisted values back into the fields after a save or a reset.
    LaunchedEffect(state.voiceEnv) { voiceDraft.syncFrom(state.voiceEnv) }
    LaunchedEffect(state.chatEnv) { chatDraft.syncFrom(state.chatEnv) }

    val dirtyDrafts = buildList {
        if (voiceDraft.isDirty(state.voiceEnv)) add(SessionMode.Video to voiceDraft)
        if (chatDraft.isDirty(state.chatEnv)) add(SessionMode.Chat to chatDraft)
    }
    val canSave = dirtyDrafts.isNotEmpty() && dirtyDrafts.all { it.second.isValid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onIntent(MainIntent.ResetConfig) },
                        enabled = state.isConfigModified
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Reset to defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Voice and chat are separate agents on separate deployments. Each mode " +
                    "picks its own; the token host follows from the socket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DeploymentSection(
                title = "Session (voice)",
                mode = SessionMode.Video,
                env = state.voiceEnv,
                draft = voiceDraft,
                onSelectSource = {
                    viewModel.onIntent(MainIntent.SelectEnvSource(SessionMode.Video, it))
                },
            )

            DeploymentSection(
                title = "Chat",
                mode = SessionMode.Chat,
                env = state.chatEnv,
                draft = chatDraft,
                onSelectSource = {
                    viewModel.onIntent(MainIntent.SelectEnvSource(SessionMode.Chat, it))
                },
            )

            Button(
                onClick = {
                    dirtyDrafts.forEach { (mode, draft) ->
                        viewModel.onIntent(
                            MainIntent.SaveStagingConfig(
                                mode = mode,
                                socketUrl = draft.socketUrl.trim(),
                                apiKey = draft.apiKey.trim(),
                            )
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun DeploymentSection(
    title: String,
    mode: SessionMode,
    env: ModeEnvConfig,
    draft: StagingDraft,
    onSelectSource: (Boolean) -> Unit,
) {
    val prodConfigured = GeoEnv.isProdConfigured(mode)
    SettingsSection("$title — ${mode.agentType.wireValue}") {
        SourceRow(
            selected = env.useProd,
            enabled = prodConfigured,
            onClick = { onSelectSource(true) },
            label = "Production",
            detail = if (prodConfigured) {
                GeoEnv.prodSocketUrl(mode)
            } else {
                "no key — set ${GeoEnv.missingKeyPropertyName(mode)} in local.properties"
            },
            isError = !prodConfigured,
        )

        SourceRow(
            selected = !env.useProd,
            enabled = true,
            onClick = { onSelectSource(false) },
            label = "Staging",
            detail = GeoEnv.TOKEN_BASE_URL_STAGING,
            isError = false,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = draft.socketUrl,
            onValueChange = { draft.socketUrl = it },
            label = { Text("Staging LiveKit URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = draft.socketUrlInvalid,
            supportingText = {
                Text(
                    if (draft.socketUrlInvalid) {
                        "Must start with wss:// or ws://"
                    } else {
                        "Applies on this mode's next connect"
                    }
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false
            )
        )

        Spacer(Modifier.height(8.dp))

        var keyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = draft.apiKey,
            onValueChange = { draft.apiKey = it },
            label = { Text("Staging API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = draft.apiKey.isBlank(),
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (keyVisible) "Hide API key" else "Show API key"
                    )
                }
            },
            supportingText = {
                Text(
                    if (draft.apiKey.isBlank()) {
                        "Required — must be the key for this host"
                    } else {
                        "Sent as the x-api-key header"
                    }
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false
            )
        )
    }
}

@Composable
private fun SourceRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    label: String,
    detail: String,
    isError: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}
