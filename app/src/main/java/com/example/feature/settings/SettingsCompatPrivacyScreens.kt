package com.example.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeviceAudioCapabilities
import com.example.domain.model.FallbackCallAction
import com.example.domain.model.UnknownNumberAction
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceMood
import com.example.domain.model.VoiceShieldSettings
import com.example.feature.common.CapabilityStatusBadge
import com.example.feature.common.SectionHeader
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonSpam
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield

/**
 * Screen 10: Complete Settings System — Configures and persists all 24+ required variables.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: VoiceShieldSettings,
    onUpdateSettings: ((VoiceShieldSettings) -> VoiceShieldSettings) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Complete Receptionist & Screening Settings",
            subtitle = "All 24+ configurable variables persisted locally via Jetpack DataStore",
            trailingContent = {
                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.testTag("reset_settings_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        )

        // Group 1: Call Answering & Timing (autoAnswerEnabled, answerDelaySeconds, maxRecordingDuration, silenceTimeout, maxCallDuration, repeatGreetingCount)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("1. Call Answering & Timing Limits", style = MaterialTheme.typography.titleMedium, color = EmeraldShield)

                SettingToggleRow(
                    title = "autoAnswerEnabled",
                    subtitle = "Automatically answer unknown calls routed to the AI receptionist",
                    checked = settings.autoAnswerEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoAnswerEnabled = v) } }
                )
                SettingSliderRow(
                    title = "answerDelaySeconds",
                    valueLabel = "${settings.answerDelaySeconds}s",
                    value = settings.answerDelaySeconds.toFloat(),
                    range = 0f..15f,
                    onValueChange = { v -> onUpdateSettings { it.copy(answerDelaySeconds = v.toInt()) } }
                )
                SettingSliderRow(
                    title = "maxRecordingDuration",
                    valueLabel = "${settings.maxRecordingDuration}s",
                    value = settings.maxRecordingDuration.toFloat(),
                    range = 10f..180f,
                    onValueChange = { v -> onUpdateSettings { it.copy(maxRecordingDuration = v.toInt()) } }
                )
                SettingSliderRow(
                    title = "silenceTimeout",
                    valueLabel = "${settings.silenceTimeout}s",
                    value = settings.silenceTimeout.toFloat(),
                    range = 2f..20f,
                    onValueChange = { v -> onUpdateSettings { it.copy(silenceTimeout = v.toInt()) } }
                )
                SettingSliderRow(
                    title = "maxCallDuration",
                    valueLabel = "${settings.maxCallDuration}s",
                    value = settings.maxCallDuration.toFloat(),
                    range = 20f..300f,
                    onValueChange = { v -> onUpdateSettings { it.copy(maxCallDuration = v.toInt()) } }
                )
                SettingSliderRow(
                    title = "repeatGreetingCount",
                    valueLabel = "${settings.repeatGreetingCount}x",
                    value = settings.repeatGreetingCount.toFloat(),
                    range = 1f..3f,
                    onValueChange = { v -> onUpdateSettings { it.copy(repeatGreetingCount = v.toInt()) } }
                )
            }
        }

        // Group 2: Greeting, Consent & Voice Synthesis (greetingEnabled, recordingDisclosureEnabled, callerConsentRequired, language, voiceMood, speechRate, speechPitch, speechVolume)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("2. Greeting, Consent & Voice Synthesis", style = MaterialTheme.typography.titleMedium, color = CyanTelemetry)

                SettingToggleRow(
                    title = "greetingEnabled",
                    subtitle = "Play local TTS greeting when the receptionist answers",
                    checked = settings.greetingEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(greetingEnabled = v) } }
                )
                SettingToggleRow(
                    title = "recordingDisclosureEnabled",
                    subtitle = "Include mandatory call recording disclosure in greeting",
                    checked = settings.recordingDisclosureEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(recordingDisclosureEnabled = v) } }
                )
                SettingToggleRow(
                    title = "callerConsentRequired",
                    subtitle = "Require caller consent before recording voice message",
                    checked = settings.callerConsentRequired,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(callerConsentRequired = v) } }
                )

                Text("language (${settings.language.displayName})", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onUpdateSettings { it.copy(language = lang) } },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Text("voiceMood (${settings.voiceMood.displayName})", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceMood.entries.forEach { mood ->
                        FilterChip(
                            selected = settings.voiceMood == mood,
                            onClick = { onUpdateSettings { it.copy(voiceMood = mood) } },
                            label = { Text(mood.displayName) }
                        )
                    }
                }

                SettingSliderRow(
                    title = "speechRate",
                    valueLabel = "${"%.2f".format(settings.speechRate)}x",
                    value = settings.speechRate,
                    range = 0.5f..2.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(speechRate = v) } }
                )
                SettingSliderRow(
                    title = "speechPitch",
                    valueLabel = "${"%.2f".format(settings.speechPitch)}x",
                    value = settings.speechPitch,
                    range = 0.5f..2.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(speechPitch = v) } }
                )
                SettingSliderRow(
                    title = "speechVolume",
                    valueLabel = "${(settings.speechVolume * 100).toInt()}%",
                    value = settings.speechVolume,
                    range = 0.1f..1.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(speechVolume = v) } }
                )
            }
        }

        // Group 3: Screening, Retention, Schedule & Theme (knownContactsBypass, unknownNumberAction, spamThreshold, notificationEnabled, messageAutoDeleteDays, workingHoursEnabled, fallbackCallAction, maximumStoredMessages, darkThemeEnabled)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("3. Screening Policy, Retention & Schedule", style = MaterialTheme.typography.titleMedium, color = AmberWarning)

                SettingToggleRow(
                    title = "knownContactsBypass",
                    subtitle = "Allow saved contacts to bypass AI receptionist",
                    checked = settings.knownContactsBypass,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(knownContactsBypass = v) } }
                )
                SettingToggleRow(
                    title = "notificationEnabled",
                    subtitle = "Send 'New Voice Message' notifications when a recording is saved",
                    checked = settings.notificationEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(notificationEnabled = v) } }
                )
                SettingToggleRow(
                    title = "workingHoursEnabled",
                    subtitle = "Scheduled receptionist availability (${settings.workingHoursStartHour}:00 - ${settings.workingHoursEndHour}:00)",
                    checked = settings.workingHoursEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(workingHoursEnabled = v) } }
                )
                SettingToggleRow(
                    title = "darkThemeEnabled",
                    subtitle = "Use Cyber-Security Shield Obsidian Navy dark theme",
                    checked = settings.darkThemeEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(darkThemeEnabled = v) } }
                )

                SettingSliderRow(
                    title = "spamThreshold",
                    valueLabel = "${settings.spamThreshold} / 100",
                    value = settings.spamThreshold.toFloat(),
                    range = 20f..95f,
                    onValueChange = { v -> onUpdateSettings { it.copy(spamThreshold = v.toInt()) } }
                )
                SettingSliderRow(
                    title = "messageAutoDeleteDays",
                    valueLabel = "${settings.messageAutoDeleteDays} days",
                    value = settings.messageAutoDeleteDays.toFloat(),
                    range = 1f..180f,
                    onValueChange = { v -> onUpdateSettings { it.copy(messageAutoDeleteDays = v.toInt()) } }
                )
                SettingSliderRow(
                    title = "maximumStoredMessages",
                    valueLabel = "${settings.maximumStoredMessages} msgs",
                    value = settings.maximumStoredMessages.toFloat(),
                    range = 10f..500f,
                    onValueChange = { v -> onUpdateSettings { it.copy(maximumStoredMessages = v.toInt()) } }
                )

                Text("unknownNumberAction (${settings.unknownNumberAction.displayName})", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnknownNumberAction.entries.forEach { act ->
                        FilterChip(
                            selected = settings.unknownNumberAction == act,
                            onClick = { onUpdateSettings { it.copy(unknownNumberAction = act) } },
                            label = { Text(act.displayName) }
                        )
                    }
                }

                Text("fallbackCallAction (${settings.fallbackCallAction.displayName})", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FallbackCallAction.entries.forEach { fb ->
                        FilterChip(
                            selected = settings.fallbackCallAction == fb,
                            onClick = { onUpdateSettings { it.copy(fallbackCallAction = fb) } },
                            label = { Text(fb.displayName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("setting_switch_$title")
        )
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = EmeraldShield)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

/**
 * Screen 11: Permissions and Device Compatibility — Displays all 5 required capabilities separately,
 * provides runtime permission launchers, and documents the SIP/VoIP PBX architecture.
 */
