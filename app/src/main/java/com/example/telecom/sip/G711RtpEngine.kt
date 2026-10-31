package com.example.telecom.sip

import com.example.ai.tts.LocalTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * RFC 3550 RTP Packet representation for SIP/VoIP media streams.
 */
data class RtpPacket(
    val payloadType: Int,
    val sequenceNumber: Int,
    val timestamp: Long,
    val ssrc: Long,
    val marker: Boolean,
    val payload: ByteArray
) {
    fun toByteArray(): ByteArray {
        val header = ByteArray(12)
        header[0] = 0x80.toByte() // Version 2, P=0, X=0, CC=0
        header[1] = ((if (marker) 0x80 else 0x00) or (payloadType and 0x7F)).toByte()
        header[2] = ((sequenceNumber ushr 8) and 0xFF).toByte()
        header[3] = (sequenceNumber and 0xFF).toByte()
        header[4] = ((timestamp ushr 24) and 0xFF).toByte()
        header[5] = ((timestamp ushr 16) and 0xFF).toByte()
        header[6] = ((timestamp ushr 8) and 0xFF).toByte()
        header[7] = (timestamp and 0xFF).toByte()
        header[8] = ((ssrc ushr 24) and 0xFF).toByte()
        header[9] = ((ssrc ushr 16) and 0xFF).toByte()
        header[10] = ((ssrc ushr 8) and 0xFF).toByte()
        header[11] = (ssrc and 0xFF).toByte()

        val raw = ByteArray(12 + payload.size)
        System.arraycopy(header, 0, raw, 0, 12)
        System.arraycopy(payload, 0, raw, 12, payload.size)
        return raw
    }

    companion object {
        const val PT_PCMU = 0
        const val PT_PCMA = 8
        const val PT_TELEPHONE_EVENT_DTMF = 101

        fun parse(raw: ByteArray, length: Int = raw.size): RtpPacket? {
            if (length < 12) return null
            val version = (raw[0].toInt() ushr 6) and 0x03
            if (version != 2) return null
            val csrcCount = raw[0].toInt() and 0x0F
            val headerLen = 12 + csrcCount * 4
            if (length < headerLen) return null

            val marker = (raw[1].toInt() and 0x80) != 0
            val pt = raw[1].toInt() and 0x7F
            val seq = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
            val ts = ((raw[4].toLong() and 0xFF) shl 24) or
                ((raw[5].toLong() and 0xFF) shl 16) or
                ((raw[6].toLong() and 0xFF) shl 8) or
                (raw[7].toLong() and 0xFF)
            val ssrc = ((raw[8].toLong() and 0xFF) shl 24) or
                ((raw[9].toLong() and 0xFF) shl 16) or
                ((raw[10].toLong() and 0xFF) shl 8) or
                (raw[11].toLong() and 0xFF)

            val payload = raw.copyOfRange(headerLen, length)
            return RtpPacket(pt, seq, ts, ssrc, marker, payload)
        }
    }
}

/**
 * ITU-T G.711 μ-law (PCMU, 8kHz) and A-law (PCMA, 8kHz) audio codec with
 * 16kHz <-> 8kHz resampling so local Sherpa-ONNX / Offline TTS 16kHz WAV buffers
 * can be injected directly into outgoing SIP RTP streams and incoming SIP RTP
 * caller audio can be captured to 16kHz WAV recordings.
 */
object G711Codec {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun linearToUlawSample(sampleIn: Short): Byte {
        var sample = sampleIn.toInt()
        val sign = (sample ushr 8) and 0x80
        if (sign != 0) sample = -sample
        if (sample > CLIP) sample = CLIP
        sample += BIAS

        var exponent = 7
        var expMask = 0x4000
        while (exponent > 0 && (sample and expMask) == 0) {
            exponent--
            expMask = expMask ushr 1
        }
        val mantissa = (sample ushr (exponent + 3)) and 0x0F
        val ulawByte = (sign or (exponent shl 4) or mantissa).inv()
        return (ulawByte and 0xFF).toByte()
    }

