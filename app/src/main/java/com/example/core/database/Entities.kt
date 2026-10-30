package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_messages")
data class VoiceMessageEntity(
    @PrimaryKey val id: String,
    val callerNumber: String,
    val contactName: String?,
    val callDateMillis: Long,
    val callDurationSeconds: Int,
    val messageDurationSeconds: Int,
    val encryptedFilePath: String?,
    val recordingStatus: String,
    val consentStatus: String,
    val transcription: String?,
    val spamClassification: String,
    val spamScore: Int,
    val callHandlingStatus: String,
    val audioCapabilityStatus: String,
    val isRead: Boolean = false,
    val isImportant: Boolean = false,
    val languageCode: String = "bn-IN",
    val voiceMoodUsed: String = "PROFESSIONAL"
)

@Entity(tableName = "screened_calls")
data class ScreenedCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerNumber: String,
    val contactName: String?,
    val timestampMillis: Long,
    val spamScore: Int,
    val spamClassification: String,
    val handlingStatus: String,
    val reasonSummary: String,
    val verificationStatus: String,
    val wasEmergency: Boolean = false,
    val isRepeatedBurst: Boolean = false
)

@Entity(tableName = "caller_rules")
data class CallerRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String,
    val ruleType: String, // ALLOWLIST, BLOCKLIST, USER_REPORTED_SPAM
    val notes: String,
    val spamWeight: Int = 80,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "greeting_scripts")
data class GreetingScriptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val languageCode: String,
    val scriptText: String,
    val closingText: String,
    val targetAudience: String, // UNKNOWN_CALLERS, KNOWN_CALLERS, ALL_CALLERS
    val lengthMode: String, // LONG, SHORT
    val includesRecordingDisclosure: Boolean = true,
    val requiresExplicitConsent: Boolean = true,
    val isSelectedDefault: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_models")
data class VoiceModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val languageCode: String,
    val languageLabel: String,
    val architecture: String, // Sherpa-ONNX VITS / Piper / System Offline
    val speakerName: String,
    val fileSizeMb: Float,
    val expectedSha256: String,
    val localModelPath: String?,
    val installStatus: String, // NOT_DOWNLOADED, VERIFIED_READY, ACTIVE_LOADED, CORRUPTED_CHECKSUM
    val supportsNativeEmotionTokens: Boolean = false,
    val sampleRateHz: Int = 22050,
    val lastVerifiedMillis: Long? = null
)
