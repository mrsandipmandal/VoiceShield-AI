package com.example

import android.content.Context
import android.telecom.Call
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.ai.capability.CallAudioCapabilityManager
import com.example.ai.stt.LocalSttTranscriber
import com.example.ai.tts.LocalTtsEngine
import com.example.core.database.VoiceMessageEntity
import com.example.core.database.VoiceShieldDatabase
import com.example.core.notifications.VoiceShieldNotificationManager
import com.example.core.preferences.SettingsDataStore
import com.example.domain.model.CapabilitySupportStatus
import com.example.domain.model.FallbackCallAction
import com.example.domain.model.RecordingStatus
import com.example.domain.model.UnknownNumberAction
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceMood
import com.example.telecom.incall.VoiceShieldInCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameResource_matchesVoiceShieldAI() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoiceShield AI", appName)
    }

    @Test
    fun settingsPersistence_savesAndRestoresAll24ConfigurableVariables() {
        runBlocking {
            val tempDir = Files.createTempDirectory("datastore_test").toFile()
            val testDataStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { File(tempDir, "test_settings.preferences_pb") }
            )
            val settingsStore = SettingsDataStore(testDataStore)

            settingsStore.updateSettings { current ->
                current.copy(
                    autoAnswerEnabled = false,
                    answerDelaySeconds = 7,
                    greetingEnabled = true,
                    greetingText = "Custom Bengali Greeting",
                    language = VoiceLanguage.HINDI_IN,
                    voiceModel = "vits-piper-hi_IN-rohan-medium",
                    voiceMood = VoiceMood.EMPATHETIC,
                    speechRate = 1.15f,
                    speechPitch = 0.92f,
                    speechVolume = 0.85f,
                    maxRecordingDuration = 120,
                    silenceTimeout = 8,
                    maxCallDuration = 180,
                    repeatGreetingCount = 2,
                    recordingDisclosureEnabled = true,
                    callerConsentRequired = false,
                    knownContactsBypass = false,
                    unknownNumberAction = UnknownNumberAction.SILENCE,
                    spamThreshold = 75,
                    notificationEnabled = true,
                    messageAutoDeleteDays = 14,
                    workingHoursEnabled = true,
                    fallbackCallAction = FallbackCallAction.ALLOW_NORMAL_RING,
                    maximumStoredMessages = 250
                )
            }

            val loaded = settingsStore.settingsFlow.first()
            assertFalse(loaded.autoAnswerEnabled)
            assertEquals(7, loaded.answerDelaySeconds)
            assertEquals("Custom Bengali Greeting", loaded.greetingText)
            assertEquals(VoiceLanguage.HINDI_IN, loaded.language)
            assertEquals("vits-piper-hi_IN-rohan-medium", loaded.voiceModel)
            assertEquals(VoiceMood.EMPATHETIC, loaded.voiceMood)
            assertEquals(1.15f, loaded.speechRate, 0.01f)
            assertEquals(0.92f, loaded.speechPitch, 0.01f)
            assertEquals(0.85f, loaded.speechVolume, 0.01f)
            assertEquals(120, loaded.maxRecordingDuration)
            assertEquals(8, loaded.silenceTimeout)
            assertEquals(180, loaded.maxCallDuration)
            assertEquals(2, loaded.repeatGreetingCount)
            assertFalse(loaded.callerConsentRequired)
            assertFalse(loaded.knownContactsBypass)
            assertEquals(UnknownNumberAction.SILENCE, loaded.unknownNumberAction)
            assertEquals(75, loaded.spamThreshold)
            assertEquals(14, loaded.messageAutoDeleteDays)
            assertTrue(loaded.workingHoursEnabled)
            assertEquals(FallbackCallAction.ALLOW_NORMAL_RING, loaded.fallbackCallAction)
            assertEquals(250, loaded.maximumStoredMessages)

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun localTtsEngine_provisionsVerifiesAndSynthesizesValid16kHzPcmWav() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val ttsEngine = LocalTtsEngine(context)

            val modelCatalog = VoiceShieldDatabase.defaultVoiceModels()
            val bnModel = modelCatalog.first { it.id == "vits-piper-bn_IN-arjun-medium" }

            val (beforeOk, _) = ttsEngine.verifyModelFile(bnModel)
            assertFalse(beforeOk)

            val provisioned = ttsEngine.provisionLocalOnnxModelBundle(bnModel)
            val (afterOk, detail) = ttsEngine.verifyModelFile(provisioned)
            assertTrue(detail, afterOk)
            assertTrue(ttsEngine.loadModelOnDemand(provisioned))

            val wavBytes = ttsEngine.generateExpressiveOfflineWav(
                text = "নমস্কার! আপনি VoiceShield AI-এর মাধ্যমে যোগাযোগ করেছেন।",
                speed = 1.0f,
                pitch = 1.0f,
                volume = 0.9f,
                pauseMs = 300,
                emphasis = 0.7f
            )
            assertTrue(wavBytes.size > 1000)
            assertEquals("RIFF", String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals("WAVE", String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII))

            val stt = LocalSttTranscriber()
            val analysis = stt.analyzeWavBuffer(wavBytes)
            assertTrue(analysis.hasVoiceActivity)
            assertTrue(analysis.durationSeconds > 0.5f)
        }
    }

    @Test
    fun audioCapabilityManager_distinguishesAll5CapabilitiesAndReportsCellularDownlinkUnsupported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val capManager = CallAudioCapabilityManager(context)
        val caps = capManager.evaluateCapabilities(isLocalTtsReady = true)

        assertEquals(CapabilitySupportStatus.SUPPORTED, caps.localTtsGenerationSupported)
        assertEquals(CapabilitySupportStatus.UNSUPPORTED, caps.cellularTtsUplinkSupported)
        assertEquals(CapabilitySupportStatus.UNSUPPORTED, caps.cellularRemoteRecordingSupported)
        assertTrue(capManager.getSipVoipArchitectureExplanation().contains("SIP"))
    }

    @Test
    fun roomDatabaseAndNotificationManager_blocksNotificationsForUnrecordedFallbackEntries() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = VoiceShieldDatabase.buildInMemory(context)
            val dao = db.voiceShieldDao()
            val notifManager = VoiceShieldNotificationManager(context)

            val unrecordedFallbackMsg = VoiceMessageEntity(
                id = "msg_unrecorded_01",
                callerNumber = "+919830000111",
                contactName = null,
                callDateMillis = System.currentTimeMillis(),
                callDurationSeconds = 5,
                messageDurationSeconds = 0,
                encryptedFilePath = null,
                recordingStatus = RecordingStatus.UNSUPPORTED_CELLULAR_AUDIO.name,
                consentStatus = "NOT_APPLICABLE",
                transcription = null,
                spamClassification = "LOW_RISK",
                spamScore = 15,
                callHandlingStatus = "RECEPTIONIST_FALLBACK",
                audioCapabilityStatus = "Unsupported Cellular Audio"
            )
            dao.insertMessage(unrecordedFallbackMsg)

            assertFalse(notifManager.canSendVoiceMessageNotification(unrecordedFallbackMsg, true))

            val tempEnc = File(context.cacheDir, "real_recorded_msg.wav.enc").apply {
                writeBytes(ByteArray(256) { 0x42 })
            }
            val recordedMsg = unrecordedFallbackMsg.copy(
                id = "msg_recorded_02",
                encryptedFilePath = tempEnc.absolutePath,
                recordingStatus = RecordingStatus.RECORDED_ENCRYPTED.name,
                messageDurationSeconds = 12
            )
            dao.insertMessage(recordedMsg)
            assertTrue(notifManager.canSendVoiceMessageNotification(recordedMsg, true))

            assertNotNull(dao.getMessageById("msg_recorded_02"))
            dao.deleteMessageById("msg_recorded_02")
            assertEquals(1, dao.getRecentMessages(10).size)

            tempEnc.delete()
            db.close()
        }
    }

    @Test
    fun callLifecycleStateLabels_mapTelecomStatesAccurately() {
        assertEquals("Incoming Ringing", VoiceShieldInCallService.stateToLabel(Call.STATE_RINGING))
        assertEquals("Connected / Active", VoiceShieldInCallService.stateToLabel(Call.STATE_ACTIVE))
        assertEquals("Disconnected", VoiceShieldInCallService.stateToLabel(Call.STATE_DISCONNECTED))
    }

    @Test
    fun sipUserAgentAndG711RtpEngine_parsesSipInviteAndTranscodesTtsToPcmuFrames() {
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val ttsEngine = LocalTtsEngine(context)
            val wav16k = ttsEngine.generateExpressiveOfflineWav(
                text = "নমস্কার! আমি একটি Automated Personal Assistant।",
                speed = 1.0f,
                pitch = 1.0f,
                volume = 0.9f,
                pauseMs = 250,
                emphasis = 0.6f
            )
            val ulaw8k = com.example.telecom.sip.G711Codec.encodeWav16kToPcmu8k(wav16k)
            assertTrue(ulaw8k.size > 1600)
            val (decodedPcm16k, amp) = com.example.telecom.sip.G711Codec.decodePcmu8kToPcm16k(ulaw8k)
            assertTrue(decodedPcm16k.size > 3200)
            assertTrue(amp > 0.01f)

            val rawInvite = buildString {
                append("INVITE sip:voiceshield@127.0.0.1:5060 SIP/2.0\r\n")
                append("Via: SIP/2.0/UDP 127.0.0.1:5090;branch=z9hG4bK-test123\r\n")
                append("From: \"Courier\" <sip:+919831099887@127.0.0.1>;tag=from1\r\n")
                append("To: <sip:voiceshield@127.0.0.1>\r\n")
                append("Call-ID: call-test-999@127.0.0.1\r\n")
                append("CSeq: 1 INVITE\r\n")
                append("Content-Type: application/sdp\r\n\r\n")
                append("v=0\r\n")
                append("c=IN IP4 127.0.0.1\r\n")
                append("m=audio 40100 RTP/AVP 0 101\r\n")
            }
            val lines = rawInvite.split("\r\n", "\n")
            val invite = com.example.telecom.sip.SipUserAgentClient.parseIncomingInvite(
                lines = lines,
                rawSip = rawInvite,
                senderAddress = java.net.InetAddress.getByName("127.0.0.1"),
                senderPort = 5090
            )
            assertNotNull(invite)
            assertEquals("call-test-999@127.0.0.1", invite!!.callId)
            assertEquals("+919831099887", invite.callerNumberOrId)
            assertEquals("Courier", invite.callerDisplayName)
            assertEquals(40100, invite.remoteRtpPort)
        }
    }
}
