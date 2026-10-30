package com.example.domain.usecase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Connection
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.example.core.database.CallerRuleEntity
import com.example.core.database.ScreenedCallEntity
import com.example.domain.model.CallHandlingStatus
import com.example.domain.model.CallerRuleType
import com.example.domain.model.SpamClassification
import com.example.domain.model.UnknownNumberAction
import com.example.domain.model.VoiceShieldSettings
import java.util.Calendar

data class ScreeningEvaluationResult(
    val normalizedNumber: String,
    val displayNumber: String,
    val isEmergencyNumber: Boolean,
    val isSavedContact: Boolean,
    val contactName: String?,
    val isAllowlisted: Boolean,
    val isBlocklisted: Boolean,
    val isRepeatedCallBurst: Boolean,
    val spamScore: Int,
    val spamClassification: SpamClassification,
    val handlingStatus: CallHandlingStatus,
    val shouldDisallowCall: Boolean,
    val shouldRejectCall: Boolean,
    val shouldSilenceCall: Boolean,
    val shouldSkipCallLog: Boolean = false,
    val shouldskipNotification: Boolean = false,
    val shouldRouteToReceptionist: Boolean,
    val reasonSummary: String
)

/**
 * Pure, testable call screening and local spam scoring engine.
 * Never labels every unknown number as spam automatically, and always permits
 * emergency numbers and user-approved contacts.
 */