    fun ulawToLinearSample(ulawByteIn: Byte): Short {
        val u = ulawByteIn.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u ushr 4) and 0x07
        val mantissa = u and 0x0F
        var sample = ((mantissa shl 3) + BIAS) shl exponent
        sample -= BIAS
        return (if (sign != 0) -sample else sample).toShort()
    }

    /**
     * Downsamples 16kHz 16-bit PCM WAV bytes to 8kHz G.711 μ-law bytes for RTP transmission.
     */
    fun encodeWav16kToPcmu8k(wavBytes: ByteArray): ByteArray {
        val offset = if (wavBytes.size > 44 && String(wavBytes, 0, 4, Charsets.US_ASCII) == "RIFF") 44 else 0
        val pcmLen = wavBytes.size - offset
        val total16kSamples = pcmLen / 2
        val total8kSamples = total16kSamples / 2
        if (total8kSamples <= 0) return ByteArray(0)

        val buf = ByteBuffer.wrap(wavBytes, offset, pcmLen).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteArray(total8kSamples)
        for (i in 0 until total8kSamples) {
            val s1 = buf.getShort((i * 2) * 2).toInt()
            val s2 = buf.getShort((i * 2 + 1) * 2).toInt()
            val avg = ((s1 + s2) / 2).toShort()
            out[i] = linearToUlawSample(avg)
        }
        return out
    }

    /**
     * Decodes 8kHz G.711 μ-law payload bytes and upsamples to 16kHz 16-bit Little-Endian PCM bytes.
     */
    fun decodePcmu8kToPcm16k(pcmuBytes: ByteArray): Pair<ByteArray, Float> {
        if (pcmuBytes.isEmpty()) return ByteArray(0) to 0f
        val outBuf = ByteBuffer.allocate(pcmuBytes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        for (i in pcmuBytes.indices) {
            val s = ulawToLinearSample(pcmuBytes[i])
            // Duplicate sample for 8kHz -> 16kHz linear hold
            outBuf.putShort(s)
            outBuf.putShort(s)
            sumSq += s.toDouble() * s.toDouble()
        }
        val rms = sqrt(sumSq / pcmuBytes.size)
        val normAmp = (rms.toFloat() / 8000f).coerceIn(0.01f, 1.0f)
        return outBuf.array() to normAmp
    }
}

data class SipRtpCaptureResult(
    val success: Boolean,
    val capturedWav16kBytes: ByteArray?,
    val durationSeconds: Int,
    val packetsReceived: Int,
    val endedBySilenceTimeout: Boolean,
    val endedByRemoteBye: Boolean,
    val dtmfDigitsReceived: String,
    val detail: String
)

/**
 * Active two-way RTP Media Session bound to a local UDP port during a connected SIP call.
 * - Transmits synthesized TTS greeting audio directly into the remote SIP caller's RTP socket.
 * - Captures incoming RTP audio packets from the remote SIP caller's media stream (never the local mic!).
 */
