package com.example.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.domain.model.StorageInspectionInfo
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
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

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

        // Default Phone App Role (Kept as an option in settings per request)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Default Phone App (Dialer Option)", style = MaterialTheme.typography.titleMedium, color = EmeraldShield)
                Text(
                    text = "Keep VoiceShield as your Default Phone App if you want incoming cellular calls to be answered automatically by InCallService after the configured ring delay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            roleLauncher.launch(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                    modifier = Modifier.testTag("request_default_dialer_setting_btn")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Set as Default Phone App (Optional)")
                }
            }
        }

        // Group 1: Call Answering & Timing (applyToAllCalls, autoAnswerEnabled, answerDelaySeconds, maxRecordingDuration, silenceTimeout, maxCallDuration, repeatGreetingCount)
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
                    title = "applyToAllCalls (Universal Protection)",
                    subtitle = "When Shield is ON, automatically answer all incoming calls with AI Receptionist",
                    checked = settings.applyToAllCalls,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(applyToAllCalls = v) } }
                )
                SettingToggleRow(
                    title = "autoAnswerEnabled",
                    subtitle = "Automatically answer calls routed to the AI receptionist",
                    checked = settings.autoAnswerEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoAnswerEnabled = v) } }
                )
                SettingSliderRow(
                    title = "answerDelaySeconds (Dynamic Slider)",
                    valueLabel = "${settings.answerDelaySeconds}s",
                    value = settings.answerDelaySeconds.toFloat(),
                    range = 1f..30f,
                    onValueChange = { v -> onUpdateSettings { it.copy(answerDelaySeconds = v.toInt().coerceIn(1, 30)) } }
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
/**
 * Screen 12: Privacy & Data Management — Physical storage location inspection,
 * Android Keystore AES-256-GCM encryption verification, retention controls, and zero-trust complete data wipe.
 */
@Composable
fun PrivacyAndDataScreen(
    settings: VoiceShieldSettings,
    storageInfo: StorageInspectionInfo,
    onRunRetentionCleanup: () -> Unit,
    onDeleteAllRecordings: () -> Unit,
    onClearCallHistory: () -> Unit,
    onWipeAllUserData: () -> Unit
) {
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CrimsonSpam
                )
            },
            title = {
                Text(
                    text = "সব ডেটা মুছে ফেলতে চান? (Wipe All Data?)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "এটি সমস্ত এনক্রিপ্ট করা ভয়েস রেকর্ডিং, কল স্ক্রীনিং লগ, কাস্টম কলার রুলস স্থায়ীভাবে মুছে দেবে এবং অ্যাপ্লিকেশন সেটিংস ফ্যাক্টরি ডিফল্টে রিসেট করবে। এটি অপরিবর্তনীয়।\n\nThis permanently erases all audio recordings from disk, clears all database tables, and resets settings to default.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmDialog = false
                        onWipeAllUserData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam),
                    modifier = Modifier.testTag("confirm_wipe_all_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("হ্যাঁ, সব মুছুন (Yes, Wipe All)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_wipe_all_btn")
                ) {
                    Text("বাতিল (Cancel)")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("privacy_data_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "ডেটা স্টোরেজ ও সম্পূর্ণ মোছার ব্যবস্থা (Data Storage & Erasure)",
            subtitle = "100% Offline-First • Device Local Storage • Zero Cloud Telemetry"
        )

        // 1. Storage Location Inspection Card (data kothay thake)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyanTelemetry.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                .testTag("storage_inspection_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = CyanTelemetry)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ডেটা কোথায় থাকে (Storage Locations)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanTelemetry
                        )
                    }
                    Text(
                        text = "মোট: ${storageInfo.formattedTotalStorageSize}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldShield
                    )
                }

                Text(
                    text = "আপনার সমস্ত তথ্য শুধুমাত্র আপনার ডিভাইসের ইন্টারনাল মেমরিতে সুরক্ষিত থাকে। কোনো ক্লাউড সার্ভারে কোনো ডেটা পাঠানো হয় না।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Item A: Voice Recordings Directory
                StorageLocationItem(
                    label = "১. ভয়েস মেসেজ অডিও ফাইল (Voice Recordings Directory)",
                    path = storageInfo.voiceMessagesDirPath,
                    details = "${storageInfo.voiceMessagesFileCount} টি ফাইল • সাইজ: ${storageInfo.formattedVoiceMessagesSize} • AES-256-GCM এনক্রিপ্টেড (.wav.enc)",
                    tint = EmeraldShield
                )

                // Item B: SQLite Database
                StorageLocationItem(
                    label = "২. রুম ডাটাবেস (Room SQLite Database)",
                    path = storageInfo.databasePath,
                    details = "সাইজ: ${storageInfo.formattedDbSize} • ${storageInfo.totalRecordedMessagesCount} টি মেসেজ, ${storageInfo.totalScreenedCallsCount} টি কল হিস্ট্রি, ${storageInfo.totalCallerRulesCount} টি কলার রুলস",
                    tint = CyanTelemetry
                )

                // Item C: Settings DataStore
                StorageLocationItem(
                    label = "৩. সেটিংস ফাইল (Preferences DataStore)",
                    path = storageInfo.settingsDataStorePath,
                    details = "সাইজ: ${storageInfo.formattedDataStoreSize} • অটো-অ্যানসার টাইমার, ভয়েস মোড ও SIP কনফিগারেশন",
                    tint = AmberWarning
                )

                // Item D: AI Voice Models
                StorageLocationItem(
                    label = "৪. অফলাইন এআই ভয়েস মডেল (Sherpa-ONNX Models)",
                    path = storageInfo.voiceModelsDirPath,
                    details = "${storageInfo.voiceModelsFileCount} টি মডেল ফাইল • সাইজ: ${storageInfo.formattedVoiceModelsSize} • বাংলা, হিন্দি ও ইংরেজি ভয়েস",
                    tint = CyanTelemetry
                )

                // Item E: Android Keystore Master Key
                StorageLocationItem(
                    label = "৫. হার্ডওয়্যার এনক্রিপশন কি (Android KeyStore)",
                    path = "AndroidKeyStore://${storageInfo.keystoreKeyAlias}",
                    details = "অ্যালগরিদম: ${storageInfo.encryptionAlgorithm} • রুট ছাড়া ডিভাইস থেকেও অডিও অ্যাক্সেস অসম্ভব",
                    tint = EmeraldShield
                )
            }
        }

        // 2. Destructive Complete Wipe Action (moch sob data)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CrimsonSpam.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonSpam)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "সব ডেটা মুছুন (Complete Data Wipe)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonSpam
                    )
                }

                Text(
                    text = "এক ক্লিকে সমস্ত অডিও ফাইল মেমরি থেকে নিশ্চিহ্ন করুন, ডাটাবেস টেবিল খালি করুন এবং সেটিংস ফ্যাক্টরি ডিফল্টে ফিরিয়ে আনুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showWipeConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wipe_all_data_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("সব ডেটা মুছুন (Wipe All Data Now)", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Individual Fine-Grained Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("পৃথকভাবে ডেটা মুছে ফেলার বিকল্প (Individual Controls)", style = MaterialTheme.typography.titleMedium)

                OutlinedButton(
                    onClick = onRunRetentionCleanup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("মেয়াদোত্তীর্ণ রেকর্ডিং মুছুন (${settings.messageAutoDeleteDays} দিনের পুরনো)")
                }

                OutlinedButton(
                    onClick = onClearCallHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("কল হিস্ট্রি মুছুন (${storageInfo.totalScreenedCallsCount} টি এন্ট্রি)")
                }

                Button(
                    onClick = onDeleteAllRecordings,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_wipe_all_recordings_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("শুধু ভয়েস রেকর্ডিং মুছুন (${storageInfo.totalRecordedMessagesCount} টি মেসেজ)")
                }
            }
        }
    }
}

@Composable
private fun StorageLocationItem(
    label: String,
    path: String,
    details: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
