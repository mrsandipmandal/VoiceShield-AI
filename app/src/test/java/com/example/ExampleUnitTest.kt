package com.example

import android.telecom.Connection
import com.example.core.database.CallerRuleEntity
import com.example.core.database.ScreenedCallEntity
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.CallHandlingStatus
import com.example.domain.model.CallerRuleType
import com.example.domain.model.SpamClassification
import com.example.domain.model.UnknownNumberAction
import com.example.domain.model.VoiceMood
import com.example.domain.model.VoiceShieldSettings
import com.example.domain.usecase.SpamAndScreeningEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    private val engine = SpamAndScreeningEngine(context = null)

    @Test
    fun `1 contact matching and phone normalization equates national and international formats`() {
        assertTrue(SpamAndScreeningEngine.numbersMatch("+91 98300-11223", "9830011223"))
        assertTrue(SpamAndScreeningEngine.numbersMatch("+919830011223", "09830011223"))
        assertFalse(SpamAndScreeningEngine.numbersMatch("+919830011223", "+919830099999"))
    }

    @Test
    fun `2 emergency calls are never blocked or routed to receptionist`() {
        val strictSettings = VoiceShieldSettings(
            unknownNumberAction = UnknownNumberAction.REJECT,
            spamThreshold = 10
        )
        val blockRuleOn112 = listOf(
            CallerRuleEntity(
                phoneNumber = "112",
                displayName = "Accidental Block",
                ruleType = CallerRuleType.BLOCKLIST.name,
                notes = "Should never block emergency"
            )
        )
        val eval112 = engine.evaluateCall(
            rawNumber = "112",
            settings = strictSettings,
            callerRules = blockRuleOn112,
            recentCallsFromNumber = emptyList()
        )
        assertTrue(eval112.isEmergencyNumber)
        assertFalse(eval112.shouldDisallowCall)
        assertFalse(eval112.shouldRejectCall)
        assertFalse(eval112.shouldRouteToReceptionist)
        assertEquals(CallHandlingStatus.EMERGENCY_BYPASS, eval112.handlingStatus)
    }

    @Test
    fun `3 allowlist and saved contacts bypass receptionist while blocklist rejects immediately`() {
        val settings = VoiceShieldSettings(
            knownContactsBypass = true,
            unknownNumberAction = UnknownNumberAction.RECEPTIONIST
        )
        val rules = listOf(
            CallerRuleEntity(
                phoneNumber = "+919830011223",
                displayName = "Ma",
                ruleType = CallerRuleType.ALLOWLIST.name,
                notes = "Trusted"
            ),
            CallerRuleEntity(
                phoneNumber = "+911409988776",
                displayName = "Spammer",
                ruleType = CallerRuleType.BLOCKLIST.name,
                notes = "Blocked"
            )
        )

        val allowResult = engine.evaluateCall(
            rawNumber = "9830011223",
            settings = settings,
            callerRules = rules,
            recentCallsFromNumber = emptyList()
        )
        assertTrue(allowResult.isAllowlisted)
        assertFalse(allowResult.shouldRejectCall)
        assertFalse(allowResult.shouldRouteToReceptionist)
        assertEquals(SpamClassification.ALLOWLISTED, allowResult.spamClassification)

        val blockResult = engine.evaluateCall(
            rawNumber = "+911409988776",
            settings = settings,
            callerRules = rules,
            recentCallsFromNumber = emptyList()
        )
        assertTrue(blockResult.isBlocklisted)
        assertTrue(blockResult.shouldDisallowCall)
        assertTrue(blockResult.shouldRejectCall)
        assertEquals(100, blockResult.spamScore)

        val savedContactResult = engine.evaluateCall(
            rawNumber = "+919831122334",
            settings = settings,
            callerRules = emptyList(),
            recentCallsFromNumber = emptyList(),
            overrideContactName = "Dr. Sen"
        )
        assertTrue(savedContactResult.isSavedContact)
        assertFalse(savedContactResult.shouldRouteToReceptionist)
        assertEquals(CallHandlingStatus.BYPASSED_KNOWN_CONTACT, savedContactResult.handlingStatus)
    }

    @Test
    fun `4 ordinary unknown numbers are not automatically labeled as spam and route to receptionist`() {
        val settings = VoiceShieldSettings(
            unknownNumberAction = UnknownNumberAction.RECEPTIONIST,
            spamThreshold = 65
        )
        val unknownResult = engine.evaluateCall(
            rawNumber = "+919831234567",
            verificationStatus = Connection.VERIFICATION_STATUS_NOT_VERIFIED,
            settings = settings,
            callerRules = emptyList(),
            recentCallsFromNumber = emptyList()
        )
        assertEquals(15, unknownResult.spamScore)
        assertEquals(SpamClassification.LOW_RISK, unknownResult.spamClassification)
        assertTrue(unknownResult.shouldRouteToReceptionist)
        assertFalse(unknownResult.shouldRejectCall)
    }

    @Test
    fun `5 telemarketer prefix plus failed STIR SHAKEN and repeated call burst triggers spam block`() {
        val settings = VoiceShieldSettings(spamThreshold = 65)
        val recentBurst = listOf(
            ScreenedCallEntity(callerNumber = "+911401112223", contactName = null, timestampMillis = 100L, spamScore = 70, spamClassification = "SUSPICIOUS", handlingStatus = "SILENCED", reasonSummary = "", verificationStatus = "FAILED"),
            ScreenedCallEntity(callerNumber = "+911401112223", contactName = null, timestampMillis = 200L, spamScore = 70, spamClassification = "SUSPICIOUS", handlingStatus = "SILENCED", reasonSummary = "", verificationStatus = "FAILED"),
            ScreenedCallEntity(callerNumber = "+911401112223", contactName = null, timestampMillis = 300L, spamScore = 70, spamClassification = "SUSPICIOUS", handlingStatus = "SILENCED", reasonSummary = "", verificationStatus = "FAILED")
        )
        val spamEval = engine.evaluateCall(
            rawNumber = "+911401112223",
            verificationStatus = Connection.VERIFICATION_STATUS_FAILED,
            settings = settings,
            callerRules = emptyList(),
            recentCallsFromNumber = recentBurst
        )
        assertTrue(spamEval.spamScore >= 65)
        assertTrue(spamEval.isRepeatedCallBurst)
        assertEquals(SpamClassification.HIGH_RISK_SPAM, spamEval.spamClassification)
        assertTrue(spamEval.shouldRejectCall)
    }

    @Test
    fun `6 keystore crypto manager encrypts decrypts and securely deletes recording files`() {
        val crypto = KeystoreCryptoManager()
        val tempDir = Files.createTempDirectory("voiceshield_test").toFile()
        val encFile = File(tempDir, "test_msg.wav.enc")
        val originalPayload = "RIFF_VOICE_SHIELD_TEST_AUDIO_PAYLOAD_16KHZ".toByteArray()

        val encryptedOk = crypto.encryptToFile(originalPayload, encFile)
        assertTrue(encryptedOk)
        assertTrue(encFile.exists())
        // Ciphertext must differ from plaintext and include 12-byte IV + 16-byte GCM tag
        assertEquals(originalPayload.size + 12 + 16, encFile.length().toInt())

        val decrypted = crypto.decryptFromFile(encFile)
        assertNotNull(decrypted)
        assertArrayEquals(originalPayload, decrypted)

        val deleted = crypto.secureDeleteFile(encFile.absolutePath)
        assertTrue(deleted)
        assertFalse(encFile.exists())
        assertNull(crypto.decryptFromFile(encFile))
        tempDir.deleteRecursively()
    }

    @Test
    fun `7 all 12 voice moods are defined with valid prosody bounds`() {
        assertEquals(12, VoiceMood.entries.size)
        VoiceMood.entries.forEach { mood ->
            assertTrue(mood.defaultSpeed in 0.5f..2.0f)
            assertTrue(mood.defaultPitch in 0.5f..2.0f)
            assertTrue(mood.defaultVolume in 0.1f..1.0f)
            assertTrue(mood.defaultPauseMs in 50..1000)
        }
    }
}
