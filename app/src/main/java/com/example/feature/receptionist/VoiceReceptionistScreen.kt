package com.example.feature.receptionist

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.database.GreetingScriptEntity
import com.example.domain.model.GreetingLengthMode
import com.example.domain.model.GreetingTarget
import com.example.domain.model.ReceptionistSessionState
import com.example.domain.model.ReceptionistStage
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceShieldSettings
import com.example.feature.common.AudioWaveformVisualizer
import com.example.feature.common.SectionHeader
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonSpam
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield
import java.util.UUID

/**
 * Screen 3: Voice Receptionist — Interactive Call & Receptionist Simulator, Greeting Script Manager,
 * Multi-Language Greeting Editor, Consent Workflow, and TTS Preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceReceptionistScreen(
    settings: VoiceShieldSettings,
    scripts: List<GreetingScriptEntity>,
    sessionState: ReceptionistSessionState,
    isTtsSpeaking: Boolean,
    onUpdateSettings: ((VoiceShieldSettings) -> VoiceShieldSettings) -> Unit,
    onSaveScript: (GreetingScriptEntity) -> Unit,
    onDeleteScript: (String) -> Unit,
    onPreviewTts: (String) -> Unit,
    onStopTts: () -> Unit,
    onTriggerSimulatedCall: (String, String?, Boolean, String?) -> Unit,
    onFinishRecordingEarly: () -> Unit,
    onCancelSession: () -> Unit
) {
    var simCallerNumber by remember { mutableStateOf("+919831099887") }
    var simCallerName by remember { mutableStateOf("") }
    var simCallerConsent by remember { mutableStateOf(true) }
    var simCallerMessageHint by remember {
        mutableStateOf("নমস্কার, আমি পার্সেল ডেলিভারির জন্য ফোন করেছি। অনুগ্রহ করে গেটে আসুন।")
    }

    var editingGreetingText by remember(settings.greetingText) { mutableStateOf(settings.greetingText) }
    var editingKnownGreetingText by remember(settings.knownCallerGreetingText) { mutableStateOf(settings.knownCallerGreetingText) }
    var editingClosingText by remember(settings.closingMessageText) { mutableStateOf(settings.closingMessageText) }
    var customScriptTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("voice_receptionist_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Interactive Call & Receptionist Simulator Card
        SectionHeader(
            title = "Interactive AI Receptionist Simulator",
            subtitle = "Test screening, auto-answer delay, local TTS greeting, consent, live mic recording & AES-GCM storage"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = if (sessionState.isActive) EmeraldShield else CyanTelemetry.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                )
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sessionState.stage.stepTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (sessionState.isActive) EmeraldShield else CyanTelemetry,
                            fontWeight = FontWeight.Bold
                        )
                        if (sessionState.isActive) {
                            Text(
                                text = "Recording: ${sessionState.elapsedSeconds}s / ${settings.maxRecordingDuration}s • Silence: ${sessionState.silenceSeconds}s / ${settings.silenceTimeout}s",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (sessionState.isActive || sessionState.stage == ReceptionistStage.RECORDING_CALLER_AUDIO) {
                    AudioWaveformVisualizer(
                        isActive = sessionState.isActive,
                        amplitude = sessionState.currentAmplitude.coerceAtLeast(0.4f),
                        barColor = if (sessionState.stage == ReceptionistStage.RECORDING_CALLER_AUDIO) CrimsonSpam else EmeraldShield
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = simCallerNumber,
                        onValueChange = { simCallerNumber = it },
                        label = { Text("Simulated Caller Number") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_caller_number_input")
                    )
                    OutlinedTextField(
                        value = simCallerName,
                        onValueChange = { simCallerName = it },
                        label = { Text("Contact Name (Optional)") },
                        placeholder = { Text("Blank = Unknown") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = simCallerMessageHint,
                    onValueChange = { simCallerMessageHint = it },
                    label = { Text("Simulated Caller Message Content (for Offline STT)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Simulate Caller Recording Consent Granted",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = simCallerConsent,
                        onCheckedChange = { simCallerConsent = it },
                        modifier = Modifier.testTag("sim_consent_switch")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!sessionState.isActive) {
                        Button(
                            onClick = {
                                onTriggerSimulatedCall(
                                    simCallerNumber,
                                    simCallerName.ifBlank { null },
                                    simCallerConsent,
                                    simCallerMessageHint
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_simulated_call_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PhoneCallback, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Simulate Incoming Call")
                        }
                    } else {
                        if (sessionState.stage == ReceptionistStage.RECORDING_CALLER_AUDIO) {
                            Button(
                                onClick = onFinishRecordingEarly,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Finish Recording & Save")
                            }
                        }
                        Button(
                            onClick = onCancelSession,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonSpam),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Abort Session")
                        }
                    }
                }

                if (sessionState.statusLog.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sessionState.statusLog.takeLast(6).forEach { logLine ->
                                Text(
                                    text = "• $logLine",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Greeting & Conversation Script Editor
        SectionHeader(
            title = "Greeting & Conversation Scripts",
            subtitle = "Customize Bengali, English, and Hindi scripts with mandatory recording disclosure"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Active Language", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onUpdateSettings { it.copy(language = lang) } },
                            label = { Text("${lang.displayName} (${lang.code})") },
                            modifier = Modifier.testTag("lang_chip_${lang.code}")
                        )
                    }
                }

                Text("Greeting Length Mode", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreetingLengthMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.greetingLengthMode == mode,
                            onClick = { onUpdateSettings { it.copy(greetingLengthMode = mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = editingGreetingText,
                    onValueChange = { editingGreetingText = it },
                    label = { Text("Unknown Caller Greeting Script (Editable)") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("greeting_script_input")
                )

                OutlinedTextField(
                    value = editingKnownGreetingText,
                    onValueChange = { editingKnownGreetingText = it },
                    label = { Text("Known Contact Busy Greeting Script") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editingClosingText,
                    onValueChange = { editingClosingText = it },
                    label = { Text("Optional Closing Message (Played after recording)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mandatory Recording Disclosure", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Never start recording secretly — always inform the caller first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.recordingDisclosureEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(recordingDisclosureEnabled = v) } }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require Caller Consent Before Recording", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Verifies caller consent before capturing audio frames",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.callerConsentRequired,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(callerConsentRequired = v) } }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onUpdateSettings {
                                it.copy(
                                    greetingText = editingGreetingText,
                                    knownCallerGreetingText = editingKnownGreetingText,
                                    closingMessageText = editingClosingText
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_greeting_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save Active Script")
                    }

                    OutlinedButton(
                        onClick = {
                            if (isTtsSpeaking) onStopTts() else onPreviewTts(editingGreetingText)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_greeting_tts_button")
                    ) {
                        Icon(
                            imageVector = if (isTtsSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isTtsSpeaking) "Stop Preview" else "Preview TTS")
                    }
                }
            }
        }

        // 3. Preset & Saved Greeting Script Library
        SectionHeader(
            title = "Multi-Language Script Presets",
            subtitle = "Tap any preset to load it into the active receptionist or save a new template"
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = customScriptTitle,
                onValueChange = { customScriptTitle = it },
                label = { Text("Save Current Script As Preset Title") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val title = customScriptTitle.ifBlank { "Custom ${settings.language.displayName} Script" }
                    onSaveScript(
                        GreetingScriptEntity(
                            id = "custom_${UUID.randomUUID().toString().take(8)}",
                            title = title,
                            languageCode = settings.language.code,
                            scriptText = editingGreetingText,
                            closingText = editingClosingText,
                            targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                            lengthMode = settings.greetingLengthMode.name,
                            includesRecordingDisclosure = settings.recordingDisclosureEnabled,
                            requiresExplicitConsent = settings.callerConsentRequired,
                            isSelectedDefault = true
                        )
                    )
                    customScriptTitle = ""
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Save Preset")
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        scripts.forEach { script ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(script.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${script.languageCode} • ${script.targetAudience} • ${script.lengthMode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanTelemetry
                            )
                        }
                        Row {
                            OutlinedButton(
                                onClick = {
                                    editingGreetingText = script.scriptText
                                    editingClosingText = script.closingText
                                    onSaveScript(script.copy(isSelectedDefault = true))
                                }
                            ) {
                                Text("Use Script")
                            }
                            if (script.id.startsWith("custom_")) {
                                IconButton(onClick = { onDeleteScript(script.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonSpam)
                                }
                            }
                        }
                    }
                    Text(
                        text = script.scriptText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
