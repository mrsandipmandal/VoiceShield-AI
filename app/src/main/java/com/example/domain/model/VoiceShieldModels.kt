package com.example.domain.model

/**
 * Capability support status labels as required by the specification:
 * Supported, Limited, and Unsupported.
 */
enum class CapabilitySupportStatus(val label: String) {
    SUPPORTED("Supported"),
    LIMITED("Limited"),
    UNSUPPORTED("Unsupported")
}

/**
 * 12 Selectable Voice Mood Presets with prosody parameters and synthesis metadata.
 */
enum class VoiceMood(
    val displayName: String,
    val description: String,
    val defaultSpeed: Float,
    val defaultPitch: Float,
    val defaultVolume: Float,
    val defaultPauseMs: Int,
    val emphasisLevel: Float,
    val greetingPrefixStyle: String
) {
    PROFESSIONAL(
        displayName = "Professional",
        description = "Clear, measured, executive receptionist cadence",
        defaultSpeed = 1.0f,
        defaultPitch = 0.98f,
        defaultVolume = 0.95f,
        defaultPauseMs = 350,
        emphasisLevel = 0.65f,
        greetingPrefixStyle = "Formal Executive"
    ),
    POLITE(
        displayName = "Polite",
        description = "Courteous, respectful, slightly elevated warmth",
        defaultSpeed = 0.96f,
        defaultPitch = 1.04f,
        defaultVolume = 0.90f,
        defaultPauseMs = 400,
        emphasisLevel = 0.55f,
        greetingPrefixStyle = "Courteous"
    ),
    FRIENDLY(
        displayName = "Friendly",
        description = "Warm, welcoming, conversational rhythm",
        defaultSpeed = 1.04f,
        defaultPitch = 1.08f,
        defaultVolume = 0.95f,
        defaultPauseMs = 280,
        emphasisLevel = 0.70f,
        greetingPrefixStyle = "Warm Conversational"
    ),
    CALM(
        displayName = "Calm",
        description = "Soothing, unhurried, steady low-stress tone",
        defaultSpeed = 0.88f,
        defaultPitch = 0.94f,
        defaultVolume = 0.85f,
        defaultPauseMs = 500,
        emphasisLevel = 0.40f,
        greetingPrefixStyle = "Unhurried & Reassuring"
    ),
    SERIOUS(
        displayName = "Serious",
        description = "Direct, grounded, no-nonsense screening delivery",
        defaultSpeed = 0.94f,
        defaultPitch = 0.90f,
        defaultVolume = 0.95f,
        defaultPauseMs = 380,
        emphasisLevel = 0.75f,
        greetingPrefixStyle = "Direct & Grounded"
    ),
    FIRM(
        displayName = "Firm",
        description = "Authoritative boundary-setting voice for unknown callers",
        defaultSpeed = 0.95f,
        defaultPitch = 0.88f,
        defaultVolume = 1.0f,
        defaultPauseMs = 320,
        emphasisLevel = 0.85f,
        greetingPrefixStyle = "Authoritative"
    ),
    CHEERFUL(
        displayName = "Cheerful",
        description = "Upbeat, energetic, bright inflection",
        defaultSpeed = 1.10f,
        defaultPitch = 1.14f,
        defaultVolume = 0.98f,
        defaultPauseMs = 240,
        emphasisLevel = 0.80f,
        greetingPrefixStyle = "Bright & Upbeat"
    ),
    EMPATHETIC(
        displayName = "Empathetic",
        description = "Gentle, patient, supportive cadence with longer pauses",
        defaultSpeed = 0.90f,
        defaultPitch = 1.02f,
        defaultVolume = 0.88f,
        defaultPauseMs = 460,
        emphasisLevel = 0.50f,
        greetingPrefixStyle = "Supportive & Patient"
    ),
    NEUTRAL(
        displayName = "Neutral",
        description = "Uncolored flat reference synthesis without prosody shift",
        defaultSpeed = 1.0f,
        defaultPitch = 1.0f,
        defaultVolume = 0.92f,
        defaultPauseMs = 300,
        emphasisLevel = 0.50f,
        greetingPrefixStyle = "Standard Automated"
    ),
    URGENT(
        displayName = "Urgent",
        description = "Brisk, high-priority alert pacing with crisp articulation",
        defaultSpeed = 1.16f,
        defaultPitch = 1.06f,
        defaultVolume = 1.0f,
        defaultPauseMs = 180,
        emphasisLevel = 0.90f,
        greetingPrefixStyle = "Priority Notice"
    ),
    SOFT(
        displayName = "Soft",
        description = "Quiet, gentle low-volume voice for late-night screening",
        defaultSpeed = 0.86f,
        defaultPitch = 1.03f,
        defaultVolume = 0.72f,
        defaultPauseMs = 520,
        emphasisLevel = 0.35f,
        greetingPrefixStyle = "Quiet Hours"
    ),
    STRICT(
        displayName = "Strict",
        description = "Uncompromising security gatekeeper tone for suspected spam",
        defaultSpeed = 0.92f,
        defaultPitch = 0.85f,
        defaultVolume = 1.0f,
        defaultPauseMs = 260,
        emphasisLevel = 0.95f,
        greetingPrefixStyle = "Security Gatekeeper"
    )
}

