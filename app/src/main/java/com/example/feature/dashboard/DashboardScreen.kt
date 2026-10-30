package com.example.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.database.ScreenedCallEntity
import com.example.core.database.VoiceMessageEntity
import com.example.core.database.VoiceModelEntity
import com.example.domain.model.CallHandlingStatus
import com.example.domain.model.DeviceAudioCapabilities
import com.example.domain.model.RecordingStatus
import com.example.domain.model.SpamClassification
import com.example.domain.model.VoiceShieldSettings
import com.example.feature.common.CapabilityStatusBadge
import com.example.feature.common.MetricStatCard
import com.example.feature.common.SectionHeader
import com.example.feature.common.VoiceShieldScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonSpam
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    settings: VoiceShieldSettings,
    screenedCalls: List<ScreenedCallEntity>,
    messages: List<VoiceMessageEntity>,
    voiceModels: List<VoiceModelEntity>,
    capabilities: DeviceAudioCapabilities,
    onToggleProtection: (Boolean) -> Unit,
    onNavigate: (VoiceShieldScreen) -> Unit,
    onQuickSimulateCall: () -> Unit,
    onPlayMessage: (VoiceMessageEntity) -> Unit
) {
    val totalScreened = screenedCalls.size
    val spamBlocked = screenedCalls.count {
        it.handlingStatus == CallHandlingStatus.SCREENED_REJECTED.name ||
            it.spamClassification == SpamClassification.HIGH_RISK_SPAM.name ||
            it.spamClassification == SpamClassification.BLOCKLISTED.name
    }
    val unknownHandled = screenedCalls.count {
        it.handlingStatus == CallHandlingStatus.RECEPTIONIST_COMPLETED.name ||
            it.handlingStatus == CallHandlingStatus.RECEPTIONIST_FALLBACK.name ||
            it.handlingStatus == CallHandlingStatus.SCREENED_SILENCED.name
    }
    val savedVoiceMessagesCount = messages.count {
        it.recordingStatus == RecordingStatus.RECORDED_ENCRYPTED.name
    }
    val latestMessage = messages.firstOrNull()
    val activeModel = voiceModels.firstOrNull { it.id == settings.voiceModel }
        ?: voiceModels.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Protection Shield Banner
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = if (settings.protectionEnabled) EmeraldShield.copy(alpha = 0.6f) else CrimsonSpam.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                if (settings.protectionEnabled) EmeraldShield.copy(alpha = 0.18f) else CrimsonSpam.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (settings.protectionEnabled) EmeraldShield.copy(alpha = 0.22f)
                                        else CrimsonSpam.copy(alpha = 0.22f)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Protection Shield",
                                    tint = if (settings.protectionEnabled) EmeraldShield else CrimsonSpam,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (settings.protectionEnabled) "SHIELD ACTIVE • OFFLINE AI" else "PROTECTION PAUSED",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (settings.protectionEnabled) EmeraldShield else CrimsonSpam
                                )
                                Text(
                                    text = "VoiceShield AI Receptionist",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Switch(
                            checked = settings.protectionEnabled,
                            onCheckedChange = onToggleProtection,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = EmeraldShield
                            ),
                            modifier = Modifier.testTag("protection_master_switch")
                        )
                    }

                    Text(
                        text = "100% On-Device Call Screening • ${settings.language.displayName} • Mood: ${settings.voiceMood.displayName} • Policy: ${settings.unknownNumberAction.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onQuickSimulateCall,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_simulate_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneInTalk,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test AI Receptionist")
                        }
                        OutlinedButton(
                            onClick = { onNavigate(VoiceShieldScreen.TTS_VOICE_STUDIO) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_voice_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Voice Studio")
                        }
                    }
                }
            }
        }

        // 2. 4 Core Telemetry Counters
        SectionHeader(
            title = "Live Call Telemetry",
            subtitle = "Real-time on-device screening and message metrics"
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricStatCard(
                title = "Total Screened",
                value = totalScreened.toString(),
                subtitle = "Incoming calls analyzed",
                icon = Icons.Default.Call,
                accentColor = CyanTelemetry,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(VoiceShieldScreen.SPAM_CALL_HISTORY) }
            )
            MetricStatCard(
                title = "Spam Blocked",
                value = spamBlocked.toString(),
                subtitle = "Threshold >= ${settings.spamThreshold}",
                icon = Icons.Default.Block,
                accentColor = CrimsonSpam,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(VoiceShieldScreen.SPAM_CALL_HISTORY) }
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricStatCard(
                title = "Unknown Handled",
                value = unknownHandled.toString(),
                subtitle = "Receptionist & silenced",
                icon = Icons.Default.RecordVoiceOver,
                accentColor = AmberWarning,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(VoiceShieldScreen.VOICE_RECEPTIONIST) }
            )
            MetricStatCard(
                title = "Saved Messages",
                value = savedVoiceMessagesCount.toString(),
                subtitle = "AES-GCM encrypted",
                icon = Icons.Default.Voicemail,
                accentColor = EmeraldShield,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(VoiceShieldScreen.VOICE_MESSAGE_INBOX) }
            )
        }

        // 3. Last Received Voice Message Card
        SectionHeader(
            title = "Last Received Voice Message",
            subtitle = "Encrypted on-device voicemail inbox preview"
        )
        if (latestMessage != null) {
            val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                .format(Date(latestMessage.callDateMillis))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(VoiceShieldScreen.VOICE_MESSAGE_INBOX) }
                    .testTag("last_message_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = latestMessage.contactName ?: latestMessage.callerNumber,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${latestMessage.callerNumber} • $dateStr • ${latestMessage.messageDurationSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (latestMessage.recordingStatus == RecordingStatus.RECORDED_ENCRYPTED.name) {
                            Button(
                                onClick = { onPlayMessage(latestMessage) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                                modifier = Modifier.testTag("dashboard_play_latest_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Listen",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Listen")
                            }
                        }
                    }
                    if (!latestMessage.transcription.isNullOrBlank()) {
                        Text(
                            text = latestMessage.transcription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Status: ${latestMessage.audioCapabilityStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning
                        )
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No voice messages yet. Tap 'Test AI Receptionist' above to simulate an incoming call and record a voice message.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // 4. Local TTS Model Status & Device Call-Audio Compatibility Matrix
        SectionHeader(
            title = "Local TTS & Device Compatibility",
            subtitle = "Transparent hardware & Android Telecom capability check"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                .clickable { onNavigate(VoiceShieldScreen.PERMISSIONS_COMPATIBILITY) }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active TTS Engine & Model",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanTelemetry
                        )
                        Text(
                            text = activeModel?.displayName ?: "Android System Offline TTS",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Language: ${settings.language.displayName} • Mood: ${settings.voiceMood.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CapabilityStatusBadge(status = capabilities.localTtsGenerationSupported)
                }

                CapabilityRowItem("1. Call Screening Service", capabilities.callScreeningSupported)
                CapabilityRowItem("2. Call Answer & Disconnect", capabilities.callAnswerDisconnectSupported)
                CapabilityRowItem("3. Local Offline TTS Synthesis", capabilities.localTtsGenerationSupported)
                CapabilityRowItem("4. Cellular Uplink TTS Injection", capabilities.cellularTtsUplinkSupported)
                CapabilityRowItem("5. Remote Cellular Call Recording", capabilities.cellularRemoteRecordingSupported)

                Text(
                    text = "Tap to inspect detailed Android 10+ audio restrictions & SIP/VoIP PBX bridge architecture →",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldShield
                )
            }
        }

        // 5. All 12 Application Modules Directory
        SectionHeader(
            title = "All 12 VoiceShield Modules",
            subtitle = "Direct access to every protection, voice, and privacy workspace"
        )
        val moduleItems = listOf(
            Triple(VoiceShieldScreen.CALL_PROTECTION, Icons.Default.Security, EmeraldShield),
            Triple(VoiceShieldScreen.VOICE_RECEPTIONIST, Icons.Default.RecordVoiceOver, CyanTelemetry),
            Triple(VoiceShieldScreen.TTS_VOICE_STUDIO, Icons.AutoMirrored.Filled.VolumeUp, EmeraldShield),
            Triple(VoiceShieldScreen.VOICE_MESSAGE_INBOX, Icons.Default.Voicemail, CyanTelemetry),
            Triple(VoiceShieldScreen.SPAM_CALL_HISTORY, Icons.Default.History, AmberWarning),
            Triple(VoiceShieldScreen.CONTACTS_ALLOWLIST, Icons.Default.People, EmeraldShield),
            Triple(VoiceShieldScreen.BLOCKLIST, Icons.Default.Block, CrimsonSpam),
            Triple(VoiceShieldScreen.MODEL_MANAGER, Icons.Default.Memory, CyanTelemetry),
            Triple(VoiceShieldScreen.SETTINGS, Icons.Default.Tune, EmeraldShield),
            Triple(VoiceShieldScreen.PERMISSIONS_COMPATIBILITY, Icons.Default.CheckCircle, AmberWarning),
            Triple(VoiceShieldScreen.PRIVACY_DATA, Icons.Default.Lock, EmeraldShield)
        )

        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            moduleItems.forEach { (screen, icon, tint) ->
                ModuleGridTile(
                    screen = screen,
                    icon = icon,
                    tint = tint,
                    onClick = { onNavigate(screen) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun CapabilityRowItem(
    label: String,
    status: com.example.domain.model.CapabilitySupportStatus
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        CapabilityStatusBadge(status = status)
    }
}

@Composable
private fun ModuleGridTile(
    screen: VoiceShieldScreen,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.testTag("nav_tile_${screen.route}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = screen.title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = screen.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = screen.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
