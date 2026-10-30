package com.example.feature.voicestudio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Slider
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
import com.example.core.database.VoiceModelEntity
import com.example.domain.model.CapabilitySupportStatus
import com.example.domain.model.ModelInstallStatus
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceMood
import com.example.domain.model.VoiceShieldSettings
import com.example.feature.common.AudioWaveformVisualizer
import com.example.feature.common.CapabilityStatusBadge
import com.example.feature.common.SectionHeader
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonSpam
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldShield

/**
 * Screen 4: TTS Voice Studio — 12 Selectable Voice Mood Presets, Prosody Controls
 * (Speed, Pitch, Volume, Pauses, Sentence Emphasis), Prosody vs Native Emotion disclosure,
 * and Live Offline Synthesis Preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TtsVoiceStudioScreen(
    settings: VoiceShieldSettings,
    voiceModels: List<VoiceModelEntity>,
    isSpeaking: Boolean,
    onSelectMood: (VoiceMood) -> Unit,
    onUpdateSettings: ((VoiceShieldSettings) -> VoiceShieldSettings) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onStopPreview: () -> Unit
) {
    var previewSampleText by remember(settings.greetingText) { mutableStateOf(settings.greetingText) }
    val activeModel = voiceModels.firstOrNull { it.id == settings.voiceModel }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("tts_voice_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "12 Expressive Voice Mood Presets",
            subtitle = "Select a receptionist persona to configure speed, pitch, volume, pauses, and emphasis"
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceMood.entries.forEach { mood ->
                        FilterChip(
                            selected = settings.voiceMood == mood,
                            onClick = { onSelectMood(mood) },
                            label = { Text(mood.displayName) },
                            modifier = Modifier.testTag("mood_chip_${mood.name}")
                        )
                    }
                }

                Text(
                    text = "${settings.voiceMood.displayName} (${settings.voiceMood.greetingPrefixStyle}): ${settings.voiceMood.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldShield,
                    fontWeight = FontWeight.Medium
                )

                // Honest Prosody vs Emotional Synthesis disclosure required by spec
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (activeModel?.supportsNativeEmotionTokens == true) {
                            "Synthesis Mode: Native Model Emotion Tokens + Prosody DSP"
                        } else {
                            "Synthesis Mode: Prosody Approximation (VITS/Piper & Offline TTS models approximate emotional delivery via speech rate, pitch contour, volume envelope, and inter-sentence pause DSP rather than neural emotion embeddings)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Fine-Grained Prosody Sliders
        SectionHeader(
            title = "Fine-Grained Prosody & Acoustic Controls",
            subtitle = "Adjust speed, pitch, volume, sentence pauses, and emphasis"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProsodySliderRow(
                    label = "Speech Speed",
                    valueText = "${"%.2f".format(settings.speechRate)}x",
                    value = settings.speechRate,
                    range = 0.5f..1.8f,
                    onValueChange = { v -> onUpdateSettings { it.copy(speechRate = v) } }
                )
                ProsodySliderRow(
                    label = "Voice Pitch",
                    valueText = "${"%.2f".format(settings.speechPitch)}x",
                    value = settings.speechPitch,
                    range = 0.6f..1.6f,
                    onValueChange = { v -> onUpdateSettings { it.copy(speechPitch = v) } }
                )
                ProsodySliderRow(
                    label = "Output Volume",
                    valueText = "${(settings.speechVolume * 100).toInt()}%",
                    value = settings.speechVolume,
                    range = 0.2f..1.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(speechVolume = v) } }
                )
                ProsodySliderRow(
                    label = "Inter-Sentence Pauses",
                    valueText = "${settings.pauseDurationMs} ms",
                    value = settings.pauseDurationMs.toFloat(),
                    range = 100f..1000f,
                    onValueChange = { v -> onUpdateSettings { it.copy(pauseDurationMs = v.toInt()) } }
                )
                ProsodySliderRow(
                    label = "Sentence Emphasis Envelope",
                    valueText = "${(settings.sentenceEmphasis * 100).toInt()}%",
                    value = settings.sentenceEmphasis,
                    range = 0.1f..1.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(sentenceEmphasis = v) } }
                )
            }
        }

        // Live Voice Audition & Preview Box
        SectionHeader(
            title = "Offline Voice Audition & Preview",
            subtitle = "Synthesize and listen offline before applying to incoming calls"
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AudioWaveformVisualizer(
                    isActive = isSpeaking,
                    amplitude = settings.speechVolume,
                    barColor = EmeraldShield
                )
                OutlinedTextField(
                    value = previewSampleText,
                    onValueChange = { previewSampleText = it },
                    label = { Text("Audition Script (${settings.language.displayName})") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isSpeaking) onStopPreview() else onPreviewVoice(previewSampleText)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSpeaking) CrimsonSpam else EmeraldShield
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("studio_preview_tts_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSpeaking) "Stop Synthesis" else "Synthesize & Play Offline")
                    }
                    OutlinedButton(
                        onClick = { onSelectMood(settings.voiceMood) }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Mood Defaults")
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProsodySliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = CyanTelemetry)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

/**
 * Screen 9: Model Manager — Local Sherpa-ONNX Voice Model Catalog, Download/Provision,
 * Custom .onnx File Import, SHA-256 Checksum Verifier, and On-Demand Loading.
 */
