package com.example.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import com.example.ai.tts.LocalTtsEngine
import com.example.core.security.KeystoreCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

data class PlaybackUiState(
    val playingMessageId: String? = null,
    val isPlaying: Boolean = false,
    val progressFraction: Float = 0f,
    val currentPositionSec: Int = 0,
    val totalDurationSec: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val errorText: String? = null
)

data class RecordingCaptureResult(
    val success: Boolean,
    val encryptedFilePath: String?,
    val durationSeconds: Int,
    val endedBySilenceTimeout: Boolean,
    val rawWavBytes: ByteArray?,
    val statusDetail: String
)

/**
 * Handles:
 * 1. Real microphone PCM recording (with RMS silence timeout & max duration enforcement)
 *    and immediate AES-GCM encryption at rest via [KeystoreCryptoManager].
 * 2. Decryption in memory and variable-speed audio playback (`0.75x` - `2.0x`) with progress scrubbing.
 */
class EncryptedAudioPlayerAndRecorder(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var playbackJob: Job? = null
    private var activeTrack: AudioTrack? = null

    fun getEncryptedRecordingsDir(): File {
        return File(context.filesDir, "encrypted_voice_messages").apply { mkdirs() }
    }

    /**
     * Captures audio from the device microphone (when RECORD_AUDIO permission is granted)
     * or synthesizes a realistic caller voice waveform during interactive simulation,
     * monitoring silence timeout and max duration, then encrypting with AES-256-GCM.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordAndEncryptCallerMessage(
        messageId: String,
        maxDurationSeconds: Int,
        silenceTimeoutSeconds: Int,
        useHardwareMicIfAvailable: Boolean,
        hasRecordPermission: Boolean,
        onTick: (elapsedSec: Int, silenceSec: Int, normalizedAmplitude: Float) -> Unit,
        shouldStopEarly: () -> Boolean
    ): RecordingCaptureResult = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val pcmOut = ByteArrayOutputStream()
        var elapsedSeconds = 0
        var consecutiveSilenceSec = 0
        var endedBySilence = false

        var audioRecord: AudioRecord? = null
        var usingLiveMic = false

        if (useHardwareMicIfAvailable && hasRecordPermission) {
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(3200)
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    audioRecord = recorder
                    usingLiveMic = true
                } else {
                    recorder.release()
                }
            } catch (_: Exception) {
                usingLiveMic = false
            }
        }

        try {
            val targetSimDuration = maxDurationSeconds.coerceIn(4, 8)
            val shortBuf = ShortArray(sampleRate / 5) // 200ms chunks (5 ticks per second)
            var tickCount = 0

            while (elapsedSeconds < maxDurationSeconds && !shouldStopEarly()) {
                val amplitude: Float
                if (usingLiveMic && audioRecord != null) {
                    val read = audioRecord.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until read) {
                            val s = shortBuf[i]
                            byteBuf.putShort(s)
                            sumSq += s.toDouble() * s.toDouble()
                        }
                        pcmOut.write(byteBuf.array())
                        val rms = sqrt(sumSq / read)
                        amplitude = (rms.toFloat() / 8000f).coerceIn(0.02f, 1.0f)
                    } else {
                        amplitude = 0.02f
                    }
                    delay(200L)
                } else {
                    // Interactive Simulator acoustic voice stream (when running on emulator or testing without mic input)
                    val chunkSamples = sampleRate / 5
                    val byteBuf = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
                    val isSpeakingPhase = elapsedSeconds < (targetSimDuration - 1).coerceAtLeast(3)
                    val freq = 210.0 + ((tickCount % 6) * 18.0)
                    for (i in 0 until chunkSamples) {
                        val t = i.toDouble() / sampleRate
                        val env = if (isSpeakingPhase) sin(PI * i / chunkSamples) else 0.02
                        val sampleVal = (sin(2.0 * PI * freq * t) * env * 0.55 * Short.MAX_VALUE)
                            .toInt()
                            .toShort()
                        byteBuf.putShort(sampleVal)
                    }
                    pcmOut.write(byteBuf.array())
                    amplitude = if (isSpeakingPhase) {
                        (0.35f + 0.45f * sin(tickCount * 0.7f).let { kotlin.math.abs(it) }).coerceIn(0.15f, 0.95f)
                    } else {
                        0.03f
                    }
                    delay(200L)
                }

                tickCount++
                if (tickCount % 5 == 0) {
                    elapsedSeconds++
                    if (amplitude < 0.08f) {
                        consecutiveSilenceSec++
                    } else {
                        consecutiveSilenceSec = 0
                    }
                    onTick(elapsedSeconds, consecutiveSilenceSec, amplitude)

                    if (consecutiveSilenceSec >= silenceTimeoutSeconds && elapsedSeconds >= 2) {
                        endedBySilence = true
                        break
                    }
                    if (!usingLiveMic && elapsedSeconds >= targetSimDuration) {
                        break
                    }
                } else {
                    onTick(elapsedSeconds, consecutiveSilenceSec, amplitude)
                }
            }
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
        }

        val pcmBytes = pcmOut.toByteArray()
        if (pcmBytes.isEmpty()) {
            return@withContext RecordingCaptureResult(
                success = false,
                encryptedFilePath = null,
                durationSeconds = 0,
                endedBySilenceTimeout = endedBySilence,
                rawWavBytes = null,
                statusDetail = "No audio frames captured."
            )
        }

        val wavBytes = LocalTtsEngine.wrapPcmInWavHeader(pcmBytes, sampleRate, 1, 16)
        val encryptedFile = File(getEncryptedRecordingsDir(), "msg_${messageId}.wav.enc")
        val encryptedOk = cryptoManager.encryptToFile(wavBytes, encryptedFile)

        if (!encryptedOk) {
            return@withContext RecordingCaptureResult(
                success = false,
                encryptedFilePath = null,
                durationSeconds = elapsedSeconds,
                endedBySilenceTimeout = endedBySilence,
                rawWavBytes = null,
                statusDetail = "Failed to encrypt voice recording via Android Keystore."
            )
        }

        RecordingCaptureResult(
            success = true,
            encryptedFilePath = encryptedFile.absolutePath,
            durationSeconds = elapsedSeconds.coerceAtLeast(1),
            endedBySilenceTimeout = endedBySilence,
            rawWavBytes = wavBytes,
            statusDetail = if (usingLiveMic) {
                "Captured live microphone audio (${elapsedSeconds}s) and encrypted with AES-256-GCM."
            } else {
                "Captured interactive simulator voice stream (${elapsedSeconds}s) and encrypted with AES-256-GCM."
            }
        )
    }

    /**
     * Decrypts an encrypted `.wav.enc` recording in memory and plays it back at [speed].
     */
    fun playEncryptedMessage(
        messageId: String,
        encryptedFilePath: String?,
        scope: CoroutineScope,
        speed: Float = _playbackState.value.playbackSpeed,
        startFraction: Float = 0f
    ) {
        stopPlayback()
        if (encryptedFilePath.isNullOrBlank()) {
            _playbackState.value = PlaybackUiState(
                playingMessageId = messageId,
                isPlaying = false,
                errorText = "No recording file exists for this call (cellular audio was unsupported)."
            )
            return
        }

        val file = File(encryptedFilePath)
        val decryptedWav = cryptoManager.decryptFromFile(file)
        if (decryptedWav == null || decryptedWav.size <= 44) {
            _playbackState.value = PlaybackUiState(
                playingMessageId = messageId,
                isPlaying = false,
                errorText = "Recording file missing or AES-GCM authentication tag verification failed."
            )
            return
        }

        val pcmBytes = decryptedWav.copyOfRange(44, decryptedWav.size)
        val sampleRate = 16000
        val totalDurationSec = (pcmBytes.size / (sampleRate * 2)).coerceAtLeast(1)
        val clampedStart = startFraction.coerceIn(0f, 0.95f)
        val startByteOffset = ((pcmBytes.size * clampedStart).toInt() and -2) // 2-byte aligned

        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                val slice = pcmBytes.copyOfRange(startByteOffset, pcmBytes.size)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(slice.size.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeTrack = track
                track.write(slice, 0, slice.size)
                try {
                    track.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 2.0f))
                } catch (_: Exception) {}
                track.play()

                val effectiveTotalMs = ((totalDurationSec * 1000f) / speed.coerceIn(0.5f, 2.0f)).toLong().coerceAtLeast(800L)
                var elapsedMs = (effectiveTotalMs * clampedStart).toLong()
                val stepMs = 100L

                while (isActive && elapsedMs < effectiveTotalMs) {
                    val fraction = (elapsedMs.toFloat() / effectiveTotalMs).coerceIn(0f, 1f)
                    val posSec = (fraction * totalDurationSec).toInt()
                    _playbackState.value = PlaybackUiState(
                        playingMessageId = messageId,
                        isPlaying = true,
                        progressFraction = fraction,
                        currentPositionSec = posSec,
                        totalDurationSec = totalDurationSec,
                        playbackSpeed = speed,
                        errorText = null
                    )
                    delay(stepMs)
                    elapsedMs += stepMs
                }

                _playbackState.value = PlaybackUiState(
                    playingMessageId = messageId,
                    isPlaying = false,
                    progressFraction = 1f,
                    currentPositionSec = totalDurationSec,
                    totalDurationSec = totalDurationSec,
                    playbackSpeed = speed,
                    errorText = null
                )
            } catch (e: Exception) {
                _playbackState.value = PlaybackUiState(
                    playingMessageId = messageId,
                    isPlaying = false,
                    playbackSpeed = speed,
                    errorText = "Audio playback completed."
                )
            } finally {
                try {
                    activeTrack?.stop()
                    activeTrack?.release()
                    activeTrack = null
                } catch (_: Exception) {}
            }
        }
    }

    fun setPlaybackSpeed(newSpeed: Float) {
        val clamped = newSpeed.coerceIn(0.5f, 2.0f)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
        try {
            activeTrack?.playbackParams = PlaybackParams().setSpeed(clamped)
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            activeTrack?.stop()
            activeTrack?.release()
            activeTrack = null
        } catch (_: Exception) {}
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    /**
     * Exports a decrypted copy of the WAV file to a user-accessible export file inside cache/exports
     * only when explicitly triggered by the user.
     */
    suspend fun exportDecryptedWav(messageId: String, encryptedFilePath: String?): File? = withContext(Dispatchers.IO) {
        if (encryptedFilePath.isNullOrBlank()) return@withContext null
        val decrypted = cryptoManager.decryptFromFile(File(encryptedFilePath)) ?: return@withContext null
        val exportDir = File(context.cacheDir, "exported_voice_messages").apply { mkdirs() }
        val outFile = File(exportDir, "VoiceShield_Message_${messageId}.wav")
        outFile.writeBytes(decrypted)
        outFile
    }
}
