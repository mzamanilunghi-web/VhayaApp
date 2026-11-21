package com.wayfare.ride.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

enum class NotificationSoundStyle(
    val key: String,
    val label: String,
    val subtitle: String
) {
    CAR_HORN("car_horn", "Car horn", "Friendly dual-tone beep-beep"),
    CAR_CHIME("car_chime", "Cabin chime", "Smart-car lock & cabin chime"),
    CLASSIC("classic", "Classic", "Standard two-tone chime");

    companion object {
        fun fromKey(key: String?): NotificationSoundStyle =
            entries.firstOrNull { it.key == key } ?: CAR_HORN
    }
}

object SoundAlertPlayer {
    private const val SAMPLE_RATE = 22_050
    private const val PREFS_NAME = "vhaya_session_prefs"
    private const val KEY_SOUND_STYLE = "notification_sound_style"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isPlaying = AtomicBoolean(false)

    fun resolveSavedStyle(context: Context): NotificationSoundStyle {
        val raw = runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SOUND_STYLE, null)
        }.getOrNull()
        return NotificationSoundStyle.fromKey(raw)
    }

    fun playRideOfferChime(
        context: Context,
        style: NotificationSoundStyle? = null
    ) {
        val resolved = style ?: resolveSavedStyle(context)
        val pcm = when (resolved) {
            NotificationSoundStyle.CAR_HORN -> generateCarHornPcm(gentle = false)
            NotificationSoundStyle.CAR_CHIME -> generateCarChimePcm()
            NotificationSoundStyle.CLASSIC -> generateChimePcm(firstFreqHz = 880.0, secondFreqHz = 1174.66)
        }
        playNotesInternal(context = context.applicationContext, pcm = pcm)
    }

    fun playStatusUpdateChime(
        context: Context,
        style: NotificationSoundStyle? = null
    ) {
        val resolved = style ?: resolveSavedStyle(context)
        val pcm = when (resolved) {
            NotificationSoundStyle.CAR_HORN -> generateCarHornPcm(gentle = true)
            NotificationSoundStyle.CAR_CHIME -> generateCarChimePcm()
            NotificationSoundStyle.CLASSIC -> generateChimePcm(firstFreqHz = 659.25, secondFreqHz = 987.77)
        }
        playNotesInternal(context = context.applicationContext, pcm = pcm)
    }

    fun playPreview(context: Context, style: NotificationSoundStyle) {
        isPlaying.set(false)
        playRideOfferChime(context = context, style = style)
    }

    fun playSuccessCelebrationChime(context: Context) {
        playNotesInternal(
            context = context.applicationContext,
            pcm = generateCelebrationChimePcm()
        )
    }

    internal fun generateCarHornPcm(gentle: Boolean = false): ShortArray {
        val tap1Samples = (SAMPLE_RATE * if (gentle) 0.075 else 0.095).toInt()
        val gapSamples = (SAMPLE_RATE * if (gentle) 0.050 else 0.060).toInt()
        val tap2Samples = (SAMPLE_RATE * if (gentle) 0.130 else 0.175).toInt()
        val total = tap1Samples + gapSamples + tap2Samples
        val pcm = ShortArray(total)

        fillHornPulse(
            buffer = pcm,
            start = 0,
            count = tap1Samples,
            amplitude = if (gentle) 0.56 else 0.74
        )
        fillHornPulse(
            buffer = pcm,
            start = tap1Samples + gapSamples,
            count = tap2Samples,
            amplitude = if (gentle) 0.62 else 0.80
        )
        return pcm
    }

    internal fun generateCarChimePcm(): ShortArray {
        val chirpSamples = (SAMPLE_RATE * 0.060).toInt()
        val gapSamples = (SAMPLE_RATE * 0.045).toInt()
        val chordSamples = (SAMPLE_RATE * 0.220).toInt()
        val pcm = ShortArray(chirpSamples + gapSamples + chordSamples)

        fillCabinChirp(
            buffer = pcm,
            start = 0,
            count = chirpSamples,
            freqs = doubleArrayOf(1046.50, 1567.98),
            amplitude = 0.62,
            decayRate = 18.0
        )
        fillCabinChirp(
            buffer = pcm,
            start = chirpSamples + gapSamples,
            count = chordSamples,
            freqs = doubleArrayOf(523.25, 659.25),
            amplitude = 0.74,
            decayRate = 6.5
        )
        return pcm
    }

    internal fun generateChimePcm(
        firstFreqHz: Double = 880.0,
        secondFreqHz: Double = 1174.66
    ): ShortArray {
        val note1Samples = (SAMPLE_RATE * 0.20).toInt()
        val gapSamples = (SAMPLE_RATE * 0.04).toInt()
        val note2Samples = (SAMPLE_RATE * 0.30).toInt()
        val total = note1Samples + gapSamples + note2Samples
        val pcm = ShortArray(total)

        fillTone(pcm, start = 0, count = note1Samples, freqHz = firstFreqHz, amplitude = 0.55)
        fillTone(
            pcm,
            start = note1Samples + gapSamples,
            count = note2Samples,
            freqHz = secondFreqHz,
            amplitude = 0.65
        )
        return pcm
    }

    internal fun generateCelebrationChimePcm(): ShortArray {
        val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5 -> E5 -> G5 -> C6
        val durationsSec = doubleArrayOf(0.085, 0.085, 0.095, 0.23)
        val amplitudes = doubleArrayOf(0.45, 0.50, 0.56, 0.68)
        val gapSamples = (SAMPLE_RATE * 0.025).toInt()

        val noteSamples = durationsSec.map { (SAMPLE_RATE * it).toInt() }
        val total = noteSamples.sum() + gapSamples * (freqs.size - 1)
        val pcm = ShortArray(total)

        var offset = 0
        for (idx in freqs.indices) {
            val count = noteSamples[idx]
            fillTone(pcm, start = offset, count = count, freqHz = freqs[idx], amplitude = amplitudes[idx])
            offset += count + gapSamples
        }
        return pcm
    }

    private fun hornWave(t: Double, freqHz: Double): Double {
        val raw = (
            1.00 * sin(2.0 * PI * freqHz * t) +
                0.58 * sin(2.0 * PI * (freqHz * 2.0) * t) +
                0.36 * sin(2.0 * PI * (freqHz * 3.0) * t) +
                0.20 * sin(2.0 * PI * (freqHz * 4.0) * t) +
                0.10 * sin(2.0 * PI * (freqHz * 5.0) * t)
            ) / 2.24
        return tanh(1.30 * raw)
    }

    private fun fillHornPulse(
        buffer: ShortArray,
        start: Int,
        count: Int,
        amplitude: Double
    ) {
        val attackSamples = (SAMPLE_RATE * 0.008).toInt().coerceAtLeast(1)
        val releaseSamples = (SAMPLE_RATE * 0.022).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val aEnv = if (i < attackSamples) 0.5 * (1.0 - cos(PI * i / attackSamples)) else 1.0
            val rEnv = if (i >= count - releaseSamples) {
                0.5 * (1.0 - cos(PI * (count - 1 - i) / releaseSamples))
            } else 1.0
            val env = min(aEnv, rEnv).coerceIn(0.0, 1.0)
            // Classic major-third dual-tone automotive horn (415.30 Hz G#4 + 523.25 Hz C5)
            val sig = 0.52 * hornWave(t, 415.30) + 0.48 * hornWave(t, 523.25)
            val sample = (sig * env * amplitude * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[start + i] = sample.toShort()
        }
    }

    private fun fillCabinChirp(
        buffer: ShortArray,
        start: Int,
        count: Int,
        freqs: DoubleArray,
        amplitude: Double,
        decayRate: Double
    ) {
        val attackSamples = (SAMPLE_RATE * 0.006).toInt().coerceAtLeast(1)
        val releaseSamples = (SAMPLE_RATE * 0.025).toInt().coerceAtLeast(1)
        val denom = 1.38 * freqs.size
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val aEnv = if (i < attackSamples) 0.5 * (1.0 - cos(PI * i / attackSamples)) else 1.0
            val rEnv = if (i >= count - releaseSamples) {
                0.5 * (1.0 - cos(PI * (count - 1 - i) / releaseSamples))
            } else 1.0
            val expEnv = exp(-decayRate * t)
            val env = (min(aEnv, rEnv) * expEnv).coerceIn(0.0, 1.0)
            var sig = 0.0
            for (f in freqs) {
                sig += (
                    1.00 * sin(2.0 * PI * f * t) +
                        0.28 * sin(2.0 * PI * (f * 2.0) * t) +
                        0.10 * sin(2.0 * PI * (f * 3.0) * t)
                    ) / denom
            }
            val sample = (sig * env * amplitude * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[start + i] = sample.toShort()
        }
    }

    private fun fillTone(
        buffer: ShortArray,
        start: Int,
        count: Int,
        freqHz: Double,
        amplitude: Double
    ) {
        val fadeSamples = min(count / 4, (SAMPLE_RATE * 0.025).toInt()).coerceAtLeast(1)
        for (i in 0 until count) {
            val attack = if (i < fadeSamples) i.toDouble() / fadeSamples else 1.0
            val release = if (i > count - fadeSamples) (count - i).toDouble() / fadeSamples else 1.0
            val env = min(attack, release).coerceIn(0.0, 1.0)
            val fundamental = sin(2.0 * PI * freqHz * i / SAMPLE_RATE)
            val harmonic = 0.25 * sin(2.0 * PI * (freqHz * 2.0) * i / SAMPLE_RATE)
            val sample = ((fundamental + harmonic) * 0.8 * amplitude * env * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[start + i] = sample.toShort()
        }
    }

    private fun playNotesInternal(
        context: Context,
        pcm: ShortArray
    ) {
        if (!isPlaying.compareAndSet(false, true)) return
        scope.launch {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val mediaVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                val notifVolume = audioManager?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 0
                val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

                // Respect hard silent mode when media is also muted
                if (ringerMode == AudioManager.RINGER_MODE_SILENT && mediaVolume <= 0) {
                    return@launch
                }

                // Prefer media stream when media volume is audible so speaker wake-up and in-app chimes work reliably;
                // fall back to notification usage when media is muted and notification volume is active.
                val useMediaStream = mediaVolume > 0 || notifVolume <= 0
                val usage = if (useMediaStream) {
                    AudioAttributes.USAGE_MEDIA
                } else {
                    AudioAttributes.USAGE_NOTIFICATION_EVENT
                }

                val played = playViaAudioTrack(pcm, usage)
                if (!played) {
                    playViaFallback(context, useMediaStream)
                }
            } catch (_: Throwable) {
                // Never crash caller on audio device unavailability
            } finally {
                isPlaying.set(false)
            }
        }
    }

    private fun playViaAudioTrack(
        pcm: ShortArray,
        usage: Int
    ): Boolean {
        return runCatching {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufBytes <= 0) return false
            val pcmBytes = pcm.size * 2
            val bufferSize = maxOf(minBufBytes, pcmBytes)
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                val written = track.write(pcm, 0, pcm.size)
                if (written <= 0) return false
                track.play()
                val durationMs = ((pcm.size * 1000L) / SAMPLE_RATE) + 80L
                Thread.sleep(durationMs)
                true
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.getOrDefault(false)
    }

    private fun playViaFallback(context: Context, useMediaStream: Boolean) {
        val playedRingtone = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@runCatching false
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@runCatching false
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(
                    if (useMediaStream) AudioAttributes.USAGE_MEDIA
                    else AudioAttributes.USAGE_NOTIFICATION_EVENT
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            true
        }.getOrDefault(false)

        if (!playedRingtone) {
            runCatching {
                val stream = if (useMediaStream) AudioManager.STREAM_MUSIC else AudioManager.STREAM_NOTIFICATION
                val tg = ToneGenerator(stream, 90)
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
                    Thread.sleep(450L)
                } finally {
                    tg.release()
                }
            }
        }
    }
}
