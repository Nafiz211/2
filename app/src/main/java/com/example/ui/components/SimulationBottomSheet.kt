package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSimulatePhotoUri: () -> Unit,
    onSimulatePhotoFallback: () -> Unit,
    onSimulateVideo: () -> Unit,
    onSimulateVoiceNote: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Simulate Notification Payloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Trigger test notifications to verify MessagingStyle Content URI streaming and Bitmap fallbacks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SimulationOptionItem(
                icon = Icons.Default.Image,
                iconColor = Color(0xFF0D9488),
                title = "Photo via Content URI (MessagingStyle)",
                description = "Tests context.contentResolver.openInputStream(contentUri) to /Images/IMG_timestamp.jpg",
                testTag = "btn_sim_photo_uri",
                onClick = {
                    onSimulatePhotoUri()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SimulationOptionItem(
                icon = Icons.Default.PhotoCamera,
                iconColor = Color(0xFF0F766E),
                title = "Photo via EXTRA_PICTURE Fallback",
                description = "Tests expanded notification preview bitmap fallback when messaging app hides URIs",
                testTag = "btn_sim_photo_fallback",
                onClick = {
                    onSimulatePhotoFallback()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SimulationOptionItem(
                icon = Icons.Default.Videocam,
                iconColor = Color(0xFF0284C7),
                title = "Video Attachment (MessagingStyle)",
                description = "Streams video/mp4 payload from contentUri to /Videos/VID_timestamp.mp4",
                testTag = "btn_sim_video",
                onClick = {
                    onSimulateVideo()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SimulationOptionItem(
                icon = Icons.Default.Audiotrack,
                iconColor = Color(0xFFF59E0B),
                title = "Voice Note (MessagingStyle audio/m4a)",
                description = "Streams voice note to /VoiceNotes/AUD_timestamp.m4a with in-app audio player",
                testTag = "btn_sim_voice",
                onClick = {
                    onSimulateVoiceNote()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun SimulationOptionItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
