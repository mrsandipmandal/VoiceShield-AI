package com.example.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.audio.PlaybackUiState
import com.example.core.database.VoiceMessageEntity
import com.example.domain.model.RecordingStatus
import com.example.feature.common.SectionHeader
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonSpam
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageFilterTab(val label: String) {
    ALL("All"),
    ENCRYPTED_AUDIO("Recorded"),
    IMPORTANT("Important"),
    UNREAD("Unread"),
    UNSUPPORTED_FALLBACK("Unsupported Audio")
}

/**
 * Screen 5: Voice Message Inbox — Encrypted audio playback, speed controls (0.75x - 2.0x),
 * progress scrubbing, search & filtering, mark as important, offline STT transcription,
 * explicit user audio export, delete with confirmation, and transparent status when
 * cellular remote audio was not captured.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceMessageInboxScreen(
    messages: List<VoiceMessageEntity>,
    playbackState: PlaybackUiState,
    highlightedMessageId: String?,
    pendingDeleteConfirmMessageId: String?,
    onPlayOrPause: (VoiceMessageEntity) -> Unit,
    onSeek: (VoiceMessageEntity, Float) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onToggleImportant: (VoiceMessageEntity) -> Unit,
    onTranscribeOffline: (VoiceMessageEntity) -> Unit,
    onExportAudio: (VoiceMessageEntity) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteAllMessages: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MessageFilterTab.ALL) }
    var messageIdToConfirmDelete by remember(pendingDeleteConfirmMessageId) {
        mutableStateOf(pendingDeleteConfirmMessageId)
    }
    var confirmDeleteAllDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }

    val filteredMessages = remember(messages, searchQuery, selectedFilter) {
        messages.filter { msg ->
            val matchesQuery = searchQuery.isBlank() ||
                msg.callerNumber.contains(searchQuery, ignoreCase = true) ||
                (msg.contactName?.contains(searchQuery, ignoreCase = true) == true) ||
                (msg.transcription?.contains(searchQuery, ignoreCase = true) == true)

            val matchesTab = when (selectedFilter) {
                MessageFilterTab.ALL -> true
                MessageFilterTab.ENCRYPTED_AUDIO -> msg.recordingStatus == RecordingStatus.RECORDED_ENCRYPTED.name
                MessageFilterTab.IMPORTANT -> msg.isImportant
                MessageFilterTab.UNREAD -> !msg.isRead
                MessageFilterTab.UNSUPPORTED_FALLBACK -> msg.recordingStatus != RecordingStatus.RECORDED_ENCRYPTED.name
            }
            matchesQuery && matchesTab
        }
    }

    if (messageIdToConfirmDelete != null) {
        val targetId = messageIdToConfirmDelete!!
        AlertDialog(
            onDismissRequest = {
                messageIdToConfirmDelete = null
                onDismissDeleteConfirmation()
            },
            title = { Text("Delete Encrypted Recording?") },
            text = {
                Text("This will zero-wipe the encrypted .wav.enc file on disk and permanently remove the message record.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMessage(targetId)
                        messageIdToConfirmDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam),
                    modifier = Modifier.testTag("confirm_delete_message_btn")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        messageIdToConfirmDelete = null
                        onDismissDeleteConfirmation()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllDialog = false },
            title = { Text("Delete All Voice Messages?") },
            text = {
                Text("All encrypted voice recordings and call message logs will be securely overwritten and deleted from this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllMessages()
                        confirmDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("voice_message_inbox_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Encrypted Voice Message Inbox",
            subtitle = "AES-256-GCM at rest • Zero cloud uploads • Full playback & export control",
            trailingContent = {
                if (messages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { confirmDeleteAllDialog = true },
                        modifier = Modifier.testTag("delete_all_messages_btn")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CrimsonSpam)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete All", color = CrimsonSpam)
                    }
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search number, contact, or transcript") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("message_search_input")
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MessageFilterTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("filter_tab_${tab.name}")
                )
            }
        }

        // Playback Speed Selector Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Playback Speed:", style = MaterialTheme.typography.labelLarge, color = CyanTelemetry)
            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                FilterChip(
                    selected = playbackState.playbackSpeed == spd,
                    onClick = { onChangeSpeed(spd) },
                    label = { Text("${spd}x") }
                )
            }
        }

        if (filteredMessages.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No voice messages match the current filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    val isThisPlaying = playbackState.playingMessageId == msg.id && playbackState.isPlaying
                    val hasEncryptedAudio = msg.recordingStatus == RecordingStatus.RECORDED_ENCRYPTED.name &&
                        !msg.encryptedFilePath.isNullOrBlank()
                    val isHighlighted = msg.id == highlightedMessageId

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isHighlighted || isThisPlaying) 1.5.dp else 1.dp,
                                color = when {
                                    isThisPlaying -> EmeraldShield
                                    isHighlighted -> CyanTelemetry
                                    !hasEncryptedAudio -> AmberWarning.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                },
                                shape = MaterialTheme.shapes.medium
                            )
                            .testTag("voice_message_card_${msg.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!msg.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(EmeraldShield)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "NEW",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = msg.contactName ?: msg.callerNumber,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${msg.callerNumber} • ${dateFormatter.format(Date(msg.callDateMillis))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onToggleImportant(msg) }) {
                                        Icon(
                                            imageVector = if (msg.isImportant) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Mark Important",
                                            tint = if (msg.isImportant) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { messageIdToConfirmDelete = msg.id },
                                        modifier = Modifier.testTag("delete_msg_btn_${msg.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Recording",
                                            tint = CrimsonSpam
                                        )
                                    }
                                }
                            }

                            // Detailed metadata pills
                            Text(
                                text = "Call: ${msg.callDurationSeconds}s • Msg: ${msg.messageDurationSeconds}s • Spam: ${msg.spamClassification} (${msg.spamScore}) • Consent: ${msg.consentStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanTelemetry
                            )

                            if (hasEncryptedAudio) {
                                com.example.feature.common.AudioWaveformVisualizer(
                                    isActive = isThisPlaying,
                                    amplitude = if (isThisPlaying) 0.78f else 0.25f,
                                    barColor = if (isThisPlaying) EmeraldShield else CyanTelemetry
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { onPlayOrPause(msg) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isThisPlaying) AmberWarning else EmeraldShield
                                        ),
                                        modifier = Modifier.testTag("play_pause_btn_${msg.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isThisPlaying) "Pause" else "Play"
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isThisPlaying) "Pause" else "Play")
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        val progress = if (playbackState.playingMessageId == msg.id) {
                                            playbackState.progressFraction
                                        } else {
                                            0f
                                        }
                                        Slider(
                                            value = progress,
                                            onValueChange = { fraction -> onSeek(msg, fraction) },
                                            valueRange = 0f..1f
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val pos = if (playbackState.playingMessageId == msg.id) playbackState.currentPositionSec else 0
                                            Text(
                                                "${pos}s / ${msg.messageDurationSeconds}s (${playbackState.playbackSpeed}x)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = EmeraldShield,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "AES-256-GCM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldShield
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!msg.transcription.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = msg.transcription,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { onTranscribeOffline(msg) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Offline Transcribe")
                                    }
                                    OutlinedButton(
                                        onClick = { onExportAudio(msg) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export WAV")
                                    }
                                }
                            } else {
                                // Honest Unsupported Cellular Audio Status Banner (never creates a fake/empty recording)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            AmberWarning.copy(alpha = 0.14f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = AmberWarning
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "No Audio Captured (${msg.recordingStatus})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = AmberWarning,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = msg.audioCapabilityStatus,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
