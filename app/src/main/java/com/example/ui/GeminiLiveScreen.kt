package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LiveAgentState
import com.example.data.model.LiveConnectionState
import com.example.service.CameraEngine
import com.example.ui.components.CameraStreamView
import com.example.ui.components.GeminiOrbVisualizer
import com.example.ui.components.TranscriptView
import com.example.ui.components.VoiceSelectorDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeminiLiveScreen(
    viewModel: GeminiLiveViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraEngine = remember(context, lifecycleOwner) {
        CameraEngine(context, lifecycleOwner).apply {
            onFrameCaptured = { bytes ->
                viewModel.onFrameCaptured(bytes)
            }
        }
    }

    DisposableEffect(cameraEngine) {
        onDispose {
            cameraEngine.release()
        }
    }

    // Camera and Audio Permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF131B2E),
                            Color(0xFF090D16),
                            Color(0xFF05070B)
                        ),
                        radius = 1200f
                    )
                )
        ) {
            // Main content layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header Bar
                LiveTopBar(
                    connectionState = uiState.connectionState,
                    sessionSeconds = uiState.sessionSeconds,
                    selectedVoiceName = uiState.selectedVoice.displayName,
                    onOpenVoiceDialog = { viewModel.setVoiceDialogVisible(true) }
                )

                // Missing API Key Banner
                if (!uiState.hasApiKey) {
                    ApiKeyMissingBanner()
                }

                // Error Banner if any
                if (uiState.errorMessage != null) {
                    ErrorBanner(
                        message = uiState.errorMessage ?: "",
                        onDismiss = { viewModel.endSession() }
                    )
                }

                // Permission Request Card if not granted
                if (!permissionsState.allPermissionsGranted) {
                    PermissionRequestCard(
                        onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }

                // Central Workspace: Orb Visualizer and Camera
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.isPipMode && uiState.isVideoEnabled) {
                        // Fullscreen Camera Mode
                        CameraStreamView(
                            cameraEngine = cameraEngine,
                            isVideoEnabled = uiState.isVideoEnabled,
                            isPipMode = false,
                            isFrontCamera = uiState.isFrontCamera,
                            onTogglePip = { viewModel.togglePip() },
                            onSwitchCamera = {
                                cameraEngine.toggleCameraFacing()
                                viewModel.toggleCameraFacing()
                            },
                            onInspectImmediate = { cameraEngine.captureImmediateFrame() },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )

                        // Floating mini visualizer badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.7f))
                                .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), CircleShape)
                        ) {
                            GeminiOrbVisualizer(
                                agentState = uiState.agentState,
                                geminiAmplitude = uiState.geminiAmplitude,
                                userAmplitude = uiState.userAmplitude,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Standard Mode: Central Glowing Orb
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(270.dp)
                                    .testTag("gemini_orb_container"),
                                contentAlignment = Alignment.Center
                            ) {
                                GeminiOrbVisualizer(
                                    agentState = uiState.agentState,
                                    geminiAmplitude = uiState.geminiAmplitude,
                                    userAmplitude = uiState.userAmplitude,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Agent State Status Label
                            AgentStateIndicator(
                                agentState = uiState.agentState,
                                connectionState = uiState.connectionState,
                                isMuted = uiState.isMuted
                            )
                        }

                        // PIP Camera (Corner Floating Window)
                        if (uiState.isVideoEnabled && permissionsState.allPermissionsGranted) {
                            CameraStreamView(
                                cameraEngine = cameraEngine,
                                isVideoEnabled = uiState.isVideoEnabled,
                                isPipMode = true,
                                isFrontCamera = uiState.isFrontCamera,
                                onTogglePip = { viewModel.togglePip() },
                                onSwitchCamera = {
                                    cameraEngine.toggleCameraFacing()
                                    viewModel.toggleCameraFacing()
                                },
                                onInspectImmediate = { cameraEngine.captureImmediateFrame() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp)
                                    .size(width = 120.dp, height = 160.dp)
                                    .testTag("pip_camera_preview")
                            )
                        }
                    }
                }

                // Preset test items for testing vision comprehension
                TestVisionItemsRow(
                    onInspectItem = { label ->
                        if (uiState.connectionState != LiveConnectionState.CONNECTED) {
                            viewModel.startSession()
                        }
                        viewModel.inspectSampleImage(label)
                    }
                )

                // Conversation Transcript Feed
                TranscriptView(
                    messages = uiState.messages,
                    onSendPrompt = { prompt ->
                        if (uiState.connectionState != LiveConnectionState.CONNECTED) {
                            viewModel.startSession()
                        }
                        viewModel.sendTextMessage(prompt)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Bottom Call Controls Bar
                CallControlsBar(
                    connectionState = uiState.connectionState,
                    isMuted = uiState.isMuted,
                    isVideoEnabled = uiState.isVideoEnabled,
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleVideo = {
                        viewModel.toggleVideo()
                        cameraEngine.setStreamingVideo(!uiState.isVideoEnabled)
                    },
                    onToggleCall = {
                        if (uiState.connectionState == LiveConnectionState.CONNECTED ||
                            uiState.connectionState == LiveConnectionState.CONNECTING
                        ) {
                            viewModel.endSession()
                        } else {
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                            viewModel.startSession()
                        }
                    },
                    onInspectNow = {
                        cameraEngine.captureImmediateFrame()
                    }
                )
            }
        }
    }

    // Voice Selector Dialog
    if (uiState.showVoiceDialog) {
        VoiceSelectorDialog(
            currentVoice = uiState.selectedVoice,
            onVoiceSelected = { voice -> viewModel.selectVoice(voice) },
            onDismiss = { viewModel.setVoiceDialogVisible(false) }
        )
    }
}