enum class VoiceLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val regionLabel: String
) {
    BENGALI_IN("bn-IN", "Bengali (India)", "বাংলা (ভারত / পশ্চিমবঙ্গ)", "India / West Bengal"),
    ENGLISH_IN("en-IN", "English (India)", "English (India)", "India"),
    HINDI_IN("hi-IN", "Hindi (India)", "हिन्दी (भारत)", "India");

    companion object {
        fun fromCode(code: String): VoiceLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: BENGALI_IN
    }
}

enum class UnknownNumberAction(val displayName: String, val description: String) {
    RECEPTIONIST("AI Voice Receptionist", "Answer (when supported), play greeting, and record message"),
    ALLOW("Allow Normal Ring", "Let unknown calls ring normally after spam check"),
    SILENCE("Silence Ringtone", "Allow the call to proceed silently without ringing"),
    REJECT("Reject Immediately", "Disconnect unknown numbers automatically")
}

enum class FallbackCallAction(val displayName: String, val description: String) {
    SILENCE_AND_NOTIFY("Silence & Log Alert", "Silence the ringer and log an unrecorded call notice"),
    ALLOW_NORMAL_RING("Ring Normally", "Fall back to standard phone ringing so you can answer manually"),
    REJECT_WITH_LOG("Reject & Log", "Reject the call and save a screening history entry"),
    ACOUSTIC_SPEAKER_BRIDGE("Acoustic Speaker Mode", "Play TTS over speaker and capture ambient audio if legal")
}

enum class SpamClassification(val label: String) {
    CLEAN("Verified / Clean"),
    LOW_RISK("Unknown / Low Risk"),
    SUSPICIOUS("Suspicious Pattern"),
    HIGH_RISK_SPAM("High-Risk Spam"),
    BLOCKLISTED("User Blocklisted"),
    ALLOWLISTED("User Allowlisted")
}

enum class RecordingStatus(val label: String) {
    RECORDED_ENCRYPTED("Encrypted Recording Saved"),
    UNSUPPORTED_CELLULAR_AUDIO("Unsupported Cellular Audio (Not Recorded)"),
    CALLER_DECLINED_CONSENT("Caller Declined Consent"),
    SILENCE_TIMEOUT_EMPTY("Ended on Silence (No Speech)"),
    RECORDING_FAILED("Recording Error")
}

enum class ConsentStatus(val label: String) {
    CONSENT_GRANTED("Consent Granted"),
    DISCLOSURE_PLAYED_IMPLICIT("Disclosure Played (Implicit)"),
    CONSENT_DECLINED("Consent Declined"),
    NOT_APPLICABLE("Not Applicable")
}

enum class CallHandlingStatus(val label: String) {
    RECEPTIONIST_COMPLETED("Handled by Voice Receptionist"),
    RECEPTIONIST_FALLBACK("Receptionist Fallback (Hardware Limit)"),
    SCREENED_ALLOWED("Screened & Allowed"),
    SCREENED_SILENCED("Screened & Silenced"),
    SCREENED_REJECTED("Screened & Blocked"),
    BYPASSED_KNOWN_CONTACT("Known Contact Bypassed"),
    EMERGENCY_BYPASS("Emergency Number Bypassed")
}

enum class CallerRuleType {
    ALLOWLIST,
    BLOCKLIST,
    USER_REPORTED_SPAM
}

enum class GreetingTarget(val label: String) {
    UNKNOWN_CALLERS("Unknown Callers"),
    KNOWN_CALLERS("Known Contacts"),
    ALL_CALLERS("All Callers")
}

enum class GreetingLengthMode(val label: String) {
    LONG("Long (Detailed with Consent)"),
    SHORT("Short (Concise)")
}

enum class ModelInstallStatus(val label: String) {
    NOT_DOWNLOADED("Not Downloaded"),
    DOWNLOADING("Downloading..."),
    VERIFIED_READY("Verified & Ready"),
    ACTIVE_LOADED("Active in Engine"),
    CORRUPTED_CHECKSUM("Verification Failed"),
    UNAVAILABLE_FOR_DEVICE("Unavailable")
}

/**
 * 5 Distinct Device Audio & Telecom Capabilities evaluated by CallAudioCapabilityManager.
 */
