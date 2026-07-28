package com.geoiq.geoiq_android_lk_vision_bot_sdk.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoiq.geoiq_android_lk_vision_bot_sdk.MainIntent
import com.geoiq.geoiq_android_lk_vision_bot_sdk.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                        Icon(Icons.Default.Restore, contentDescription = "Reset to build defaults")
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
            var urlDraft by rememberSaveable { mutableStateOf(state.geoVisionUrl) }
            var tokenUrlDraft by rememberSaveable { mutableStateOf(state.tokenUrl) }
            var keyDraft by rememberSaveable { mutableStateOf(state.apiKey) }

            LaunchedEffect(state.geoVisionUrl, state.tokenUrl, state.apiKey) {
                urlDraft = state.geoVisionUrl
                tokenUrlDraft = state.tokenUrl
                keyDraft = state.apiKey
            }

            val urlInvalid = urlDraft.isNotBlank() &&
                !urlDraft.startsWith("ws://") &&
                !urlDraft.startsWith("wss://")
            val tokenUrlInvalid = tokenUrlDraft.isNotBlank() && !tokenUrlDraft.startsWith("https://")
            val draftValid = !urlInvalid && !tokenUrlInvalid &&
                urlDraft.isNotBlank() && tokenUrlDraft.isNotBlank() && keyDraft.isNotBlank()
            val isDirty = urlDraft != state.geoVisionUrl ||
                tokenUrlDraft != state.tokenUrl ||
                keyDraft != state.apiKey

            SettingsSection("Configuration") {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("LiveKit URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = urlInvalid,
                    supportingText = {
                        Text(if (urlInvalid) "Must start with wss:// or ws://" else "Applies on next connect")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = tokenUrlDraft,
                    onValueChange = { tokenUrlDraft = it },
                    label = { Text("Token endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = tokenUrlInvalid,
                    supportingText = {
                        Text(
                            if (tokenUrlInvalid) {
                                "Must start with https://"
                            } else {
                                "POSTed to for an access token before connecting"
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
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = keyDraft.isBlank(),
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
                            if (keyDraft.isBlank()) {
                                "Required — token requests will fail"
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

            Button(
                onClick = {
                    viewModel.onIntent(
                        MainIntent.SaveConfig(
                            geoVisionUrl = urlDraft.trim(),
                            apiKey = keyDraft.trim(),
                            tokenUrl = tokenUrlDraft.trim(),
                        )
                    )
                },
                enabled = isDirty && draftValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
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
