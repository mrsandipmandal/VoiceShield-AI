package com.example.telecom.incall

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.example.VoiceShieldApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ActiveTelecomCallState(
    val hasActiveCall: Boolean = false,
    val callerNumber: String = "",
    val callState: Int = Call.STATE_DISCONNECTED,
    val callStateLabel: String = "Idle / No Active Call",
    val isEmergencyCall: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerphoneOn: Boolean = false,
    val connectTimeMillis: Long = 0L
)

/**
 * Real Android InCallService supporting the optional Default Dialer role (RoleManager.ROLE_DIALER).
 * - Tracks live incoming and ongoing call state changes safely.
 * - Exposes answer(), disconnect(), mute, and speakerphone controls via documented Telecom APIs.
 * - Never interferes with emergency calls.
 */
class VoiceShieldInCallService : InCallService() {

    companion object {
        private val _activeCallState = MutableStateFlow(ActiveTelecomCallState())
        val activeCallState: StateFlow<ActiveTelecomCallState> = _activeCallState.asStateFlow()

        @Volatile
        private var currentCallRef: Call? = null

        @Volatile
        private var serviceInstanceRef: VoiceShieldInCallService? = null

        fun answerActiveCall(): Boolean {
            serviceInstanceRef?.cancelAutoAnswer()
            val call = currentCallRef ?: return false
            return try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun disconnectActiveCall(): Boolean {
            serviceInstanceRef?.cancelAutoAnswer()
            val call = currentCallRef ?: return false
            // Safeguard: never programmatically disconnect an emergency call from automation
            val isEmergency = call.details?.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) == true
            if (isEmergency) return false
            return try {
                if (call.state == Call.STATE_RINGING) {
                    call.reject(false, null)
                } else {
                    call.disconnect()
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        fun toggleSpeakerphone(enable: Boolean) {
            val svc = serviceInstanceRef ?: return
            val route = if (enable) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            try {
                svc.setAudioRoute(route)
                _activeCallState.value = _activeCallState.value.copy(isSpeakerphoneOn = enable)
            } catch (_: Exception) {}
        }

        fun toggleMute(mute: Boolean) {
            val svc = serviceInstanceRef ?: return
            try {
                svc.setMuted(mute)
                _activeCallState.value = _activeCallState.value.copy(isMuted = mute)
            } catch (_: Exception) {}
        }

        fun stateToLabel(state: Int): String = when (state) {
            Call.STATE_NEW -> "New"
            Call.STATE_RINGING -> "Incoming Ringing"
            Call.STATE_DIALING -> "Dialing"
            Call.STATE_ACTIVE -> "Connected / Active"
            Call.STATE_HOLDING -> "On Hold"
            Call.STATE_DISCONNECTING -> "Disconnecting"
            Call.STATE_DISCONNECTED -> "Disconnected"
            Call.STATE_CONNECTING -> "Connecting"
            else -> "Idle"
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoAnswerJob: Job? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallSnapshot(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details?) {
            updateCallSnapshot(call)
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstanceRef = this
    }

    override fun onDestroy() {
        cancelAutoAnswer()
        serviceScope.cancel()
        if (serviceInstanceRef === this) {
            serviceInstanceRef = null
        }
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCallRef = call
        call.registerCallback(callCallback)
        updateCallSnapshot(call)
    }

    override fun onCallRemoved(call: Call) {
        cancelAutoAnswer()
        call.unregisterCallback(callCallback)
        if (currentCallRef === call) {
            currentCallRef = null
        }
        _activeCallState.value = ActiveTelecomCallState()
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            _activeCallState.value = _activeCallState.value.copy(
                isMuted = audioState.isMuted,
                isSpeakerphoneOn = (audioState.route == CallAudioState.ROUTE_SPEAKER)
            )
        }
    }

    private fun cancelAutoAnswer() {
        autoAnswerJob?.cancel()
        autoAnswerJob = null
    }

    private fun scheduleAutoAnswerIfEnabled(call: Call) {
        if (autoAnswerJob?.isActive == true) return
        val app = applicationContext as? VoiceShieldApplication ?: return
        autoAnswerJob = serviceScope.launch {
            val settings = app.repository.settingsFlow.first()
            if (!settings.protectionEnabled || !settings.autoAnswerEnabled) {
                return@launch
            }
            val delaySec = settings.answerDelaySeconds.coerceIn(1, 30)
            for (sec in delaySec downTo 1) {
                if (call.state != Call.STATE_RINGING) break
                _activeCallState.value = _activeCallState.value.copy(
                    callStateLabel = "Incoming Call • Auto-answering in ${sec}s..."
                )
                delay(1000L)
            }
            if (call.state == Call.STATE_RINGING) {
                _activeCallState.value = _activeCallState.value.copy(
                    callStateLabel = "Auto-answering with VoiceShield AI..."
                )
                val answered = answerActiveCall()
                if (answered) {
                    app.repository.handleCellularInCallReceptionist(call)
                }
            }
        }
    }

    private fun updateCallSnapshot(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart.orEmpty().ifBlank { "Unknown Caller" }
        val isEmergency = call.details?.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) == true
        val state = call.state

        if (state == Call.STATE_RINGING && !isEmergency) {
            scheduleAutoAnswerIfEnabled(call)
        } else {
            cancelAutoAnswer()
        }

        _activeCallState.value = ActiveTelecomCallState(
            hasActiveCall = state != Call.STATE_DISCONNECTED,
            callerNumber = number,
            callState = state,
            callStateLabel = if (autoAnswerJob?.isActive == true) _activeCallState.value.callStateLabel else stateToLabel(state),
            isEmergencyCall = isEmergency,
            isMuted = _activeCallState.value.isMuted,
            isSpeakerphoneOn = _activeCallState.value.isSpeakerphoneOn,
            connectTimeMillis = call.details?.connectTimeMillis ?: System.currentTimeMillis()
        )
    }
}
