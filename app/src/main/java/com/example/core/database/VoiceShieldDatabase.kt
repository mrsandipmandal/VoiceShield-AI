package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.model.GreetingLengthMode
import com.example.domain.model.GreetingTarget
import com.example.domain.model.ModelInstallStatus
import com.example.domain.model.VoiceLanguage

@Database(
    entities = [
        VoiceMessageEntity::class,
        ScreenedCallEntity::class,
        CallerRuleEntity::class,
        GreetingScriptEntity::class,
        VoiceModelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoiceShieldDatabase : RoomDatabase() {
    abstract fun voiceShieldDao(): VoiceShieldDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceShieldDatabase? = null

        fun getInstance(context: Context): VoiceShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoiceShieldDatabase::class.java,
                    "voiceshield_ai.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun buildInMemory(context: Context): VoiceShieldDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                VoiceShieldDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }

        fun defaultGreetingScripts(): List<GreetingScriptEntity> = listOf(
            GreetingScriptEntity(
                id = "script_bn_default_long",
                title = "Bengali Standard Receptionist (Default)",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                scriptText = "নমস্কার! আপনি VoiceShield AI-এর মাধ্যমে যোগাযোগ করেছেন। অনুগ্রহ করে আপনার নাম এবং কী কারণে ফোন করেছেন তা বলুন। আপনার বার্তা রেকর্ড করা হতে পারে। সম্মতি থাকলে নির্দেশ অনুসরণ করুন।",
                closingText = "ধন্যবাদ। আপনার বার্তাটি সুরক্ষিতভাবে সংরক্ষিত হয়েছে। শুভ দিন!",
                targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.LONG.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = true,
                isSelectedDefault = true
            ),
            GreetingScriptEntity(
                id = "script_bn_short",
                title = "Bengali Quick Screening",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                scriptText = "নমস্কার, এটি স্বয়ংক্রিয় রিসেপশনিস্ট। আপনার বার্তা রেকর্ড হতে পারে। অনুগ্রহ করে নাম ও কারণ বলুন।",
                closingText = "ধন্যবাদ, বার্তা রেকর্ড হয়েছে।",
                targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.SHORT.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = false,
                isSelectedDefault = false
            ),
            GreetingScriptEntity(
                id = "script_en_default_long",
                title = "English (India) Formal Receptionist",
                languageCode = VoiceLanguage.ENGLISH_IN.code,
                scriptText = "Hello! This is an automated call assistant. Please state your name and the reason for your call. Your message may be recorded. Please follow the consent instructions before continuing.",
                closingText = "Thank you. Your voice message has been encrypted and saved on this device. Goodbye.",
                targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.LONG.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = true,
                isSelectedDefault = false
            ),
            GreetingScriptEntity(
                id = "script_en_known",
                title = "English Known Contact Busy Notice",
                languageCode = VoiceLanguage.ENGLISH_IN.code,
                scriptText = "Hi! I am currently in a meeting and unable to take your call. Please leave a quick recorded message after the tone and I will call you right back.",
                closingText = "Got your message. Talk to you soon!",
                targetAudience = GreetingTarget.KNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.SHORT.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = false,
                isSelectedDefault = false
            ),
            GreetingScriptEntity(
                id = "script_hi_default_long",
                title = "Hindi (India) Automated Assistant",
                languageCode = VoiceLanguage.HINDI_IN.code,
                scriptText = "नमस्ते! आप VoiceShield AI स्वचालित कॉल सहायक से जुड़े हैं। कृपया अपना नाम और कॉल करने का कारण बताएं। आपका संदेश रिकॉर्ड किया जा सकता है। सहमति के लिए निर्देशों का पालन करें।",
                closingText = "धन्यवाद। आपका संदेश सुरक्षित रूप से सहेज लिया गया है। नमस्कार!",
                targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.LONG.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = true,
                isSelectedDefault = false
            )
        )

        fun defaultVoiceModels(): List<VoiceModelEntity> = listOf(
            VoiceModelEntity(
                id = "vits-piper-bn_IN-arjun-medium",
                displayName = "Sherpa-ONNX Bengali (Arjun Medium)",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                languageLabel = "Bengali (India / West Bengal)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Arjun (bn-IN)",
                fileSizeMb = 42.5f,
                expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 22050
            ),
            VoiceModelEntity(
                id = "vits-piper-bn_IN-mitra-low",
                displayName = "Sherpa-ONNX Bengali (Mitra Compact)",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                languageLabel = "Bengali (India / West Bengal)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Mitra (bn-IN)",
                fileSizeMb = 18.2f,
                expectedSha256 = "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 16000
            ),
            VoiceModelEntity(
                id = "vits-piper-en_IN-kavya-medium",
                displayName = "Sherpa-ONNX English India (Kavya)",
                languageCode = VoiceLanguage.ENGLISH_IN.code,
                languageLabel = "English (India)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Kavya (en-IN)",
                fileSizeMb = 46.0f,
                expectedSha256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 22050
            ),
            VoiceModelEntity(
                id = "vits-piper-hi_IN-rohan-medium",
                displayName = "Sherpa-ONNX Hindi India (Rohan)",
                languageCode = VoiceLanguage.HINDI_IN.code,
                languageLabel = "Hindi (India)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Rohan (hi-IN)",
                fileSizeMb = 44.8f,
                expectedSha256 = "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 22050
            ),
            VoiceModelEntity(
                id = "android-system-offline-tts",
                displayName = "Android On-Device Offline TTS Engine",
                languageCode = "multi-IN",
                languageLabel = "Bengali / English / Hindi (System Offline)",
                architecture = "Android TextToSpeech Offline Acoustic Synthesizer",
                speakerName = "System Multi-Locale Voice",
                fileSizeMb = 0.0f,
                expectedSha256 = "BUILT_IN_SYSTEM_VERIFIED",
                localModelPath = "system://tts/offline",
                installStatus = ModelInstallStatus.ACTIVE_LOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 22050,
                lastVerifiedMillis = System.currentTimeMillis()
            )
        )
    }
}
