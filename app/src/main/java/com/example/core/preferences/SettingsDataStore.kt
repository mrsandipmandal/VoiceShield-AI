package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.FallbackCallAction
import com.example.domain.model.GreetingLengthMode
import com.example.domain.model.UnknownNumberAction
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceMood
import com.example.domain.model.VoiceShieldSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voiceShieldDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voiceshield_settings"
)

class SettingsDataStore(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.voiceShieldDataStore)

    object Keys {
        val PROTECTION_ENABLED = booleanPreferencesKey("protectionEnabled")
        val AUTO_ANSWER_ENABLED = booleanPreferencesKey("autoAnswerEnabled")
        val ANSWER_DELAY_SECONDS = intPreferencesKey("answerDelaySeconds")
        val GREETING_ENABLED = booleanPreferencesKey("greetingEnabled")
        val GREETING_TEXT = stringPreferencesKey("greetingText")
        val KNOWN_CALLER_GREETING_TEXT = stringPreferencesKey("knownCallerGreetingText")
        val CLOSING_MESSAGE_TEXT = stringPreferencesKey("closingMessageText")
        val GREETING_LENGTH_MODE = stringPreferencesKey("greetingLengthMode")
        val LANGUAGE = stringPreferencesKey("language")
        val VOICE_MODEL = stringPreferencesKey("voiceModel")
        val VOICE_MOOD = stringPreferencesKey("voiceMood")
        val SPEECH_RATE = floatPreferencesKey("speechRate")
        val SPEECH_PITCH = floatPreferencesKey("speechPitch")
        val SPEECH_VOLUME = floatPreferencesKey("speechVolume")
        val PAUSE_DURATION_MS = intPreferencesKey("pauseDurationMs")
        val SENTENCE_EMPHASIS = floatPreferencesKey("sentenceEmphasis")
        val MAX_RECORDING_DURATION = intPreferencesKey("maxRecordingDuration")
        val SILENCE_TIMEOUT = intPreferencesKey("silenceTimeout")
        val MAX_CALL_DURATION = intPreferencesKey("maxCallDuration")
        val REPEAT_GREETING_COUNT = intPreferencesKey("repeatGreetingCount")
        val RECORDING_DISCLOSURE_ENABLED = booleanPreferencesKey("recordingDisclosureEnabled")
        val CALLER_CONSENT_REQUIRED = booleanPreferencesKey("callerConsentRequired")
        val KNOWN_CONTACTS_BYPASS = booleanPreferencesKey("knownContactsBypass")
        val UNKNOWN_NUMBER_ACTION = stringPreferencesKey("unknownNumberAction")
        val SPAM_THRESHOLD = intPreferencesKey("spamThreshold")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notificationEnabled")
        val MESSAGE_AUTO_DELETE_DAYS = intPreferencesKey("messageAutoDeleteDays")
        val WORKING_HOURS_ENABLED = booleanPreferencesKey("workingHoursEnabled")
        val WORKING_HOURS_START = intPreferencesKey("workingHoursStartHour")
        val WORKING_HOURS_END = intPreferencesKey("workingHoursEndHour")
        val FALLBACK_CALL_ACTION = stringPreferencesKey("fallbackCallAction")
        val MAXIMUM_STORED_MESSAGES = intPreferencesKey("maximumStoredMessages")
        val DARK_THEME_ENABLED = booleanPreferencesKey("darkThemeEnabled")
    }

    val settingsFlow: Flow<VoiceShieldSettings> = dataStore.data.map { prefs ->
        val defaults = VoiceShieldSettings()
        VoiceShieldSettings(
            protectionEnabled = prefs[Keys.PROTECTION_ENABLED] ?: defaults.protectionEnabled,
            autoAnswerEnabled = prefs[Keys.AUTO_ANSWER_ENABLED] ?: defaults.autoAnswerEnabled,
            answerDelaySeconds = prefs[Keys.ANSWER_DELAY_SECONDS] ?: defaults.answerDelaySeconds,
            greetingEnabled = prefs[Keys.GREETING_ENABLED] ?: defaults.greetingEnabled,
            greetingText = prefs[Keys.GREETING_TEXT] ?: defaults.greetingText,
            knownCallerGreetingText = prefs[Keys.KNOWN_CALLER_GREETING_TEXT] ?: defaults.knownCallerGreetingText,
            closingMessageText = prefs[Keys.CLOSING_MESSAGE_TEXT] ?: defaults.closingMessageText,
            greetingLengthMode = prefs[Keys.GREETING_LENGTH_MODE]?.let {
                runCatching { GreetingLengthMode.valueOf(it) }.getOrNull()
            } ?: defaults.greetingLengthMode,
            language = prefs[Keys.LANGUAGE]?.let { VoiceLanguage.fromCode(it) } ?: defaults.language,
            voiceModel = prefs[Keys.VOICE_MODEL] ?: defaults.voiceModel,
            voiceMood = prefs[Keys.VOICE_MOOD]?.let {
                runCatching { VoiceMood.valueOf(it) }.getOrNull()
            } ?: defaults.voiceMood,
            speechRate = prefs[Keys.SPEECH_RATE] ?: defaults.speechRate,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: defaults.speechPitch,
            speechVolume = prefs[Keys.SPEECH_VOLUME] ?: defaults.speechVolume,
            pauseDurationMs = prefs[Keys.PAUSE_DURATION_MS] ?: defaults.pauseDurationMs,
            sentenceEmphasis = prefs[Keys.SENTENCE_EMPHASIS] ?: defaults.sentenceEmphasis,
            maxRecordingDuration = prefs[Keys.MAX_RECORDING_DURATION] ?: defaults.maxRecordingDuration,
            silenceTimeout = prefs[Keys.SILENCE_TIMEOUT] ?: defaults.silenceTimeout,
            maxCallDuration = prefs[Keys.MAX_CALL_DURATION] ?: defaults.maxCallDuration,
            repeatGreetingCount = prefs[Keys.REPEAT_GREETING_COUNT] ?: defaults.repeatGreetingCount,
            recordingDisclosureEnabled = prefs[Keys.RECORDING_DISCLOSURE_ENABLED] ?: defaults.recordingDisclosureEnabled,
            callerConsentRequired = prefs[Keys.CALLER_CONSENT_REQUIRED] ?: defaults.callerConsentRequired,
            knownContactsBypass = prefs[Keys.KNOWN_CONTACTS_BYPASS] ?: defaults.knownContactsBypass,
            unknownNumberAction = prefs[Keys.UNKNOWN_NUMBER_ACTION]?.let {
                runCatching { UnknownNumberAction.valueOf(it) }.getOrNull()
            } ?: defaults.unknownNumberAction,
            spamThreshold = prefs[Keys.SPAM_THRESHOLD] ?: defaults.spamThreshold,
            notificationEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: defaults.notificationEnabled,
            messageAutoDeleteDays = prefs[Keys.MESSAGE_AUTO_DELETE_DAYS] ?: defaults.messageAutoDeleteDays,
            workingHoursEnabled = prefs[Keys.WORKING_HOURS_ENABLED] ?: defaults.workingHoursEnabled,
            workingHoursStartHour = prefs[Keys.WORKING_HOURS_START] ?: defaults.workingHoursStartHour,
            workingHoursEndHour = prefs[Keys.WORKING_HOURS_END] ?: defaults.workingHoursEndHour,
            fallbackCallAction = prefs[Keys.FALLBACK_CALL_ACTION]?.let {
                runCatching { FallbackCallAction.valueOf(it) }.getOrNull()
            } ?: defaults.fallbackCallAction,
            maximumStoredMessages = prefs[Keys.MAXIMUM_STORED_MESSAGES] ?: defaults.maximumStoredMessages,
            darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: defaults.darkThemeEnabled
        )
    }

    suspend fun updateSettings(transform: (VoiceShieldSettings) -> VoiceShieldSettings) {
        dataStore.edit { prefs ->
            val current = VoiceShieldSettings(
                protectionEnabled = prefs[Keys.PROTECTION_ENABLED] ?: true,
                autoAnswerEnabled = prefs[Keys.AUTO_ANSWER_ENABLED] ?: true,
                answerDelaySeconds = prefs[Keys.ANSWER_DELAY_SECONDS] ?: 3,
                greetingEnabled = prefs[Keys.GREETING_ENABLED] ?: true,
                greetingText = prefs[Keys.GREETING_TEXT] ?: VoiceShieldSettings().greetingText,
                knownCallerGreetingText = prefs[Keys.KNOWN_CALLER_GREETING_TEXT] ?: VoiceShieldSettings().knownCallerGreetingText,
                closingMessageText = prefs[Keys.CLOSING_MESSAGE_TEXT] ?: VoiceShieldSettings().closingMessageText,
                greetingLengthMode = prefs[Keys.GREETING_LENGTH_MODE]?.let {
                    runCatching { GreetingLengthMode.valueOf(it) }.getOrNull()
                } ?: GreetingLengthMode.LONG,
                language = prefs[Keys.LANGUAGE]?.let { VoiceLanguage.fromCode(it) } ?: VoiceLanguage.BENGALI_IN,
                voiceModel = prefs[Keys.VOICE_MODEL] ?: "vits-piper-bn_IN-arjun-medium",
                voiceMood = prefs[Keys.VOICE_MOOD]?.let {
                    runCatching { VoiceMood.valueOf(it) }.getOrNull()
                } ?: VoiceMood.PROFESSIONAL,
                speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
                speechPitch = prefs[Keys.SPEECH_PITCH] ?: 0.98f,
                speechVolume = prefs[Keys.SPEECH_VOLUME] ?: 0.95f,
                pauseDurationMs = prefs[Keys.PAUSE_DURATION_MS] ?: 350,
                sentenceEmphasis = prefs[Keys.SENTENCE_EMPHASIS] ?: 0.65f,
                maxRecordingDuration = prefs[Keys.MAX_RECORDING_DURATION] ?: 60,
                silenceTimeout = prefs[Keys.SILENCE_TIMEOUT] ?: 5,
                maxCallDuration = prefs[Keys.MAX_CALL_DURATION] ?: 90,
                repeatGreetingCount = prefs[Keys.REPEAT_GREETING_COUNT] ?: 1,
                recordingDisclosureEnabled = prefs[Keys.RECORDING_DISCLOSURE_ENABLED] ?: true,
                callerConsentRequired = prefs[Keys.CALLER_CONSENT_REQUIRED] ?: true,
                knownContactsBypass = prefs[Keys.KNOWN_CONTACTS_BYPASS] ?: true,
                unknownNumberAction = prefs[Keys.UNKNOWN_NUMBER_ACTION]?.let {
                    runCatching { UnknownNumberAction.valueOf(it) }.getOrNull()
                } ?: UnknownNumberAction.RECEPTIONIST,
                spamThreshold = prefs[Keys.SPAM_THRESHOLD] ?: 65,
                notificationEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: true,
                messageAutoDeleteDays = prefs[Keys.MESSAGE_AUTO_DELETE_DAYS] ?: 30,
                workingHoursEnabled = prefs[Keys.WORKING_HOURS_ENABLED] ?: false,
                workingHoursStartHour = prefs[Keys.WORKING_HOURS_START] ?: 9,
                workingHoursEndHour = prefs[Keys.WORKING_HOURS_END] ?: 18,
                fallbackCallAction = prefs[Keys.FALLBACK_CALL_ACTION]?.let {
                    runCatching { FallbackCallAction.valueOf(it) }.getOrNull()
                } ?: FallbackCallAction.SILENCE_AND_NOTIFY,
                maximumStoredMessages = prefs[Keys.MAXIMUM_STORED_MESSAGES] ?: 100,
                darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true
            )
            val updated = transform(current)
            prefs[Keys.PROTECTION_ENABLED] = updated.protectionEnabled
            prefs[Keys.AUTO_ANSWER_ENABLED] = updated.autoAnswerEnabled
            prefs[Keys.ANSWER_DELAY_SECONDS] = updated.answerDelaySeconds.coerceIn(0, 30)
            prefs[Keys.GREETING_ENABLED] = updated.greetingEnabled
            prefs[Keys.GREETING_TEXT] = updated.greetingText
            prefs[Keys.KNOWN_CALLER_GREETING_TEXT] = updated.knownCallerGreetingText
            prefs[Keys.CLOSING_MESSAGE_TEXT] = updated.closingMessageText
            prefs[Keys.GREETING_LENGTH_MODE] = updated.greetingLengthMode.name
            prefs[Keys.LANGUAGE] = updated.language.code
            prefs[Keys.VOICE_MODEL] = updated.voiceModel
            prefs[Keys.VOICE_MOOD] = updated.voiceMood.name
            prefs[Keys.SPEECH_RATE] = updated.speechRate.coerceIn(0.5f, 2.0f)
            prefs[Keys.SPEECH_PITCH] = updated.speechPitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.SPEECH_VOLUME] = updated.speechVolume.coerceIn(0.1f, 1.0f)
            prefs[Keys.PAUSE_DURATION_MS] = updated.pauseDurationMs.coerceIn(0, 2000)
            prefs[Keys.SENTENCE_EMPHASIS] = updated.sentenceEmphasis.coerceIn(0.0f, 1.0f)
            prefs[Keys.MAX_RECORDING_DURATION] = updated.maxRecordingDuration.coerceIn(10, 300)
            prefs[Keys.SILENCE_TIMEOUT] = updated.silenceTimeout.coerceIn(2, 30)
            prefs[Keys.MAX_CALL_DURATION] = updated.maxCallDuration.coerceIn(20, 600)
            prefs[Keys.REPEAT_GREETING_COUNT] = updated.repeatGreetingCount.coerceIn(1, 5)
            prefs[Keys.RECORDING_DISCLOSURE_ENABLED] = updated.recordingDisclosureEnabled
            prefs[Keys.CALLER_CONSENT_REQUIRED] = updated.callerConsentRequired
            prefs[Keys.KNOWN_CONTACTS_BYPASS] = updated.knownContactsBypass
            prefs[Keys.UNKNOWN_NUMBER_ACTION] = updated.unknownNumberAction.name
            prefs[Keys.SPAM_THRESHOLD] = updated.spamThreshold.coerceIn(10, 100)
            prefs[Keys.NOTIFICATION_ENABLED] = updated.notificationEnabled
            prefs[Keys.MESSAGE_AUTO_DELETE_DAYS] = updated.messageAutoDeleteDays.coerceIn(1, 365)
            prefs[Keys.WORKING_HOURS_ENABLED] = updated.workingHoursEnabled
            prefs[Keys.WORKING_HOURS_START] = updated.workingHoursStartHour.coerceIn(0, 23)
            prefs[Keys.WORKING_HOURS_END] = updated.workingHoursEndHour.coerceIn(0, 23)
            prefs[Keys.FALLBACK_CALL_ACTION] = updated.fallbackCallAction.name
            prefs[Keys.MAXIMUM_STORED_MESSAGES] = updated.maximumStoredMessages.coerceIn(5, 1000)
            prefs[Keys.DARK_THEME_ENABLED] = updated.darkThemeEnabled
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }
}
