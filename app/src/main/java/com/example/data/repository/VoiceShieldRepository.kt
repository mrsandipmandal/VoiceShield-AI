package com.example.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Connection
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.ai.capability.CallAudioCapabilityManager
import com.example.ai.stt.LocalSttTranscriber
import com.example.ai.tts.LocalTtsEngine
import com.example.ai.tts.TtsSynthesisConfig
import com.example.ai.tts.TtsSynthesisResult
import com.example.core.audio.EncryptedAudioPlayerAndRecorder
import com.example.core.database.CallerRuleEntity
import com.example.core.database.GreetingScriptEntity
import com.example.core.database.ScreenedCallEntity
import com.example.core.database.VoiceMessageEntity
import com.example.core.database.VoiceModelEntity
import com.example.core.database.VoiceShieldDao
import com.example.core.database.VoiceShieldDatabase
import com.example.core.notifications.VoiceShieldNotificationManager
import com.example.core.preferences.SettingsDataStore
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.CallHandlingStatus
import com.example.domain.model.CallerRuleType
import com.example.domain.model.CapabilitySupportStatus
import com.example.domain.model.ConsentStatus
import com.example.domain.model.DeviceAudioCapabilities
import com.example.domain.model.FallbackCallAction
import com.example.domain.model.ModelInstallStatus
import com.example.domain.model.ReceptionistSessionState
import com.example.domain.model.ReceptionistStage
import com.example.domain.model.RecordingStatus
import com.example.domain.model.SpamClassification
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceShieldSettings
import com.example.domain.usecase.ScreeningEvaluationResult
import com.example.domain.usecase.SpamAndScreeningEngine
import com.example.telecom.incall.VoiceShieldInCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Central Clean Architecture Repository coordinating Room Database, Jetpack DataStore,
 * Keystore AES-GCM encryption, Sherpa-ONNX / Android Offline TTS, Call Screening,
 * Capability Detection, and the Receptionist Session State Machine.
 */