@Composable
fun LiveTopBar(
    connectionState: LiveConnectionState,
    sessionSeconds: Int,
    selectedVoiceName: String,
    onOpenVoiceDialog: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // App Identity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFFA855F7))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "Gemini Live",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Real-time Voice & Vision",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Status & Voice Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live / Status pill
            Surface(
                color = when (connectionState) {
                    LiveConnectionState.CONNECTED -> Color(0xFF065F46).copy(alpha = 0.8f)
                    LiveConnectionState.CONNECTING -> Color(0xFF854D0E).copy(alpha = 0.8f)
                    LiveConnectionState.ERROR -> Color(0xFF7F1D1D).copy(alpha = 0.8f)
                    LiveConnectionState.DISCONNECTED -> Color(0xFF1E293B).copy(alpha = 0.8f)
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    LiveConnectionState.CONNECTED -> Color(0xFF10B981)
                                    LiveConnectionState.CONNECTING -> Color(0xFFFBBF24)
                                    LiveConnectionState.ERROR -> Color(0xFFEF4444)
                                    LiveConnectionState.DISCONNECTED -> Color(0xFF64748B)
                                }
                            )
                    )
                    Text(
                        text = when (connectionState) {
                            LiveConnectionState.CONNECTED -> {
                                val mins = sessionSeconds / 60
                                val secs = sessionSeconds % 60
                                String.format("%02d:%02d", mins, secs)
                            }
                            LiveConnectionState.CONNECTING -> "Connecting..."
                            LiveConnectionState.ERROR -> "Offline"
                            LiveConnectionState.DISCONNECTED -> "Ready"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            // Voice Selector Chip
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clickable { onOpenVoiceDialog() }
                    .testTag("voice_selector_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Voice",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = selectedVoiceName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AgentStateIndicator(
    agentState: LiveAgentState,
    connectionState: LiveConnectionState,
    isMuted: Boolean
) {
    val text = when {
        connectionState != LiveConnectionState.CONNECTED -> "Tap the Start Call button to begin"
        isMuted -> "Microphone is muted"
        agentState == LiveAgentState.SPEAKING -> "Gemini is speaking..."
        agentState == LiveAgentState.THINKING -> "Gemini is thinking..."
        agentState == LiveAgentState.LISTENING -> "Gemini is listening..."
        else -> "Ready"
    }

    val color = when {
        connectionState != LiveConnectionState.CONNECTED -> Color(0xFF94A3B8)
        isMuted -> Color(0xFFF87171)
        agentState == LiveAgentState.SPEAKING -> Color(0xFFC084FC)
        agentState == LiveAgentState.THINKING -> Color(0xFFF472B6)
        agentState == LiveAgentState.LISTENING -> Color(0xFF38BDF8)
        else -> Color(0xFF94A3B8)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = color,
        textAlign = TextAlign.Center
    )
}

@Composable
fun TestVisionItemsRow(
    onInspectItem: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Test Sight:",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )

        listOf("Math Equation", "Circuit Diagram", "Document").forEach { label ->
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clickable { onInspectItem(label) }
                    .testTag("test_vision_${label.lowercase().replace(" ", "_")}")
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CallControlsBar(
    connectionState: LiveConnectionState,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleCall: () -> Unit,
    onInspectNow: () -> Unit
) {
    val isLive = connectionState == LiveConnectionState.CONNECTED || connectionState == LiveConnectionState.CONNECTING

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            // Mute / Unmute
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) Color(0xFFDC2626) else Color(0xFF1E293B))
                    .testTag("mute_toggle_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Video Camera Toggle
            IconButton(
                onClick = onToggleVideo,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (!isVideoEnabled) Color(0xFF475569) else Color(0xFF1E293B))
                    .testTag("video_toggle_button")
            ) {
                Icon(
                    imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (isVideoEnabled) "Turn off video" else "Turn on video",
                    tint = if (isVideoEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Instant Sight / Inspect button
            IconButton(
                onClick = onInspectNow,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .testTag("inspect_sight_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Inspect sight immediately",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Primary Call Action Button (Start / End Call)
            Button(
                onClick = onToggleCall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive) Color(0xFFDC2626) else Color(0xFF0284C7)
                ),
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("call_action_button")
            ) {
                Icon(
                    imageVector = if (isLive) Icons.Default.CallEnd else Icons.Default.Call,
                    contentDescription = if (isLive) "End Call" else "Start Gemini Live Call",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionRequestCard(
    onRequestPermissions: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Microphone & Camera Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "Gemini Live needs microphone and camera access to stream real-time audio and vision to the Multimodal AI.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("grant_permissions_button")
            ) {
                Text("Grant Permissions", color = Color.White)
            }
        }
    }
}

@Composable
fun ApiKeyMissingBanner() {
    Surface(
        color = Color(0xFF7C2D12),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFDBA74),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Configure GEMINI_API_KEY in the Secrets panel to activate real-time calls.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFED7AA)
            )
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF450A0A),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFECACA)
                )
            }
            Text(
                text = "Dismiss",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF87171),
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(4.dp)
            )
        }
    }
}