@Composable
fun PermissionsAndCompatibilityScreen(
    capabilities: DeviceAudioCapabilities,
    sipArchitectureText: String,
    onRefreshCapabilities: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshCapabilities()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("permissions_compatibility_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "5-Point Call & Audio Capability Matrix",
            subtitle = "Evaluated by CallAudioCapabilityManager using documented Android APIs only"
        )

        Button(
            onClick = {
                val perms = buildList {
                    add(Manifest.permission.READ_CONTACTS)
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.READ_PHONE_STATE)
                    add(Manifest.permission.ANSWER_PHONE_CALLS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                permissionLauncher.launch(perms.toTypedArray())
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("grant_runtime_permissions_btn")
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Grant / Refresh Runtime Permissions")
        }

        val items = listOf(
            Triple("1. Call Screening Supported", capabilities.callScreeningSupported, capabilities.callScreeningDetail),
            Triple("2. Call Answering / Disconnecting Supported", capabilities.callAnswerDisconnectSupported, capabilities.callAnswerDisconnectDetail),
            Triple("3. Local Offline TTS Generation Supported", capabilities.localTtsGenerationSupported, capabilities.localTtsGenerationDetail),
            Triple("4. TTS Transmission to Remote Cellular Caller", capabilities.cellularTtsUplinkSupported, capabilities.cellularTtsUplinkDetail),
            Triple("5. Remote Cellular Caller Audio Recording", capabilities.cellularRemoteRecordingSupported, capabilities.cellularRemoteRecordingDetail)
        )

        items.forEach { (title, status, detail) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        CapabilityStatusBadge(status = status)
                    }
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SectionHeader(
            title = "Optional SIP/VoIP & Self-Hosted PBX Bridge",
            subtitle = "How to achieve 100% two-way server/VoIP audio when cellular baseband is locked"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyanTelemetry.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
        ) {
            Text(
                text = sipArchitectureText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Screen 12: Privacy and Data Management — Android Keystore AES-256-GCM encryption verification,
 * retention controls, zero-cloud guarantee, and instant data wipe actions.
 */
@Composable
fun PrivacyAndDataScreen(
    settings: VoiceShieldSettings,
    totalMessagesCount: Int,
    totalScreenedCallsCount: Int,
    onRunRetentionCleanup: () -> Unit,
    onDeleteAllRecordings: () -> Unit,
    onClearCallHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("privacy_data_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Privacy & Security Architecture",
            subtitle = "100% Offline-First • Hardware-backed Keystore encryption • Zero cloud telemetry"
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EmeraldShield.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldShield)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Android Keystore AES-256-GCM Encryption Active",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldShield
                    )
                }
                Text(
                    "• Voice recordings are encrypted at rest with a 256-bit AES-GCM key bound to AndroidKeyStore (VoiceShieldRecordingMasterKey_v1).\n" +
                        "• No cloud TTS, STT, or analytics servers are contacted.\n" +
                        "• Recording disclosure and caller consent status are stored alongside every message.\n" +
                        "• Secure file deletion overwrites encrypted bytes with zeros before unlinking.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Data Retention & Wipe Controls", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stored Encrypted Messages: $totalMessagesCount (Max limit: ${settings.maximumStoredMessages})\n" +
                        "Auto-Delete Retention Policy: ${settings.messageAutoDeleteDays} days\n" +
                        "Screened Call History Entries: $totalScreenedCallsCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onRunRetentionCleanup,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanTelemetry),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run Expired Recording Cleanup Now")
                }

                OutlinedButton(
                    onClick = onClearCallHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wipe Screened Call Log ($totalScreenedCallsCount entries)")
                }

                Button(
                    onClick = onDeleteAllRecordings,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_wipe_all_recordings_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Zero-Wipe & Delete All Voice Recordings")
                }
            }
        }
    }
}