data class DeviceAudioCapabilities(
    val callScreeningSupported: CapabilitySupportStatus,
    val callScreeningDetail: String,
    val callAnswerDisconnectSupported: CapabilitySupportStatus,
    val callAnswerDisconnectDetail: String,
    val localTtsGenerationSupported: CapabilitySupportStatus,
    val localTtsGenerationDetail: String,
    val cellularTtsUplinkSupported: CapabilitySupportStatus,
    val cellularTtsUplinkDetail: String,
    val cellularRemoteRecordingSupported: CapabilitySupportStatus,
    val cellularRemoteRecordingDetail: String,
    val isDefaultCallScreener: Boolean,
    val isDefaultDialer: Boolean,
    val hasContactsPermission: Boolean,
    val hasRecordAudioPermission: Boolean,
    val hasPhoneStatePermission: Boolean,
    val hasPostNotificationsPermission: Boolean,
    val hasAnswerCallsPermission: Boolean
)

/**
 * All 24+ Configurable Settings persisted via Jetpack DataStore.
 */
data class VoiceShieldSettings(
    val protectionEnabled: Boolean = true,
    val autoAnswerEnabled: Boolean = true,
    val answerDelaySeconds: Int = 3,
    val greetingEnabled: Boolean = true,
    val greetingText: String = "নমস্কার! আপনি VoiceShield AI-এর মাধ্যমে যোগাযোগ করেছেন। অনুগ্রহ করে আপনার নাম এবং কী কারণে ফোন করেছেন তা বলুন। আপনার বার্তা রেকর্ড করা হতে পারে। সম্মতি থাকলে নির্দেশ অনুসরণ করুন।",
    val knownCallerGreetingText: String = "নমস্কার! আমি এখন একটু ব্যস্ত আছি। অনুগ্রহ করে আপনার বার্তাটি বলুন, আমি শীঘ্রই কল ব্যাক করব।",
    val closingMessageText: String = "ধন্যবাদ। আপনার বার্তাটি সংরক্ষিত হয়েছে। শুভ দিন!",
    val greetingLengthMode: GreetingLengthMode = GreetingLengthMode.LONG,
    val language: VoiceLanguage = VoiceLanguage.BENGALI_IN,
    val voiceModel: String = "vits-piper-bn_IN-arjun-medium",
    val voiceMood: VoiceMood = VoiceMood.PROFESSIONAL,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 0.98f,
    val speechVolume: Float = 0.95f,
    val pauseDurationMs: Int = 350,
    val sentenceEmphasis: Float = 0.65f,
    val maxRecordingDuration: Int = 60,
    val silenceTimeout: Int = 5,
    val maxCallDuration: Int = 90,
    val repeatGreetingCount: Int = 1,
    val recordingDisclosureEnabled: Boolean = true,
    val callerConsentRequired: Boolean = true,
    val knownContactsBypass: Boolean = true,
    val unknownNumberAction: UnknownNumberAction = UnknownNumberAction.RECEPTIONIST,
    val spamThreshold: Int = 65,
    val notificationEnabled: Boolean = true,
    val messageAutoDeleteDays: Int = 30,
    val workingHoursEnabled: Boolean = false,
    val workingHoursStartHour: Int = 9,
    val workingHoursEndHour: Int = 18,
    val fallbackCallAction: FallbackCallAction = FallbackCallAction.SILENCE_AND_NOTIFY,
    val maximumStoredMessages: Int = 100,
    val darkThemeEnabled: Boolean = true
)

/**
 * Interactive Receptionist State Machine states.
 */
enum class ReceptionistStage(val stepTitle: String) {
    IDLE("Ready / Standby"),
    SCREENING_CALLER("1. Screening Caller & Spam Rules"),
    WAITING_ANSWER_DELAY("2. Waiting Auto-Answer Delay"),
    ANSWERING_CALL("3. Answering Call"),
    PLAYING_TTS_GREETING("4. Playing Local TTS Greeting & Disclosure"),
    AWAITING_CALLER_CONSENT("5. Verifying Caller Consent"),
    RECORDING_CALLER_AUDIO("6. Recording Caller Voice Message"),
    PLAYING_CLOSING_SCRIPT("7. Playing Closing Message"),
    ENCRYPTING_AND_SAVING("8. AES-GCM Encrypting & Saving to Room"),
    COMPLETED_SUCCESS("9. Call Disconnected & Notification Sent"),
    FALLBACK_UNSUPPORTED("Hardware Audio Limitation Fallback Applied"),
    CALL_REJECTED_OR_BYPASSED("Call Handled by Screening Policy")
}

data class ReceptionistSessionState(
    val isActive: Boolean = false,
    val callerNumber: String = "",
    val callerName: String? = null,
    val isSimulatedCall: Boolean = true,
    val stage: ReceptionistStage = ReceptionistStage.IDLE,
    val elapsedSeconds: Int = 0,
    val silenceSeconds: Int = 0,
    val currentAmplitude: Float = 0f,
    val spamScore: Int = 0,
    val spamClassification: SpamClassification = SpamClassification.LOW_RISK,
    val statusLog: List<String> = emptyList(),
    val activeGreetingText: String = "",
    val consentGranted: Boolean = false,
    val recordedMessageId: String? = null,
    val capabilityWarning: String? = null
)