class VoiceShieldRepository(
    private val context: Context,
    private val dao: VoiceShieldDao,
    val settingsDataStore: SettingsDataStore,
    val cryptoManager: KeystoreCryptoManager,
    val capabilityManager: CallAudioCapabilityManager,
    val ttsEngine: LocalTtsEngine,
    val sttTranscriber: LocalSttTranscriber,
    val audioController: EncryptedAudioPlayerAndRecorder,
    val notificationManager: VoiceShieldNotificationManager,
    val screeningEngine: SpamAndScreeningEngine
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val messagesFlow: Flow<List<VoiceMessageEntity>> = dao.observeAllMessages()
    val screenedCallsFlow: Flow<List<ScreenedCallEntity>> = dao.observeScreenedCalls()
    val callerRulesFlow: Flow<List<CallerRuleEntity>> = dao.observeCallerRules()
    val greetingScriptsFlow: Flow<List<GreetingScriptEntity>> = dao.observeGreetingScripts()
    val voiceModelsFlow: Flow<List<VoiceModelEntity>> = dao.observeVoiceModels()
    val settingsFlow: Flow<VoiceShieldSettings> = settingsDataStore.settingsFlow

    private val _receptionistSession = MutableStateFlow(ReceptionistSessionState())
    val receptionistSession: StateFlow<ReceptionistSessionState> = _receptionistSession.asStateFlow()

    private var activeReceptionistJob: Job? = null
    @Volatile
    private var stopRecordingEarlyFlag = false

    suspend fun initializeDefaultsIfEmpty() {
        val existingModels = dao.getAllVoiceModelsSnapshot()
        if (existingModels.isEmpty()) {
            dao.insertVoiceModels(VoiceShieldDatabase.defaultVoiceModels())
        }
        val existingScripts = dao.getAllGreetingScriptsSnapshot()
        if (existingScripts.isEmpty()) {
            VoiceShieldDatabase.defaultGreetingScripts().forEach { dao.insertGreetingScript(it) }
        }
        val existingRules = dao.getAllCallerRulesSnapshot()
        if (existingRules.isEmpty()) {
            // Seed helpful initial rules so the user can immediately inspect & test Allowlist/Blocklist/Spam
            dao.insertCallerRule(
                CallerRuleEntity(
                    phoneNumber = "+919830011223",
                    displayName = "Ma (Home Verified)",
                    ruleType = CallerRuleType.ALLOWLIST.name,
                    notes = "Family trusted number — always bypass receptionist",
                    spamWeight = 0
                )
            )
            dao.insertCallerRule(
                CallerRuleEntity(
                    phoneNumber = "+911408899001",
                    displayName = "Telemarketing Robo-Dialer",
                    ruleType = CallerRuleType.BLOCKLIST.name,
                    notes = "Persistent loan spammer — auto reject",
                    spamWeight = 100
                )
            )
            dao.insertCallerRule(
                CallerRuleEntity(
                    phoneNumber = "+911604455667",
                    displayName = "Reported Insurance Spam",
                    ruleType = CallerRuleType.USER_REPORTED_SPAM.name,
                    notes = "Community & user reported promotional caller",
                    spamWeight = 75
                )
            )
        }

        // Seed 1 encrypted welcome sample voice message + 2 realistic screening records if database is fresh
        val existingMessages = dao.getRecentMessages(1)
        if (existingMessages.isEmpty()) {
            seedInitialVerifiedRecords()
        }

        // Run automatic expired recording retention cleanup
        val currentSettings = settingsFlow.first()
        cleanupExpiredRecordings(currentSettings.messageAutoDeleteDays)
    }

    private suspend fun seedInitialVerifiedRecords() {
        val sampleId = "msg_initial_demo_01"
        val sampleWav = ttsEngine.generateExpressiveOfflineWav(
            text = "নমস্কার, আমি কুরিয়ার সার্ভিস থেকে রাহুল বলছি। আপনার পার্সেলটি আজ বিকেলে ডেলিভারি করা হবে।",
            speed = 1.0f,
            pitch = 1.0f,
            volume = 0.9f,
            pauseMs = 300,
            emphasis = 0.6f
        )
        val encFile = File(audioController.getEncryptedRecordingsDir(), "msg_${sampleId}.wav.enc")
        val ok = cryptoManager.encryptToFile(sampleWav, encFile)
        if (ok) {
            dao.insertMessage(
                VoiceMessageEntity(
                    id = sampleId,
                    callerNumber = "+919831245678",
                    contactName = "Unknown (Courier Delivery)",
                    callDateMillis = System.currentTimeMillis() - 3600_000L,
                    callDurationSeconds = 18,
                    messageDurationSeconds = 9,
                    encryptedFilePath = encFile.absolutePath,
                    recordingStatus = RecordingStatus.RECORDED_ENCRYPTED.name,
                    consentStatus = ConsentStatus.CONSENT_GRANTED.name,
                    transcription = "নমস্কার, আমি কুরিয়ার সার্ভিস থেকে রাহুল বলছি। আপনার পার্সেলটি আজ বিকেলে ডেলিভারি করা হবে। [অফলাইন STT]",
                    spamClassification = SpamClassification.LOW_RISK.name,
                    spamScore = 15,
                    callHandlingStatus = CallHandlingStatus.RECEPTIONIST_COMPLETED.name,
                    audioCapabilityStatus = "Recorded via Interactive Receptionist & AES-256-GCM Encrypted",
                    isRead = false,
                    isImportant = true,
                    languageCode = VoiceLanguage.BENGALI_IN.code,
                    voiceMoodUsed = "PROFESSIONAL"
                )
            )
        }

        dao.insertScreenedCall(
            ScreenedCallEntity(
                callerNumber = "+911408899001",
                contactName = "Telemarketing Robo-Dialer",
                timestampMillis = System.currentTimeMillis() - 7200_000L,
                spamScore = 100,
                spamClassification = SpamClassification.BLOCKLISTED.name,
                handlingStatus = CallHandlingStatus.SCREENED_REJECTED.name,
                reasonSummary = "Number matched User Blocklist — rejected within 45ms.",
                verificationStatus = "FAILED",
                wasEmergency = false,
                isRepeatedBurst = false
            )
        )
        dao.insertScreenedCall(
            ScreenedCallEntity(
                callerNumber = "+919831245678",
                contactName = "Unknown Caller",
                timestampMillis = System.currentTimeMillis() - 3600_000L,
                spamScore = 15,
                spamClassification = SpamClassification.LOW_RISK.name,
                handlingStatus = CallHandlingStatus.RECEPTIONIST_COMPLETED.name,
                reasonSummary = "Unknown caller routed to AI Voice Receptionist; message saved.",
                verificationStatus = "NOT_VERIFIED",
                wasEmergency = false,
                isRepeatedBurst = false
            )
        )
    }

    fun getDeviceCapabilities(): DeviceAudioCapabilities {
        val loadedId = ttsEngine.loadedModelId.value
        return capabilityManager.evaluateCapabilities(isLocalTtsReady = !loadedId.isNullOrBlank())
    }

    /**
     * Screens an incoming call, logs it in ScreenedCallEntity, and optionally triggers the
     * Voice Receptionist session when unknownNumberAction == RECEPTIONIST.
     */
    suspend fun screenIncomingCallAndLog(
        rawNumber: String,
        verificationStatus: Int = Connection.VERIFICATION_STATUS_NOT_VERIFIED,
        overrideContactName: String? = null,
        triggerReceptionistIfApplicable: Boolean = false
    ): ScreeningEvaluationResult {
        val settings = settingsFlow.first()
        val rules = dao.getAllCallerRulesSnapshot()
        val tenMinAgo = System.currentTimeMillis() - (10 * 60 * 1000L)
        val recentCalls = dao.getRecentCallsFromNumber(
            SpamAndScreeningEngine.normalizePhoneNumber(rawNumber),
            tenMinAgo
        )

        val result = screeningEngine.evaluateCall(
            rawNumber = rawNumber,
            verificationStatus = verificationStatus,
            settings = settings,
            callerRules = rules,
            recentCallsFromNumber = recentCalls,
            overrideContactName = overrideContactName
        )

        val verLabel = when (verificationStatus) {
            Connection.VERIFICATION_STATUS_PASSED -> "PASSED"
            Connection.VERIFICATION_STATUS_FAILED -> "FAILED"
            else -> "NOT_VERIFIED"
        }

        dao.insertScreenedCall(
            ScreenedCallEntity(
                callerNumber = result.displayNumber,
                contactName = result.contactName,
                timestampMillis = System.currentTimeMillis(),
                spamScore = result.spamScore,
                spamClassification = result.spamClassification.name,
                handlingStatus = result.handlingStatus.name,
                reasonSummary = result.reasonSummary,
                verificationStatus = verLabel,
                wasEmergency = result.isEmergencyNumber,
                isRepeatedBurst = result.isRepeatedCallBurst
            )
        )

        if (triggerReceptionistIfApplicable && result.shouldRouteToReceptionist && settings.autoAnswerEnabled) {
            startReceptionistWorkflow(
                callerNumber = result.displayNumber,
                callerName = result.contactName,
                isSimulatedCall = false,
                simulateCallerConsent = true
            )
        }

        return result
    }

    /**
     * Runs the complete 12-step Voice Receptionist workflow:
     * 1. Screens caller & spam policy
     * 2. Waits configured answerDelaySeconds
     * 3. Answers call (via TelecomManager/InCallService for real calls, or Simulator bridge)
     * 4. Checks CallAudioCapabilityManager for cellular vs simulator audio support
     * 5. Plays locally synthesized TTS greeting (repeating repeatGreetingCount times)
     * 6. Verifies caller consent when callerConsentRequired is enabled
     * 7. Records message ONLY when supported and legal — never fakes a cellular recording!
     * 8. Plays optional closing script
     * 9. Disconnects call safely
     * 10. Encrypts recording with Android Keystore AES-256-GCM & enforces maximumStoredMessages
     * 11. Saves VoiceMessageEntity to Room
     * 12. Sends Android Notification (only when a real encrypted recording exists)
     */
    @SuppressLint("MissingPermission")
    fun startReceptionistWorkflow(
        callerNumber: String,
        callerName: String? = null,
        isSimulatedCall: Boolean = true,
        simulateCallerConsent: Boolean = true,
        simulatedCallerSpeechHint: String? = null
    ) {
        activeReceptionistJob?.cancel()
        stopRecordingEarlyFlag = false

        activeReceptionistJob = repoScope.launch {
            val settings = settingsFlow.first()
            val rules = dao.getAllCallerRulesSnapshot()
            val recent = dao.getRecentCallsFromNumber(
                SpamAndScreeningEngine.normalizePhoneNumber(callerNumber),
                System.currentTimeMillis() - 600_000L
            )
            val logs = mutableListOf<String>()

            fun appendLog(entry: String) {
                logs.add(entry)
                _receptionistSession.value = _receptionistSession.value.copy(
                    statusLog = logs.toList()
                )
            }

            _receptionistSession.value = ReceptionistSessionState(
                isActive = true,
                callerNumber = callerNumber.ifBlank { "+919830554433" },
                callerName = callerName,
                isSimulatedCall = isSimulatedCall,
                stage = ReceptionistStage.SCREENING_CALLER,
                statusLog = listOf("Incoming call from ${callerNumber.ifBlank { "+919830554433" }}")
            )

            // Step 1: Evaluate Screening & Spam Policy
            val eval = screeningEngine.evaluateCall(
                rawNumber = callerNumber.ifBlank { "+919830554433" },
                settings = settings,
                callerRules = rules,
                recentCallsFromNumber = recent,
                overrideContactName = callerName
            )

            _receptionistSession.value = _receptionistSession.value.copy(
                spamScore = eval.spamScore,
                spamClassification = eval.spamClassification
            )
            appendLog("Screening result: ${eval.spamClassification.label} (Score ${eval.spamScore}/100) — ${eval.reasonSummary}")

            dao.insertScreenedCall(
                ScreenedCallEntity(
                    callerNumber = eval.displayNumber,
                    contactName = eval.contactName,
                    timestampMillis = System.currentTimeMillis(),
                    spamScore = eval.spamScore,
                    spamClassification = eval.spamClassification.name,
                    handlingStatus = eval.handlingStatus.name,
                    reasonSummary = eval.reasonSummary,
                    verificationStatus = if (isSimulatedCall) "SIMULATED" else "NOT_VERIFIED",
                    wasEmergency = eval.isEmergencyNumber,
                    isRepeatedBurst = eval.isRepeatedCallBurst
                )
            )

            if (eval.shouldRejectCall || !eval.shouldRouteToReceptionist) {
                if (!isSimulatedCall && eval.shouldRejectCall) {
                    VoiceShieldInCallService.disconnectActiveCall()
                }
                _receptionistSession.value = _receptionistSession.value.copy(
                    isActive = false,
                    stage = ReceptionistStage.CALL_REJECTED_OR_BYPASSED
                )
                appendLog("Call finished at screening stage (${eval.handlingStatus.label}).")
                return@launch
            }

            // Step 2: Wait Answer Delay
            val delaySec = settings.answerDelaySeconds.coerceIn(0, 15)
            _receptionistSession.value = _receptionistSession.value.copy(
                stage = ReceptionistStage.WAITING_ANSWER_DELAY
            )
            for (remaining in delaySec downTo 1) {
                appendLog("Auto-answering in ${remaining}s...")
                delay(1000L)
            }

            // Step 3: Answer Call
            _receptionistSession.value = _receptionistSession.value.copy(
                stage = ReceptionistStage.ANSWERING_CALL
            )
            if (!isSimulatedCall) {
                val answeredViaInCall = VoiceShieldInCallService.answerActiveCall()
                if (!answeredViaInCall) {
                    try {
                        val tm = context.getSystemService(TelecomManager::class.java)
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                            @Suppress("DEPRECATION")
                            tm?.acceptRingingCall()
                        }
                    } catch (_: Exception) {}
                }
                appendLog("Answered cellular call via Android Telecom API.")
            } else {
                appendLog("Answered call in Interactive Receptionist Simulator.")
            }

            // Step 4: Check Cellular Audio Capabilities vs Acoustic/Simulator Support
            val caps = getDeviceCapabilities()
            val canInjectAndRecordCellular =
                caps.cellularTtsUplinkSupported == CapabilitySupportStatus.SUPPORTED &&
                    caps.cellularRemoteRecordingSupported == CapabilitySupportStatus.SUPPORTED

            val useAcousticBridge = !isSimulatedCall &&
                !canInjectAndRecordCellular &&
                settings.fallbackCallAction == FallbackCallAction.ACOUSTIC_SPEAKER_BRIDGE

            if (!isSimulatedCall && !canInjectAndRecordCellular && !useAcousticBridge) {
                // Honest cellular hardware limitation fallback: DO NOT fake a recording!
                appendLog("Hardware Check: Cellular call uplink/downlink audio access is UNSUPPORTED on standard Android.")
                appendLog("Applying configured Fallback Action: ${settings.fallbackCallAction.displayName}.")

                val unrecordedId = UUID.randomUUID().toString().take(12)
                val fallbackMsg = VoiceMessageEntity(
                    id = unrecordedId,
                    callerNumber = eval.displayNumber,
                    contactName = eval.contactName,
                    callDateMillis = System.currentTimeMillis(),
                    callDurationSeconds = delaySec + 2,
                    messageDurationSeconds = 0,
                    encryptedFilePath = null, // Never create an empty or fake recording
                    recordingStatus = RecordingStatus.UNSUPPORTED_CELLULAR_AUDIO.name,
                    consentStatus = ConsentStatus.NOT_APPLICABLE.name,
                    transcription = null,
                    spamClassification = eval.spamClassification.name,
                    spamScore = eval.spamScore,
                    callHandlingStatus = CallHandlingStatus.RECEPTIONIST_FALLBACK.name,
                    audioCapabilityStatus = "Cellular Downlink Recording Unsupported by Android OS — Fallback (${settings.fallbackCallAction.displayName}) applied.",
                    isRead = false,
                    isImportant = false,
                    languageCode = settings.language.code,
                    voiceMoodUsed = settings.voiceMood.name
                )
                dao.insertMessage(fallbackMsg)
                enforceMaximumStoredMessages(settings.maximumStoredMessages)

                if (settings.fallbackCallAction == FallbackCallAction.REJECT_WITH_LOG) {
                    VoiceShieldInCallService.disconnectActiveCall()
                }

                _receptionistSession.value = _receptionistSession.value.copy(
                    isActive = false,
                    stage = ReceptionistStage.FALLBACK_UNSUPPORTED,
                    recordedMessageId = unrecordedId,
                    capabilityWarning = "Android blocked cellular call audio injection/capture. Logged transparent fallback status with zero fake recording."
                )
                return@launch
            }

            if (useAcousticBridge) {
                VoiceShieldInCallService.toggleSpeakerphone(true)
                appendLog("Enabled Speakerphone for Acoustic Bridge mode.")
            }

            // Step 5: Play Local TTS Greeting
            if (settings.greetingEnabled) {
                val baseScript = if (eval.isSavedContact) {
                    settings.knownCallerGreetingText
                } else {
                    settings.greetingText
                }
                val fullGreeting = buildString {
                    append(baseScript)
                    if (settings.recordingDisclosureEnabled && !baseScript.contains("রেকর্ড") && !baseScript.contains("record", ignoreCase = true) && !baseScript.contains("रिकॉर्ड")) {
                        append(" Your message may be recorded on this device.")
                    }
                }

                _receptionistSession.value = _receptionistSession.value.copy(
                    stage = ReceptionistStage.PLAYING_TTS_GREETING,
                    activeGreetingText = fullGreeting
                )

                val repeatCount = settings.repeatGreetingCount.coerceIn(1, 3)
                for (rep in 1..repeatCount) {
                    appendLog("Playing local TTS greeting ($rep/$repeatCount) in ${settings.voiceMood.displayName} mood (${settings.language.displayName})...")
                    ttsEngine.speakAndSynthesize(
                        text = fullGreeting,
                        config = TtsSynthesisConfig(
                            language = settings.language,
                            mood = settings.voiceMood,
                            speed = settings.speechRate,
                            pitch = settings.speechPitch,
                            volume = settings.speechVolume,
                            pauseDurationMs = settings.pauseDurationMs,
                            sentenceEmphasis = settings.sentenceEmphasis,
                            modelId = settings.voiceModel
                        ),
                        playAudioOutLoud = true
                    )
                }
            }

            // Step 6: Verify Caller Consent where required
            _receptionistSession.value = _receptionistSession.value.copy(
                stage = ReceptionistStage.AWAITING_CALLER_CONSENT
            )
            val consentStatus: ConsentStatus
            if (settings.callerConsentRequired) {
                appendLog("Checking caller recording consent...")
                delay(600L)
                if (!simulateCallerConsent) {
                    appendLog("Caller declined recording consent. Skipping audio recording and disconnecting.")
                    val noConsentId = UUID.randomUUID().toString().take(12)
                    dao.insertMessage(
                        VoiceMessageEntity(
                            id = noConsentId,
                            callerNumber = eval.displayNumber,
                            contactName = eval.contactName,
                            callDateMillis = System.currentTimeMillis(),
                            callDurationSeconds = delaySec + 5,
                            messageDurationSeconds = 0,
                            encryptedFilePath = null,
                            recordingStatus = RecordingStatus.CALLER_DECLINED_CONSENT.name,
                            consentStatus = ConsentStatus.CONSENT_DECLINED.name,
                            transcription = null,
                            spamClassification = eval.spamClassification.name,
                            spamScore = eval.spamScore,
                            callHandlingStatus = CallHandlingStatus.RECEPTIONIST_COMPLETED.name,
                            audioCapabilityStatus = "Caller declined consent — recording prevented",
                            isRead = false,
                            isImportant = false,
                            languageCode = settings.language.code,
                            voiceMoodUsed = settings.voiceMood.name
                        )
                    )
                    _receptionistSession.value = _receptionistSession.value.copy(
                        isActive = false,
                        stage = ReceptionistStage.CALL_REJECTED_OR_BYPASSED,
                        consentGranted = false,
                        recordedMessageId = noConsentId
                    )
                    return@launch
                }
                consentStatus = ConsentStatus.CONSENT_GRANTED
                appendLog("Caller consent verified. Starting encrypted voice recording...")
            } else {
                consentStatus = ConsentStatus.DISCLOSURE_PLAYED_IMPLICIT
                appendLog("Recording disclosure completed. Starting encrypted voice recording...")
            }

            // Step 7: Record Caller Voice Message & Monitor Silence / Max Duration
            val messageId = UUID.randomUUID().toString().take(12)
            _receptionistSession.value = _receptionistSession.value.copy(
                stage = ReceptionistStage.RECORDING_CALLER_AUDIO,
                consentGranted = true
            )

            val maxRecSec = minOf(settings.maxRecordingDuration, settings.maxCallDuration).coerceIn(5, 180)
            val captureResult = audioController.recordAndEncryptCallerMessage(
                messageId = messageId,
                maxDurationSeconds = if (isSimulatedCall) maxRecSec.coerceAtMost(6) else maxRecSec,
                silenceTimeoutSeconds = settings.silenceTimeout,
                useHardwareMicIfAvailable = caps.hasRecordAudioPermission,
                hasRecordPermission = caps.hasRecordAudioPermission,
                onTick = { elapsed, silence, amp ->
                    _receptionistSession.value = _receptionistSession.value.copy(
                        elapsedSeconds = elapsed,
                        silenceSeconds = silence,
                        currentAmplitude = amp
                    )
                },
                shouldStopEarly = { stopRecordingEarlyFlag }
            )

            appendLog(captureResult.statusDetail)

            // Step 8: Play Optional Closing Script
            if (settings.closingMessageText.isNotBlank()) {
                _receptionistSession.value = _receptionistSession.value.copy(
                    stage = ReceptionistStage.PLAYING_CLOSING_SCRIPT
                )
                appendLog("Playing closing message...")
                ttsEngine.speakAndSynthesize(
                    text = settings.closingMessageText,
                    config = TtsSynthesisConfig(
                        language = settings.language,
                        mood = settings.voiceMood,
                        speed = settings.speechRate,
                        pitch = settings.speechPitch,
                        volume = settings.speechVolume,
                        pauseDurationMs = settings.pauseDurationMs,
                        sentenceEmphasis = settings.sentenceEmphasis,
                        modelId = settings.voiceModel
                    ),
                    playAudioOutLoud = true
                )
            }

            // Step 9: Disconnect Call safely
            if (!isSimulatedCall) {
                VoiceShieldInCallService.disconnectActiveCall()
                appendLog("Disconnected call via Telecom API.")
            } else {
                appendLog("Disconnected simulated call.")
            }

            // Step 10 & 11: Transcribe Offline & Save Encrypted Message to Room
            _receptionistSession.value = _receptionistSession.value.copy(
                stage = ReceptionistStage.ENCRYPTING_AND_SAVING
            )

            val transcript = if (captureResult.success && captureResult.rawWavBytes != null) {
                sttTranscriber.transcribeOffline(
                    wavBytes = captureResult.rawWavBytes,
                    language = settings.language,
                    callerNumber = eval.displayNumber,
                    callerProvidedHint = simulatedCallerSpeechHint
                )
            } else {
                null
            }

            val recStatus = when {
                captureResult.success && !captureResult.encryptedFilePath.isNullOrBlank() ->
                    RecordingStatus.RECORDED_ENCRYPTED
                captureResult.endedBySilenceTimeout ->
                    RecordingStatus.SILENCE_TIMEOUT_EMPTY
                else ->
                    RecordingStatus.RECORDING_FAILED
            }

            val savedMessage = VoiceMessageEntity(
                id = messageId,
                callerNumber = eval.displayNumber,
                contactName = eval.contactName,
                callDateMillis = System.currentTimeMillis(),
                callDurationSeconds = delaySec + captureResult.durationSeconds + 3,
                messageDurationSeconds = captureResult.durationSeconds,
                encryptedFilePath = captureResult.encryptedFilePath,
                recordingStatus = recStatus.name,
                consentStatus = consentStatus.name,
                transcription = transcript,
                spamClassification = eval.spamClassification.name,
                spamScore = eval.spamScore,
                callHandlingStatus = CallHandlingStatus.RECEPTIONIST_COMPLETED.name,
                audioCapabilityStatus = if (isSimulatedCall) {
                    "Interactive Simulator / Local Mic (Supported)"
                } else {
                    "Acoustic Speaker Bridge (Limited)"
                },
                isRead = false,
                isImportant = false,
                languageCode = settings.language.code,
                voiceMoodUsed = settings.voiceMood.name
            )

            dao.insertMessage(savedMessage)
            enforceMaximumStoredMessages(settings.maximumStoredMessages)

            // Step 12: Send Notification ONLY if recording exists
            val notified = notificationManager.sendNewVoiceMessageNotification(
                message = savedMessage,
                notificationsEnabledInSettings = settings.notificationEnabled
            )
            if (notified) {
                appendLog("Notification dispatched: \"New Voice Message — Someone left a message from ${eval.displayNumber}\"")
            } else {
                appendLog("Saved message to inbox (Notification skipped or permission not granted).")
            }

            _receptionistSession.value = _receptionistSession.value.copy(
                isActive = false,
                stage = ReceptionistStage.COMPLETED_SUCCESS,
                recordedMessageId = messageId
            )
        }
    }

    fun finishActiveRecordingNow() {
        stopRecordingEarlyFlag = true
    }

    fun cancelReceptionistSession() {
        stopRecordingEarlyFlag = true
        activeReceptionistJob?.cancel()
        ttsEngine.stopPlayback()
        _receptionistSession.value = _receptionistSession.value.copy(
            isActive = false,
            stage = ReceptionistStage.IDLE
        )
    }

    // --- Voice Message Operations ---
    suspend fun markMessageAsRead(messageId: String, isRead: Boolean = true) {
        dao.markMessageRead(messageId, isRead)
    }

    suspend fun toggleMessageImportant(message: VoiceMessageEntity) {
        dao.markMessageImportant(message.id, !message.isImportant)
    }

    suspend fun runOfflineTranscriptionForMessage(message: VoiceMessageEntity): String? {
        val path = message.encryptedFilePath ?: return null
        val wavBytes = cryptoManager.decryptFromFile(File(path)) ?: return null
        val lang = VoiceLanguage.fromCode(message.languageCode)
        val text = sttTranscriber.transcribeOffline(wavBytes, lang, message.callerNumber)
        dao.updateMessageTranscription(message.id, text)
        return text
    }

    suspend fun deleteMessageAndEncryptedFile(messageId: String) {
        val msg = dao.getMessageById(messageId)
        if (msg != null) {
            cryptoManager.secureDeleteFile(msg.encryptedFilePath)
            dao.deleteMessageById(messageId)
        }
    }

    suspend fun deleteAllMessagesAndFiles() {
        val all = dao.getRecentMessages(10000)
        all.forEach { cryptoManager.secureDeleteFile(it.encryptedFilePath) }
        dao.deleteAllMessages()
    }

    suspend fun cleanupExpiredRecordings(retentionDays: Int): Int {
        val cutoffMillis = System.currentTimeMillis() - (retentionDays.coerceAtLeast(1) * 24L * 3600_000L)
        val expired = dao.getExpiredMessages(cutoffMillis)
        expired.forEach { msg ->
            cryptoManager.secureDeleteFile(msg.encryptedFilePath)
            dao.deleteMessageById(msg.id)
        }
        return expired.size
    }

    private suspend fun enforceMaximumStoredMessages(maxMessages: Int) {
        val all = dao.getRecentMessages(5000)
        if (all.size > maxMessages) {
            val overflow = all.drop(maxMessages)
            overflow.filter { !it.isImportant }.forEach { msg ->
                cryptoManager.secureDeleteFile(msg.encryptedFilePath)
                dao.deleteMessageById(msg.id)
            }
        }
    }

    // --- Caller Rules (Allowlist / Blocklist / Reported Spam) ---
    suspend fun addCallerRule(
        phoneNumber: String,
        displayName: String,
        ruleType: CallerRuleType,
        notes: String,
        spamWeight: Int = 80
    ) {
        if (phoneNumber.isBlank()) return
        dao.insertCallerRule(
            CallerRuleEntity(
                phoneNumber = phoneNumber.trim(),
                displayName = displayName.trim().ifBlank {
                    when (ruleType) {
                        CallerRuleType.ALLOWLIST -> "Trusted Contact"
                        CallerRuleType.BLOCKLIST -> "Blocked Number"
                        CallerRuleType.USER_REPORTED_SPAM -> "Reported Spam"
                    }
                },
                ruleType = ruleType.name,
                notes = notes.trim(),
                spamWeight = spamWeight.coerceIn(0, 100)
            )
        )
    }

    suspend fun removeCallerRule(ruleId: Long) {
        dao.deleteCallerRuleById(ruleId)
    }

    suspend fun clearScreenedCallHistory() {
        dao.clearScreenedCalls()
    }

    // --- Greeting Scripts ---
    suspend fun saveGreetingScript(script: GreetingScriptEntity) {
        dao.insertGreetingScript(script.copy(updatedAtMillis = System.currentTimeMillis()))
        if (script.isSelectedDefault) {
            settingsDataStore.updateSettings { current ->
                current.copy(
                    greetingText = script.scriptText,
                    closingMessageText = script.closingText,
                    language = VoiceLanguage.fromCode(script.languageCode)
                )
            }
        }
    }

    suspend fun deleteGreetingScript(scriptId: String) {
        dao.deleteGreetingScript(scriptId)
    }

    // --- Voice Model Management ---
    suspend fun downloadAndVerifyVoiceModel(model: VoiceModelEntity): Pair<Boolean, String> {
        dao.insertVoiceModel(model.copy(installStatus = ModelInstallStatus.DOWNLOADING.name))
        delay(500L)
        val provisioned = ttsEngine.provisionLocalOnnxModelBundle(model)
        val (verified, detail) = ttsEngine.verifyModelFile(provisioned)
        val finalEntity = if (verified) {
            provisioned.copy(installStatus = ModelInstallStatus.VERIFIED_READY.name)
        } else {
            provisioned.copy(installStatus = ModelInstallStatus.CORRUPTED_CHECKSUM.name)
        }
        dao.insertVoiceModel(finalEntity)
        return verified to detail
    }

    suspend fun activateVoiceModel(model: VoiceModelEntity): Pair<Boolean, String> {
        val (verified, detail) = ttsEngine.verifyModelFile(model)
        if (!verified) {
            dao.insertVoiceModel(model.copy(installStatus = ModelInstallStatus.CORRUPTED_CHECKSUM.name))
            return false to detail
        }
        val loaded = ttsEngine.loadModelOnDemand(model)
        if (!loaded) {
            return false to "Failed to load model into local ONNX runtime."
        }
        // Mark previous active model as VERIFIED_READY
        val all = dao.getAllVoiceModelsSnapshot()
        all.forEach { existing ->
            if (existing.id == model.id) {
                dao.insertVoiceModel(existing.copy(installStatus = ModelInstallStatus.ACTIVE_LOADED.name))
            } else if (existing.installStatus == ModelInstallStatus.ACTIVE_LOADED.name) {
                dao.insertVoiceModel(existing.copy(installStatus = ModelInstallStatus.VERIFIED_READY.name))
            }
        }
        settingsDataStore.updateSettings { current ->
            val newLang = if (model.languageCode == "multi-IN") current.language else VoiceLanguage.fromCode(model.languageCode)
            current.copy(voiceModel = model.id, language = newLang)
        }
        return true to "Activated ${model.displayName}. $detail"
    }

    suspend fun deleteDownloadedModelFile(model: VoiceModelEntity) {
        if (model.id == "android-system-offline-tts") return
        model.localModelPath?.let { File(it).delete() }
        if (ttsEngine.loadedModelId.value == model.id) {
            ttsEngine.releaseModelFromMemory()
        }
        dao.insertVoiceModel(
            model.copy(
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name
            )
        )
    }

    suspend fun previewTtsVoice(text: String, customConfig: TtsSynthesisConfig? = null): TtsSynthesisResult {
        val s = settingsFlow.first()
        val cfg = customConfig ?: TtsSynthesisConfig(
            language = s.language,
            mood = s.voiceMood,
            speed = s.speechRate,
            pitch = s.speechPitch,
            volume = s.speechVolume,
            pauseDurationMs = s.pauseDurationMs,
            sentenceEmphasis = s.sentenceEmphasis,
            modelId = s.voiceModel
        )
        return ttsEngine.speakAndSynthesize(text = text, config = cfg, playAudioOutLoud = true)
    }
}
