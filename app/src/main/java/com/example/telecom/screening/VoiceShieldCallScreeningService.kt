package com.example.telecom.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import com.example.VoiceShieldApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real Android CallScreeningService implementation.
 * - Screens incoming calls within Android's strict 5-second response deadline (targets < 1500ms).
 * - Never confuses call screening with answering calls (answering is handled by InCallService / TelecomManager).
 * - Never blocks or interferes with emergency numbers.
 */
class VoiceShieldCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen incoming calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val verificationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerNumberVerificationStatus
        } else {
            Connection.VERIFICATION_STATUS_NOT_VERIFIED
        }

        serviceScope.launch {
            val app = applicationContext as? VoiceShieldApplication
            val evaluation = withTimeoutOrNull(2500L) {
                app?.repository?.screenIncomingCallAndLog(
                    rawNumber = rawNumber,
                    verificationStatus = verificationStatus,
                    triggerReceptionistIfApplicable = true
                )
            }

            val responseBuilder = CallResponse.Builder()
            if (evaluation != null && !evaluation.isEmergencyNumber) {
                responseBuilder
                    .setDisallowCall(evaluation.shouldDisallowCall)
                    .setRejectCall(evaluation.shouldRejectCall)
                    .setSilenceCall(evaluation.shouldSilenceCall)
                    .setSkipCallLog(evaluation.shouldSkipCallLog)
                    .setSkipNotification(evaluation.shouldskipNotification)
            }

            respondToCall(callDetails, responseBuilder.build())
        }
    }
}
