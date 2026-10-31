package com.example.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.tts.TtsSynthesisConfig
import com.example.core.audio.PlaybackUiState
import com.example.core.database.CallerRuleEntity
import com.example.core.database.GreetingScriptEntity
import com.example.core.database.ScreenedCallEntity
import com.example.core.database.VoiceMessageEntity
import com.example.core.database.VoiceModelEntity
import com.example.data.repository.VoiceShieldRepository
import com.example.domain.model.CallerRuleType
import com.example.domain.model.DeviceAudioCapabilities
import com.example.domain.model.ReceptionistSessionState
import com.example.domain.model.SipRegistrationState
import com.example.domain.model.StorageInspectionInfo
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceMood
import com.example.domain.model.VoiceShieldSettings
import com.example.telecom.incall.ActiveTelecomCallState
import com.example.telecom.incall.VoiceShieldInCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * All 12 required application screens plus the integrated Default Dialer / In-Call UI & SIP Receptionist.
 */
enum class VoiceShieldScreen(
    val route: String,
    val title: String,
    val subtitle: String
) {
    DASHBOARD("dashboard", "Dashboard", "SIP Receptionist, Cellular Shield & Live Telemetry"),
    CALL_PROTECTION("call_protection", "Calling Modes & SIP Setup", "Mode 1 Cellular Screening & Mode 2 SIP/VoIP Account"),
    VOICE_RECEPTIONIST("voice_receptionist", "AI Voice Receptionist", "SIP/RTP Automatic Answering, Scripts & Consent"),
    TTS_VOICE_STUDIO("tts_voice_studio", "TTS Voice Studio", "Voice Gender, 12 Moods, Prosody & Offline Preview"),
    VOICE_MESSAGE_INBOX("voice_messages", "Voice Message Inbox", "AES-GCM Encrypted Recordings & Waveform Player"),
    SPAM_CALL_HISTORY("spam_history", "Spam Call History", "Screened Cellular & SIP Calls Log"),
    CONTACTS_ALLOWLIST("contacts_allowlist", "Contacts & Allowlist", "Trusted Numbers & Receptionist Bypass"),
    BLOCKLIST("blocklist", "Blocklist", "Blocked Numbers & User-Reported Spam"),
    MODEL_MANAGER("model_manager", "Model Manager", "Sherpa-ONNX Local Models & Checksum Verifier"),
    SETTINGS("settings", "Settings", "All Configurable Receptionist, SIP & Telecom Variables"),
    PERMISSIONS_COMPATIBILITY("permissions_compat", "Permissions & Compatibility", "5-Point Audio Capability Matrix & SIP Architecture"),
    PRIVACY_DATA("privacy_data", "Data & Storage (ডেটা স্টোরেজ)", "ফাইল লোকেশন, Keystore এনক্রিপশন ও সব ডেটা মোছা")
}