class SpamAndScreeningEngine(
    private val context: Context? = null
) {
    companion object {
        private val EMERGENCY_NUMBERS = setOf(
            "112", "911", "999", "100", "101", "102", "108", "1091", "1098", "000"
        )

        private val KNOWN_TELEMARKETER_PREFIXES = listOf(
            "+91140", "140", "+91160", "160", "1800", "+1800", "+44800"
        )

        fun normalizePhoneNumber(rawNumber: String): String {
            val trimmed = rawNumber.trim()
            if (trimmed.isEmpty()) return ""
            val hasPlus = trimmed.startsWith("+")
            val digits = trimmed.filter { it.isDigit() }
            return if (hasPlus && digits.isNotEmpty()) "+$digits" else digits
        }

        fun numbersMatch(first: String, second: String): Boolean {
            val n1 = normalizePhoneNumber(first)
            val n2 = normalizePhoneNumber(second)
            if (n1.isEmpty() || n2.isEmpty()) return false
            if (n1 == n2) return true
            // Match trailing 10 digits for national/international format equivalence (e.g., +919876543210 vs 9876543210)
            val tail1 = n1.filter { it.isDigit() }.takeLast(10)
            val tail2 = n2.filter { it.isDigit() }.takeLast(10)
            return tail1.length == 10 && tail1 == tail2
        }
    }

    fun isEmergency(rawNumber: String): Boolean {
        val clean = normalizePhoneNumber(rawNumber).removePrefix("+")
        if (clean in EMERGENCY_NUMBERS) return true
        return try {
            if (context != null) {
                PhoneNumberUtils.isEmergencyNumber(rawNumber)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Looks up whether [phoneNumber] is saved in the user's device contacts when READ_CONTACTS is granted.
     */
    fun lookupSystemContactName(phoneNumber: String): String? {
        val ctx = context ?: return null
        if (phoneNumber.isBlank()) return null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates an incoming call against Emergency safeguards, Contacts, Allowlist, Blocklist,
     * User-Reported Spam, Repeated-Call Burst history, STIR/SHAKEN verification status,
     * and the user's VoiceShieldSettings.
     */
    fun evaluateCall(
        rawNumber: String,
        verificationStatus: Int = Connection.VERIFICATION_STATUS_NOT_VERIFIED,
        settings: VoiceShieldSettings,
        callerRules: List<CallerRuleEntity>,
        recentCallsFromNumber: List<ScreenedCallEntity>,
        overrideContactName: String? = null,
        currentHourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): ScreeningEvaluationResult {
        val displayNumber = rawNumber.ifBlank { "Private / Hidden Number" }
        val normalized = normalizePhoneNumber(rawNumber)

        // 0. Emergency Call Safeguard — NEVER interfere with emergency numbers
        if (isEmergency(rawNumber)) {
            return ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = true,
                isSavedContact = false,
                contactName = "Emergency Services",
                isAllowlisted = true,
                isBlocklisted = false,
                isRepeatedCallBurst = false,
                spamScore = 0,
                spamClassification = SpamClassification.CLEAN,
                handlingStatus = CallHandlingStatus.EMERGENCY_BYPASS,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = false,
                shouldRouteToReceptionist = false,
                reasonSummary = "Emergency number detected — bypassed all screening immediately."
            )
        }

        // If protection is turned off globally, allow normal ringing
        if (!settings.protectionEnabled) {
            return ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = false,
                contactName = overrideContactName,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = false,
                spamScore = 0,
                spamClassification = SpamClassification.LOW_RISK,
                handlingStatus = CallHandlingStatus.SCREENED_ALLOWED,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = false,
                shouldRouteToReceptionist = false,
                reasonSummary = "VoiceShield protection is paused — allowed normal ring."
            )
        }

        // 1. Check Allowlist & Blocklist & User-Reported Spam
        val matchingRules = callerRules.filter { numbersMatch(it.phoneNumber, rawNumber) }
        val allowRule = matchingRules.firstOrNull { it.ruleType == CallerRuleType.ALLOWLIST.name }
        val blockRule = matchingRules.firstOrNull { it.ruleType == CallerRuleType.BLOCKLIST.name }
        val reportedSpamRules = matchingRules.filter { it.ruleType == CallerRuleType.USER_REPORTED_SPAM.name }

        // 2. Check Saved System Contacts
        val systemContact = overrideContactName ?: lookupSystemContactName(rawNumber) ?: allowRule?.displayName
        val isSavedContact = !systemContact.isNullOrBlank() && allowRule == null

        if (blockRule != null) {
            return ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = isSavedContact,
                contactName = blockRule.displayName.ifBlank { systemContact },
                isAllowlisted = false,
                isBlocklisted = true,
                isRepeatedCallBurst = false,
                spamScore = 100,
                spamClassification = SpamClassification.BLOCKLISTED,
                handlingStatus = CallHandlingStatus.SCREENED_REJECTED,
                shouldDisallowCall = true,
                shouldRejectCall = true,
                shouldSilenceCall = true,
                shouldRouteToReceptionist = false,
                reasonSummary = "Number is on your Blocklist (${blockRule.notes.ifBlank { "Blocked by user" }})."
            )
        }

        if (allowRule != null) {
            return ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = true,
                contactName = allowRule.displayName.ifBlank { systemContact ?: "Allowlisted Caller" },
                isAllowlisted = true,
                isBlocklisted = false,
                isRepeatedCallBurst = false,
                spamScore = 0,
                spamClassification = SpamClassification.ALLOWLISTED,
                handlingStatus = CallHandlingStatus.SCREENED_ALLOWED,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = false,
                shouldRouteToReceptionist = false,
                reasonSummary = "Caller is on your Allowlist — allowed immediately."
            )
        }

        if (isSavedContact && settings.knownContactsBypass) {
            return ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = true,
                contactName = systemContact,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = false,
                spamScore = 5,
                spamClassification = SpamClassification.CLEAN,
                handlingStatus = CallHandlingStatus.BYPASSED_KNOWN_CONTACT,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = false,
                shouldRouteToReceptionist = false,
                reasonSummary = "Saved contact ($systemContact) bypassed receptionist per settings."
            )
        }

        // 3. Calculate Spam Score (0 - 100) without labeling every unknown number as spam
        var score = 15 // Base score for an ordinary unknown number is Low Risk (15), NOT spam
        val reasons = mutableListOf<String>()

        if (rawNumber.isBlank()) {
            score += 35
            reasons.add("Hidden/Private caller ID (+35)")
        }

        if (KNOWN_TELEMARKETER_PREFIXES.any { normalized.startsWith(it) }) {
            score += 55
            reasons.add("Telemarketing series prefix (+55)")
        }

        // Digit pattern heuristics (e.g., 5+ identical consecutive digits or sequential spam patterns)
        val digitsOnly = normalized.filter { it.isDigit() }
        if (digitsOnly.length >= 8 && Regex("(\\d)\\1{4,}").containsMatchIn(digitsOnly)) {
            score += 30
            reasons.add("Robocall repetitive digit pattern (+30)")
        }

        // User-reported spam rules
        if (reportedSpamRules.isNotEmpty()) {
            val reportBoost = reportedSpamRules.maxOf { it.spamWeight }.coerceIn(40, 85)
            score += reportBoost
            reasons.add("User-reported spam number (+$reportBoost)")
        }

        // Android STIR/SHAKEN caller number verification
        when (verificationStatus) {
            Connection.VERIFICATION_STATUS_FAILED -> {
                score += 45
                reasons.add("Carrier STIR/SHAKEN attestation failed (+45)")
            }
            Connection.VERIFICATION_STATUS_PASSED -> {
                score = (score - 10).coerceAtLeast(5)
                reasons.add("Carrier STIR/SHAKEN verified (-10)")
            }
        }

        // Repeated incoming call detection within last 10 minutes
        val isRepeatedBurst = recentCallsFromNumber.size >= 3
        if (isRepeatedBurst) {
            score += 25
            reasons.add("Repeated burst (${recentCallsFromNumber.size + 1} calls in 10m, +25)")
        }

        val finalScore = score.coerceIn(0, 100)
        val classification = when {
            finalScore >= settings.spamThreshold -> SpamClassification.HIGH_RISK_SPAM
            finalScore >= (settings.spamThreshold * 0.65).toInt() -> SpamClassification.SUSPICIOUS
            finalScore <= 15 -> SpamClassification.LOW_RISK
            else -> SpamClassification.LOW_RISK
        }

        // 4. High-Risk Spam >= spamThreshold -> Reject or Silence
        if (finalScore >= settings.spamThreshold) {
            return ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = isSavedContact,
                contactName = systemContact,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = isRepeatedBurst,
                spamScore = finalScore,
                spamClassification = classification,
                handlingStatus = CallHandlingStatus.SCREENED_REJECTED,
                shouldDisallowCall = true,
                shouldRejectCall = true,
                shouldSilenceCall = true,
                shouldRouteToReceptionist = false,
                reasonSummary = "Spam score $finalScore >= threshold ${settings.spamThreshold} (${reasons.joinToString(", ")})."
            )
        }

        // 5. Check Working Hours schedule if enabled
        val withinWorkingHours = if (!settings.workingHoursEnabled) {
            true
        } else {
            val start = settings.workingHoursStartHour
            val end = settings.workingHoursEndHour
            if (start <= end) {
                currentHourOfDay in start..end
            } else {
                currentHourOfDay >= start || currentHourOfDay <= end
            }
        }

        // 6. Apply Unknown Number Policy
        val action = if (!withinWorkingHours && settings.unknownNumberAction == UnknownNumberAction.RECEPTIONIST) {
            UnknownNumberAction.SILENCE
        } else {
            settings.unknownNumberAction
        }

        return when (action) {
            UnknownNumberAction.RECEPTIONIST -> ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = isSavedContact,
                contactName = systemContact,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = isRepeatedBurst,
                spamScore = finalScore,
                spamClassification = classification,
                handlingStatus = CallHandlingStatus.RECEPTIONIST_COMPLETED,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = true, // Silence ringer while AI Receptionist handles call
                shouldRouteToReceptionist = true,
                reasonSummary = "Unknown caller (Spam Score $finalScore/${settings.spamThreshold}) routed to AI Voice Receptionist."
            )

            UnknownNumberAction.ALLOW -> ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = isSavedContact,
                contactName = systemContact,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = isRepeatedBurst,
                spamScore = finalScore,
                spamClassification = classification,
                handlingStatus = CallHandlingStatus.SCREENED_ALLOWED,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = false,
                shouldRouteToReceptionist = false,
                reasonSummary = "Unknown caller allowed to ring (Spam Score $finalScore < ${settings.spamThreshold})."
            )

            UnknownNumberAction.SILENCE -> ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = isSavedContact,
                contactName = systemContact,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = isRepeatedBurst,
                spamScore = finalScore,
                spamClassification = classification,
                handlingStatus = CallHandlingStatus.SCREENED_SILENCED,
                shouldDisallowCall = false,
                shouldRejectCall = false,
                shouldSilenceCall = true,
                shouldRouteToReceptionist = false,
                reasonSummary = "Unknown caller silenced per policy (Spam Score $finalScore)."
            )

            UnknownNumberAction.REJECT -> ScreeningEvaluationResult(
                normalizedNumber = normalized,
                displayNumber = displayNumber,
                isEmergencyNumber = false,
                isSavedContact = isSavedContact,
                contactName = systemContact,
                isAllowlisted = false,
                isBlocklisted = false,
                isRepeatedCallBurst = isRepeatedBurst,
                spamScore = finalScore,
                spamClassification = classification,
                handlingStatus = CallHandlingStatus.SCREENED_REJECTED,
                shouldDisallowCall = true,
                shouldRejectCall = true,
                shouldSilenceCall = true,
                shouldRouteToReceptionist = false,
                reasonSummary = "Unknown caller rejected per policy (Spam Score $finalScore)."
            )
        }
    }
}
