package com.example

import android.app.Application
import com.example.ai.capability.CallAudioCapabilityManager
import com.example.ai.stt.LocalSttTranscriber
import com.example.ai.tts.LocalTtsEngine
import com.example.core.audio.EncryptedAudioPlayerAndRecorder
import com.example.core.database.VoiceShieldDatabase
import com.example.core.notifications.VoiceShieldNotificationManager
import com.example.core.preferences.SettingsDataStore
import com.example.core.security.KeystoreCryptoManager
import com.example.data.repository.VoiceShieldRepository
import com.example.domain.usecase.SpamAndScreeningEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceShieldApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: VoiceShieldRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = VoiceShieldDatabase.getInstance(this)
        val settingsDataStore = SettingsDataStore(this)
        val cryptoManager = KeystoreCryptoManager()
        val capabilityManager = CallAudioCapabilityManager(this)
        val ttsEngine = LocalTtsEngine(this)
        val sttTranscriber = LocalSttTranscriber()
        val audioController = EncryptedAudioPlayerAndRecorder(this, cryptoManager)
        val notificationManager = VoiceShieldNotificationManager(this)
        val screeningEngine = SpamAndScreeningEngine(this)

        repository = VoiceShieldRepository(
            context = this,
            dao = database.voiceShieldDao(),
            settingsDataStore = settingsDataStore,
            cryptoManager = cryptoManager,
            capabilityManager = capabilityManager,
            ttsEngine = ttsEngine,
            sttTranscriber = sttTranscriber,
            audioController = audioController,
            notificationManager = notificationManager,
            screeningEngine = screeningEngine
        )

        appScope.launch {
            repository.initializeDefaultsIfEmpty()
        }
    }
}