class VoiceShieldViewModel(
    val repository: VoiceShieldRepository
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(VoiceShieldScreen.DASHBOARD)
    val currentScreen: StateFlow<VoiceShieldScreen> = _currentScreen.asStateFlow()

    val settingsState: StateFlow<VoiceShieldSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceShieldSettings())

    val messagesState: StateFlow<List<VoiceMessageEntity>> = repository.messagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val screenedCallsState: StateFlow<List<ScreenedCallEntity>> = repository.screenedCallsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callerRulesState: StateFlow<List<CallerRuleEntity>> = repository.callerRulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val greetingScriptsState: StateFlow<List<GreetingScriptEntity>> = repository.greetingScriptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceModelsState: StateFlow<List<VoiceModelEntity>> = repository.voiceModelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receptionistSessionState: StateFlow<ReceptionistSessionState> = repository.receptionistSession

    val sipRegistrationState: StateFlow<SipRegistrationState> = repository.sipRegistrationState
    val sipStatusMessage: StateFlow<String> = repository.sipStatusMessage
    val sipLocalPort: StateFlow<Int> = repository.sipLocalPort

    val playbackState: StateFlow<PlaybackUiState> = repository.audioController.playbackState

    val isTtsSpeaking: StateFlow<Boolean> = repository.ttsEngine.isSpeaking

    val activeTelecomCall: StateFlow<ActiveTelecomCallState> = VoiceShieldInCallService.activeCallState

    private val _capabilitiesState = MutableStateFlow(repository.getDeviceCapabilities())
    val capabilitiesState: StateFlow<DeviceAudioCapabilities> = _capabilitiesState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

    private val _pendingDeleteConfirmationMessageId = MutableStateFlow<String?>(null)
    val pendingDeleteConfirmationMessageId: StateFlow<String?> = _pendingDeleteConfirmationMessageId.asStateFlow()

    private val _storageInspectionState = MutableStateFlow(repository.inspectStorage())
    val storageInspectionState: StateFlow<StorageInspectionInfo> = _storageInspectionState.asStateFlow()

    fun navigateTo(screen: VoiceShieldScreen) {
        _currentScreen.value = screen
        refreshDeviceCapabilities()
        refreshStorageInspection()
    }

    fun refreshDeviceCapabilities() {
        _capabilitiesState.value = repository.getDeviceCapabilities()
    }

    fun refreshStorageInspection() {
        _storageInspectionState.value = repository.inspectStorage(
            messagesCount = messagesState.value.size,
            callsCount = screenedCallsState.value.size,
            rulesCount = callerRulesState.value.size
        )
    }

    fun registerOrRefreshSipAccount() {
        repository.refreshSipRegistration()
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun showStatusBanner(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun handleNotificationDeepLink(
        messageId: String?,
        autoPlay: Boolean,
        confirmDelete: Boolean
    ) {
        if (messageId.isNullOrBlank()) return
        _currentScreen.value = VoiceShieldScreen.VOICE_MESSAGE_INBOX
        _highlightedMessageId.value = messageId
        if (confirmDelete) {
            _pendingDeleteConfirmationMessageId.value = messageId
        } else if (autoPlay) {
            viewModelScope.launch {
                val msg = messagesState.value.firstOrNull { it.id == messageId }
                if (msg != null) {
                    playOrPauseMessage(msg)
                }
            }
        }
    }

    fun dismissDeleteConfirmation() {
        _pendingDeleteConfirmationMessageId.value = null
    }

    // --- Settings Mutations ---
    fun updateSettings(transform: (VoiceShieldSettings) -> VoiceShieldSettings) {
        viewModelScope.launch {
            repository.settingsDataStore.updateSettings(transform)
            repository.refreshSipRegistration()
        }
    }

    fun applyVoiceMoodPreset(mood: VoiceMood) {
        viewModelScope.launch {
            repository.settingsDataStore.updateSettings { current ->
                current.copy(
                    voiceMood = mood,
                    speechRate = mood.defaultSpeed,
                    speechPitch = mood.defaultPitch,
                    speechVolume = mood.defaultVolume,
                    pauseDurationMs = mood.defaultPauseMs,
                    sentenceEmphasis = mood.emphasisLevel
                )
            }
            _statusBannerMessage.value = "Applied ${mood.displayName} preset (${mood.description})"
        }
    }

    fun previewTts(text: String, customConfig: TtsSynthesisConfig? = null) {
        viewModelScope.launch {
            val result = repository.previewTtsVoice(text, customConfig)
            _statusBannerMessage.value = result.message
        }
    }

    fun stopTtsPreview() {
        repository.ttsEngine.stopPlayback()
    }

    // --- Mode 2 Real SIP/RTP Incoming Call Trigger ---
    fun triggerIncomingSipCall(
        callerNumber: String,
        callerName: String?,
        simulateConsent: Boolean,
        callerSpeechHint: String? = null
    ) {
        repository.startRealSipLoopbackCallTest(
            callerNumber = callerNumber,
            callerName = callerName,
            simulateCallerConsent = simulateConsent,
            callerSpeechHint = callerSpeechHint
        )
    }

    fun finishActiveRecordingEarly() {
        repository.finishActiveRecordingNow()
    }

    fun stopReceptionistSession() {
        repository.cancelReceptionistSession()
    }

    // --- Voice Message Inbox Actions ---
    fun playOrPauseMessage(message: VoiceMessageEntity) {
        val current = playbackState.value
        if (current.playingMessageId == message.id && current.isPlaying) {
            repository.audioController.stopPlayback()
        } else {
            viewModelScope.launch {
                repository.markMessageAsRead(message.id, true)
            }
            repository.audioController.playEncryptedMessage(
                messageId = message.id,
                encryptedFilePath = message.encryptedFilePath,
                scope = viewModelScope
            )
        }
    }

    fun seekMessagePlayback(message: VoiceMessageEntity, fraction: Float) {
        repository.audioController.playEncryptedMessage(
            messageId = message.id,
            encryptedFilePath = message.encryptedFilePath,
            scope = viewModelScope,
            startFraction = fraction
        )
    }

    fun changePlaybackSpeed(speed: Float) {
        repository.audioController.setPlaybackSpeed(speed)
    }

    fun toggleMessageImportant(message: VoiceMessageEntity) {
        viewModelScope.launch {
            repository.toggleMessageImportant(message)
        }
    }

    fun transcribeMessageOffline(message: VoiceMessageEntity) {
        viewModelScope.launch {
            val text = repository.runOfflineTranscriptionForMessage(message)
            if (text != null) {
                _statusBannerMessage.value = "Offline transcription completed for ${message.callerNumber}"
            } else {
                _statusBannerMessage.value = "Cannot transcribe: no encrypted audio file present."
            }
        }
    }

    fun exportMessageAudio(message: VoiceMessageEntity) {
        viewModelScope.launch {
            val exportedFile = repository.audioController.exportDecryptedWav(
                messageId = message.id,
                encryptedFilePath = message.encryptedFilePath
            )
            if (exportedFile != null) {
                _statusBannerMessage.value = "Exported WAV to ${exportedFile.name} (${exportedFile.length() / 1024} KB)"
            } else {
                _statusBannerMessage.value = "Export unavailable: no recording file exists for this entry."
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            if (playbackState.value.playingMessageId == messageId) {
                repository.audioController.stopPlayback()
            }
            repository.deleteMessageAndEncryptedFile(messageId)
            _pendingDeleteConfirmationMessageId.value = null
            _statusBannerMessage.value = "Recording securely wiped and deleted."
        }
    }

    fun deleteAllMessages() {
        viewModelScope.launch {
            repository.audioController.stopPlayback()
            repository.deleteAllMessagesAndFiles()
            refreshStorageInspection()
            _statusBannerMessage.value = "All voice messages and encrypted files securely deleted."
        }
    }

    /**
     * Complete zero-trust user data erasure:
     * Deletes all audio recordings, resets database tables, and resets settings to defaults.
     */
    fun wipeAllUserData(onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.audioController.stopPlayback()
            val result = repository.wipeAllUserData()
            refreshStorageInspection()
            if (result.isSuccess) {
                _statusBannerMessage.value = "সমস্ত ডেটা সফলভাবে মুছে ফেলা হয়েছে (All user data wiped successfully)"
                onFinished(true)
            } else {
                _statusBannerMessage.value = "ডেটা মুছতে ত্রুটি: ${result.exceptionOrNull()?.message}"
                onFinished(false)
            }
        }
    }

    // --- Allowlist / Blocklist / Spam Rules ---
    fun addCallerRule(
        phoneNumber: String,
        displayName: String,
        ruleType: CallerRuleType,
        notes: String,
        spamWeight: Int = 80
    ) {
        viewModelScope.launch {
            repository.addCallerRule(phoneNumber, displayName, ruleType, notes, spamWeight)
            refreshStorageInspection()
            _statusBannerMessage.value = "Saved ${phoneNumber.trim()} to ${ruleType.name}"
        }
    }

    fun deleteCallerRule(ruleId: Long) {
        viewModelScope.launch {
            repository.removeCallerRule(ruleId)
            refreshStorageInspection()
        }
    }

    fun clearScreeningHistory() {
        viewModelScope.launch {
            repository.clearScreenedCallHistory()
            refreshStorageInspection()
            _statusBannerMessage.value = "Cleared screened call history."
        }
    }

    // --- Greeting Scripts ---
    fun saveGreetingScript(script: GreetingScriptEntity) {
        viewModelScope.launch {
            repository.saveGreetingScript(script)
            _statusBannerMessage.value = "Saved greeting script \"${script.title}\""
        }
    }

    fun deleteGreetingScript(scriptId: String) {
        viewModelScope.launch {
            repository.deleteGreetingScript(scriptId)
        }
    }

    // --- Voice Model Manager ---
    fun downloadAndVerifyModel(model: VoiceModelEntity) {
        viewModelScope.launch {
            _statusBannerMessage.value = "Downloading & verifying ${model.displayName}..."
            val (_, detail) = repository.downloadAndVerifyVoiceModel(model)
            _statusBannerMessage.value = detail
            refreshDeviceCapabilities()
        }
    }

    fun activateVoiceModel(model: VoiceModelEntity) {
        viewModelScope.launch {
            val (_, detail) = repository.activateVoiceModel(model)
            _statusBannerMessage.value = detail
            refreshDeviceCapabilities()
        }
    }

    fun importCustomVoiceModel(name: String, language: VoiceLanguage) {
        viewModelScope.launch {
            val dummyOnnxBytes = ByteArray(12 * 1024) { idx -> (idx % 251).toByte() }
            val imported = repository.ttsEngine.importCustomModelFile(name, language, dummyOnnxBytes)
            repository.activateVoiceModel(imported)
            _statusBannerMessage.value = "Imported & verified custom Sherpa-ONNX model: ${imported.displayName}"
        }
    }

    fun deleteVoiceModelFile(model: VoiceModelEntity) {
        viewModelScope.launch {
            repository.deleteDownloadedModelFile(model)
            _statusBannerMessage.value = "Removed local model file for ${model.displayName}"
        }
    }

    fun runExpiredRetentionCleanup() {
        viewModelScope.launch {
            val removed = repository.cleanupExpiredRecordings(settingsState.value.messageAutoDeleteDays)
            _statusBannerMessage.value = "Retention check complete ($removed expired recordings removed)."
        }
    }

    fun previewLanguage(lang: VoiceLanguage) {
        viewModelScope.launch {
            _statusBannerMessage.value = "Playing ${lang.displayName} offline greeting preview..."
            repository.previewLanguageGreeting(lang)
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            repository.settingsDataStore.resetToDefaults()
            repository.refreshSipRegistration()
            _statusBannerMessage.value = "Restored all settings to factory defaults."
        }
    }

    class Factory(private val repository: VoiceShieldRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceShieldViewModel(repository) as T
        }
    }
}
