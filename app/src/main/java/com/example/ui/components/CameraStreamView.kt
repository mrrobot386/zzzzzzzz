package com.example.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.service.CameraEngine

@Composable
fun CameraStreamView(
    cameraEngine: CameraEngine,
    isVideoEnabled: Boolean,
    isPipMode: Boolean,
    isFrontCamera: Boolean,
    onTogglePip: () -> Unit,
    onSwitchCamera: () -> Unit,
    onInspectImmediate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = if (isPipMode) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0F172A))
            .then(
                if (isPipMode) {
                    Modifier.border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFFA855F7))
                        ),
                        shape = shape
                    )
                } else Modifier
            )
    ) {
        if (isVideoEnabled) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        cameraEngine.setPreviewView(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Live sight badge
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = if (isFrontCamera) "FRONT CAM" else "LIVE VISION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // Camera Overlay Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Immediate focus/inspect button
                IconButton(
                    onClick = onInspectImmediate,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("inspect_frame_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Inspect frame now",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Switch front/back camera
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("switch_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Toggle PIP / Fullscreen
                IconButton(
                    onClick = onTogglePip,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("toggle_pip_button")
                ) {
                    Icon(
                        imageVector = if (isPipMode) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                        contentDescription = "Toggle fullscreen camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            // Video disabled state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Camera paused",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Camera is paused",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
