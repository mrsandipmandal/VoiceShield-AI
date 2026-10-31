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
    version = 2,
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
                id = "script_bn_personal_assistant",
                title = "Bengali Automated Personal Assistant (Default)",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                scriptText = "নমস্কার! আমি একটি Automated Personal Assistant। আপনি যাঁর সঙ্গে যোগাযোগ করতে চাইছেন, তিনি এখন ফোন ধরতে পারছেন না। অনুগ্রহ করে আপনার নাম এবং বার্তাটি বলুন।",
                closingText = "ধন্যবাদ। আপনার বার্তাটি সুরক্ষিতভাবে সংরক্ষিত হয়েছে। শুভ দিন!",
                targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.LONG.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = true,
                isSelectedDefault = true
            ),
            GreetingScriptEntity(
                id = "script_bn_disclosure_formal",
                title = "Bengali Formal Receptionist with Consent",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                scriptText = "নমস্কার! আপনি VoiceShield AI-এর মাধ্যমে যোগাযোগ করেছেন। অনুগ্রহ করে আপনার নাম এবং কী কারণে ফোন করেছেন তা বলুন। আপনার বার্তা রেকর্ড করা হতে পারে। সম্মতি থাকলে নির্দেশ অনুসরণ করুন।",
                closingText = "ধন্যবাদ, বার্তা রেকর্ড হয়েছে।",
                targetAudience = GreetingTarget.UNKNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.LONG.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = true,
                isSelectedDefault = false
            ),
            GreetingScriptEntity(
                id = "script_en_default_long",
                title = "English (India) Automated Personal Assistant",
                languageCode = VoiceLanguage.ENGLISH_IN.code,
                scriptText = "Hello! I am an Automated Personal Assistant. The person you are trying to reach is currently unavailable. Your message may be recorded. Please state your name and leave your message.",
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
                scriptText = "Hi! I am currently in a meeting and unable to take your call. Please leave a quick recorded message and I will call you right back.",
                closingText = "Got your message. Talk to you soon!",
                targetAudience = GreetingTarget.KNOWN_CALLERS.name,
                lengthMode = GreetingLengthMode.SHORT.name,
                includesRecordingDisclosure = true,
                requiresExplicitConsent = false,
                isSelectedDefault = false
            ),
            GreetingScriptEntity(
                id = "script_hi_default_long",
                title = "Hindi (India) Automated Personal Assistant",
                languageCode = VoiceLanguage.HINDI_IN.code,
                scriptText = "नमस्ते! मैं एक ऑटोमेटेड पर्सनल असिस्टेंट हूँ। आप जिनसे संपर्क करना चाहते हैं वे अभी कॉल नहीं उठा पा रहे हैं। आपका संदेश रिकॉर्ड किया जा सकता है। कृपया अपना नाम और संदेश बताएं।",
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
                id = "vits-piper-bn_IN-mitra-low",
                displayName = "Sherpa-ONNX Bengali — Mitra (Female)",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                languageLabel = "Bengali (India / West Bengal)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Mitra • Female (bn-IN)",
                fileSizeMb = 18.2f,
                expectedSha256 = "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 16000
            ),
            VoiceModelEntity(
                id = "vits-piper-bn_IN-arjun-medium",
                displayName = "Sherpa-ONNX Bengali — Arjun (Male)",
                languageCode = VoiceLanguage.BENGALI_IN.code,
                languageLabel = "Bengali (India / West Bengal)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Arjun • Male (bn-IN)",
                fileSizeMb = 42.5f,
                expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 22050
            ),
            VoiceModelEntity(
                id = "vits-piper-en_IN-kavya-medium",
                displayName = "Sherpa-ONNX English India — Kavya (Female)",
                languageCode = VoiceLanguage.ENGLISH_IN.code,
                languageLabel = "English (India)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Kavya • Female (en-IN)",
                fileSizeMb = 46.0f,
                expectedSha256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
                localModelPath = null,
                installStatus = ModelInstallStatus.NOT_DOWNLOADED.name,
                supportsNativeEmotionTokens = false,
                sampleRateHz = 22050
            ),
            VoiceModelEntity(
                id = "vits-piper-hi_IN-rohan-medium",
                displayName = "Sherpa-ONNX Hindi India — Rohan (Male)",
                languageCode = VoiceLanguage.HINDI_IN.code,
                languageLabel = "Hindi (India)",
                architecture = "Sherpa-ONNX VITS-Piper (int8)",
                speakerName = "Rohan • Male (hi-IN)",
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
                speakerName = "Multi-Locale Female/Male Voice",
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