class RtpMediaSession(
    val localRtpPort: Int = 0
) {
    private val socket = DatagramSocket(localRtpPort).apply {
        reuseAddress = true
        soTimeout = 200
    }
    val boundPort: Int = socket.localPort

    private var sequenceNumber = Random.nextInt(1000, 30000)
    private var rtpTimestamp = Random.nextLong(10000L, 900000L)
    private val ssrc = Random.nextLong(100_000L, 0x7FFFFFFFL)

    /**
     * Streams locally synthesized TTS WAV bytes into the outgoing SIP RTP stream as 20ms
     * (160-byte) G.711 PCMU packets to [remoteAddress]:[remoteRtpPort].
     */
    suspend fun transmitTtsWavToRemoteRtpStream(
        wavBytes: ByteArray,
        remoteAddress: InetAddress,
        remoteRtpPort: Int,
        fastLoopbackPacing: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val pcmuAll = G711Codec.encodeWav16kToPcmu8k(wavBytes)
        if (pcmuAll.isEmpty() || socket.isClosed) return@withContext 0

        val frameSize = 160 // 20ms at 8000 Hz
        var offset = 0
        var packetsSent = 0
        var isFirst = true

        while (offset < pcmuAll.size && !socket.isClosed) {
            val end = minOf(offset + frameSize, pcmuAll.size)
            val payload = ByteArray(frameSize) { idx ->
                if (offset + idx < end) pcmuAll[offset + idx] else 0xFF.toByte() // 0xFF = μ-law silence
            }
            val packet = RtpPacket(
                payloadType = RtpPacket.PT_PCMU,
                sequenceNumber = sequenceNumber++ and 0xFFFF,
                timestamp = rtpTimestamp,
                ssrc = ssrc,
                marker = isFirst,
                payload = payload
            )
            isFirst = false
            rtpTimestamp += frameSize

            val raw = packet.toByteArray()
            try {
                socket.send(DatagramPacket(raw, raw.size, remoteAddress, remoteRtpPort))
                packetsSent++
            } catch (_: Exception) {
                break
            }
            offset += frameSize
            delay(if (fastLoopbackPacing) 2L else 18L)
        }
        packetsSent
    }

    /**
     * Captures incoming RTP media packets directly from the remote SIP caller's media stream,
     * decodes G.711 PCMU to 16kHz PCM WAV, tracks RMS silence timeout and RFC 2833 DTMF digits,
     * and returns the recorded caller audio.
     */
    suspend fun captureRemoteSipCallerRtpStream(
        maxDurationSeconds: Int,
        silenceTimeoutSeconds: Int,
        isCallStillConnected: () -> Boolean,
        shouldStopEarly: () -> Boolean,
        onTick: (elapsedSec: Int, silenceSec: Int, amplitude: Float, packetsRx: Int) -> Unit
    ): SipRtpCaptureResult = withContext(Dispatchers.IO) {
        val pcm16kOut = ByteArrayOutputStream()
        val dtmfBuilder = StringBuilder()
        val rxBuf = ByteArray(1500)

        var packetsRx = 0
        var elapsedMs = 0L
        var consecutiveSilenceMs = 0L
        var endedBySilence = false
        var lastAmplitude = 0f

        val maxDurationMs = maxDurationSeconds.coerceIn(3, 180) * 1000L
        val silenceLimitMs = silenceTimeoutSeconds.coerceIn(2, 30) * 1000L
        val startWallMs = System.currentTimeMillis()

        while (elapsedMs < maxDurationMs && isCallStillConnected() && !shouldStopEarly() && !socket.isClosed) {
            try {
                val dp = DatagramPacket(rxBuf, rxBuf.size)
                socket.receive(dp)
                val rtp = RtpPacket.parse(dp.data, dp.length)
                if (rtp != null) {
                    when (rtp.payloadType) {
                        RtpPacket.PT_PCMU, RtpPacket.PT_PCMA -> {
                            packetsRx++
                            val (decodedPcm16k, amp) = G711Codec.decodePcmu8kToPcm16k(rtp.payload)
                            pcm16kOut.write(decodedPcm16k)
                            lastAmplitude = amp
                            if (amp < 0.07f) {
                                consecutiveSilenceMs += 20L
                            } else {
                                consecutiveSilenceMs = 0L
                            }
                        }
                        RtpPacket.PT_TELEPHONE_EVENT_DTMF -> {
                            if (rtp.payload.isNotEmpty()) {
                                val eventCode = rtp.payload[0].toInt() and 0xFF
                                val digit = when (eventCode) {
                                    in 0..9 -> ('0' + eventCode)
                                    10 -> '*'
                                    11 -> '#'
                                    else -> null
                                }
                                if (digit != null && dtmfBuilder.lastOrNull() != digit) {
                                    dtmfBuilder.append(digit)
                                }
                            }
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {
                // 200ms socket timeout without incoming RTP packet counts toward silence
                consecutiveSilenceMs += 200L
                lastAmplitude = 0.01f
            } catch (_: Exception) {
                break
            }

            elapsedMs = (System.currentTimeMillis() - startWallMs).coerceAtLeast(packetsRx * 20L)
            val elapsedSec = (elapsedMs / 1000L).toInt()
            val silenceSec = (consecutiveSilenceMs / 1000L).toInt()
            onTick(elapsedSec, silenceSec, lastAmplitude, packetsRx)

            if (consecutiveSilenceMs >= silenceLimitMs && packetsRx >= 25) {
                endedBySilence = true
                break
            }
        }

        val pcmBytes = pcm16kOut.toByteArray()
        val durationSec = ((pcmBytes.size / (16000 * 2)).coerceAtLeast(if (packetsRx > 0) 1 else 0))

        if (packetsRx == 0 || pcmBytes.isEmpty()) {
            return@withContext SipRtpCaptureResult(
                success = false,
                capturedWav16kBytes = null,
                durationSeconds = 0,
                packetsReceived = 0,
                endedBySilenceTimeout = endedBySilence,
                endedByRemoteBye = !isCallStillConnected(),
                dtmfDigitsReceived = dtmfBuilder.toString(),
                detail = "No incoming RTP audio packets received from remote SIP endpoint."
            )
        }

        val wavBytes = LocalTtsEngine.wrapPcmInWavHeader(pcmBytes, 16000, 1, 16)
        SipRtpCaptureResult(
            success = true,
            capturedWav16kBytes = wavBytes,
            durationSeconds = durationSec,
            packetsReceived = packetsRx,
            endedBySilenceTimeout = endedBySilence,
            endedByRemoteBye = !isCallStillConnected(),
            dtmfDigitsReceived = dtmfBuilder.toString(),
            detail = "Captured $packetsRx incoming RTP PCMU packets (${durationSec}s) from remote SIP caller stream."
        )
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
    }
}
