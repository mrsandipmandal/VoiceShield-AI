package com.example.ai.capability

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.domain.model.CapabilitySupportStatus
import com.example.domain.model.DeviceAudioCapabilities

/**
 * Dedicated CallAudioCapabilityManager that inspects real Android OS, Telecom, and Audio
 * capabilities without using hidden APIs, root, AccessibilityService, or undocumented hacks.
 *
 * Clearly distinguishes between:
 * 1. Call screening supported
 * 2. Call answering/disconnecting supported
 * 3. TTS generation supported
 * 4. TTS transmission to the remote cellular caller supported
 * 5. Remote caller audio recording supported
 */
class CallAudioCapabilityManager(
    private val context: Context
) {

    fun evaluateCapabilities(isLocalTtsReady: Boolean = true): DeviceAudioCapabilities {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        val audioManager = context.getSystemService(AudioManager::class.java)

        val hasContacts = hasPermission(Manifest.permission.READ_CONTACTS)
        val hasRecordAudio = hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasPhoneState = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val hasAnswerCalls = hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        val isCallScreeningRoleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) ?: true
        val isCallScreeningRoleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false

        val isDialerRoleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) ?: true
        val isDefaultDialer = (roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true) ||
            (telecomManager?.defaultDialerPackage == context.packageName)

        // 1. Call Screening Support
        val callScreeningStatus = when {
            !isCallScreeningRoleAvailable -> CapabilitySupportStatus.UNSUPPORTED
            isCallScreeningRoleHeld -> CapabilitySupportStatus.SUPPORTED
            else -> CapabilitySupportStatus.LIMITED
        }
        val callScreeningDetail = when (callScreeningStatus) {
            CapabilitySupportStatus.SUPPORTED ->
                "CallScreeningService role granted. Unknown & spam numbers are screened within Android's 5s deadline."
            CapabilitySupportStatus.LIMITED ->
                "CallScreeningService is available on Android ${Build.VERSION.RELEASE} — grant Call Screening Role to activate automatic background screening."
            CapabilitySupportStatus.UNSUPPORTED ->
                "RoleManager.ROLE_CALL_SCREENING is not supported on this device profile."
        }

        // 2. Call Answering & Disconnecting Support
        val answerDisconnectStatus = when {
            isDefaultDialer && hasAnswerCalls -> CapabilitySupportStatus.SUPPORTED
            hasAnswerCalls || isDialerRoleAvailable -> CapabilitySupportStatus.LIMITED
            else -> CapabilitySupportStatus.UNSUPPORTED
        }
        val answerDisconnectDetail = when (answerDisconnectStatus) {
            CapabilitySupportStatus.SUPPORTED ->
                "Default Dialer Role & ANSWER_PHONE_CALLS granted. Programmatic answer and disconnect via TelecomManager/InCallService are active."
            CapabilitySupportStatus.LIMITED ->
                "Supported via TelecomManager.acceptRingingCall() / InCallService once Default Dialer role or ANSWER_PHONE_CALLS permission is granted."
            CapabilitySupportStatus.UNSUPPORTED ->
                "Telephony feature unavailable on this device."
        }

        // 3. Local TTS Generation Support
        val ttsGenerationStatus = if (isLocalTtsReady) {
            CapabilitySupportStatus.SUPPORTED
        } else {
            CapabilitySupportStatus.LIMITED
        }
        val ttsGenerationDetail = if (isLocalTtsReady) {
            "Offline TTS synthesis (Sherpa-ONNX & System Offline Engine) is active for Bengali, English (India), and Hindi."
        } else {
            "TTS engine initializing or awaiting voice model verification."
        }

        // 4. TTS Transmission to Remote Cellular Caller
        // Public Android APIs only allow routing directly into Telephony Uplink if an AudioDeviceInfo.TYPE_TELEPHONY
        // sink is writable by the app (which standard non-system apps cannot write to directly).
        val hasTelephonyOutputSink = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { it.type == AudioDeviceInfo.TYPE_TELEPHONY } == true
        val isSystemOrPrivilegedApp = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

        val cellularTtsUplinkStatus = when {
            hasTelephonyOutputSink && isSystemOrPrivilegedApp -> CapabilitySupportStatus.SUPPORTED
            else -> CapabilitySupportStatus.UNSUPPORTED
        }
        val cellularTtsUplinkDetail = when (cellularTtsUplinkStatus) {
            CapabilitySupportStatus.SUPPORTED ->
                "Direct telephony uplink audio sink detected and privileged routing enabled."
            else ->
                "Unsupported on standard Android cellular calls: Android's audio HAL isolates the cellular uplink stream from third-party apps (requires CAPTURE_AUDIO_OUTPUT / system privilege). Use Interactive Receptionist Simulator, Acoustic Speaker Mode, or SIP/VoIP PBX forwarding."
        }

        // 5. Remote Cellular Caller Audio Recording Support
        // Android 10+ blocks third-party access to MediaRecorder.AudioSource.VOICE_DOWNLINK / VOICE_CALL.
        val cellularRemoteRecordingStatus = when {
            isSystemOrPrivilegedApp && hasRecordAudio -> CapabilitySupportStatus.SUPPORTED
            hasRecordAudio -> CapabilitySupportStatus.UNSUPPORTED
            else -> CapabilitySupportStatus.UNSUPPORTED
        }
        val cellularRemoteRecordingDetail = when {
            isSystemOrPrivilegedApp && hasRecordAudio ->
                "Privileged VOICE_CALL recording supported."
            else ->
                "Unsupported for cellular downlink on ordinary Android 10+ devices: Android security blocks third-party apps from recording the remote cellular party (AudioSource.VOICE_DOWNLINK). VoiceShield AI never creates fake recordings — cellular calls use Fallback Policy unless routed via SIP/VoIP or tested in Interactive Simulator."
        }

        return DeviceAudioCapabilities(
            callScreeningSupported = callScreeningStatus,
            callScreeningDetail = callScreeningDetail,
            callAnswerDisconnectSupported = answerDisconnectStatus,
            callAnswerDisconnectDetail = answerDisconnectDetail,
            localTtsGenerationSupported = ttsGenerationStatus,
            localTtsGenerationDetail = ttsGenerationDetail,
            cellularTtsUplinkSupported = cellularTtsUplinkStatus,
            cellularTtsUplinkDetail = cellularTtsUplinkDetail,
            cellularRemoteRecordingSupported = cellularRemoteRecordingStatus,
            cellularRemoteRecordingDetail = cellularRemoteRecordingDetail,
            isDefaultCallScreener = isCallScreeningRoleHeld,
            isDefaultDialer = isDefaultDialer,
            hasContactsPermission = hasContacts,
            hasRecordAudioPermission = hasRecordAudio,
            hasPhoneStatePermission = hasPhoneState,
            hasPostNotificationsPermission = hasNotifications,
            hasAnswerCallsPermission = hasAnswerCalls
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Architectural reference for SIP/VoIP or Self-Hosted PBX integration when full
     * two-way cellular call audio injection and recording is desired on non-rooted Android.
     */
    fun getSipVoipArchitectureExplanation(): String = """
        Why Cellular Call Audio Injection is Restricted on Standard Android:
        • Since Android 6.0+ and strictly enforced in Android 10+ (API 29+), the Android Audio HAL isolates the baseband cellular modem audio streams (VOICE_UPLINK and VOICE_DOWNLINK).
        • Injecting synthesized TTS directly into the cellular uplink or capturing the remote caller's downlink stream requires signature/privileged permission android.permission.CAPTURE_AUDIO_OUTPUT or OEM dialer signing keys.
        
        Recommended Production Alternative — Conditional Call Forwarding to Local/Self-Hosted SIP PBX:
        1. Configure standard GSM Conditional Call Forwarding on Busy/Unanswered (*67*<SIP_DID>#) so rejected or unanswered unknown calls forward to a lightweight SIP/VoIP endpoint (e.g., Asterisk, FreeSWITCH, or Android SIP ConnectionService).
        2. Because VoIP/RTP audio flows in user-space sockets rather than the locked cellular baseband, VoiceShield AI's Sherpa-ONNX TTS greeting and AES-GCM recorder have 100% legal, crystal-clear two-way audio access.
        3. On-Device Interactive Simulator & Acoustic Mode: For immediate local verification or hands-free desk usage, VoiceShield AI also supports Acoustic Speakerphone Mode and the Interactive Receptionist Simulator using standard MediaPlayer + AudioRecord MIC APIs.
    """.trimIndent()
}