@Composable
fun ModelManagerScreen(
    models: List<VoiceModelEntity>,
    activeModelId: String,
    onDownloadAndVerify: (VoiceModelEntity) -> Unit,
    onActivateModel: (VoiceModelEntity) -> Unit,
    onDeleteModel: (VoiceModelEntity) -> Unit,
    onImportCustomModel: (String, VoiceLanguage) -> Unit,
    onPreviewModel: (VoiceModelEntity) -> Unit
) {
    var customModelName by remember { mutableStateOf("") }
    var customModelLang by remember { mutableStateOf(VoiceLanguage.BENGALI_IN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("model_manager_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Local Sherpa-ONNX Voice Models",
            subtitle = "Zero cloud TTS APIs • Models are SHA-256 verified before loading on demand"
        )

        // Import Custom Local ONNX Voice Bundle Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Import Local Sherpa-ONNX (.onnx) Model Bundle", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        label = { Text("Custom Model Name") },
                        placeholder = { Text("Kolkata Bengali VITS") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onImportCustomModel(
                                customModelName.ifBlank { "Imported ${customModelLang.displayName} VITS" },
                                customModelLang
                            )
                            customModelName = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanTelemetry),
                        modifier = Modifier.testTag("import_onnx_model_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Import & Verify")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = customModelLang == lang,
                            onClick = { customModelLang = lang },
                            label = { Text(lang.displayName) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(models, key = { it.id }) { model ->
                val isSelected = model.id == activeModelId ||
                    model.installStatus == ModelInstallStatus.ACTIVE_LOADED.name
                val statusBadge = when (model.installStatus) {
                    ModelInstallStatus.ACTIVE_LOADED.name,
                    ModelInstallStatus.VERIFIED_READY.name -> CapabilitySupportStatus.SUPPORTED
                    ModelInstallStatus.DOWNLOADING.name,
                    ModelInstallStatus.NOT_DOWNLOADED.name -> CapabilitySupportStatus.LIMITED
                    else -> CapabilitySupportStatus.UNSUPPORTED
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) EmeraldShield else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium
                        )
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
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${model.languageLabel} • ${model.architecture} • ${model.sampleRateHz} Hz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CapabilityStatusBadge(status = statusBadge)
                        }

                        Text(
                            text = "Speaker: ${model.speakerName} • Size: ${if (model.fileSizeMb > 0f) "${model.fileSizeMb} MB" else "Built-in OS"} • SHA-256: ${model.expectedSha256.take(16)}…",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanTelemetry
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (model.installStatus == ModelInstallStatus.NOT_DOWNLOADED.name ||
                                model.installStatus == ModelInstallStatus.CORRUPTED_CHECKSUM.name
                            ) {
                                Button(
                                    onClick = { onDownloadAndVerify(model) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_model_${model.id}")
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download & Verify")
                                }
                            } else {
                                Button(
                                    onClick = { onActivateModel(model) },
                                    enabled = !isSelected,
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("activate_model_${model.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isSelected) "Active Voice Model" else "Load Model")
                                }
                                OutlinedButton(
                                    onClick = { onPreviewModel(model) }
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                                    Spacer(Modifier.width(4.dp))
                                    Text("Preview")
                                }
                                if (model.id != "android-system-offline-tts") {
                                    IconButton(onClick = { onDeleteModel(model) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Model File",
                                            tint = CrimsonSpam
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
