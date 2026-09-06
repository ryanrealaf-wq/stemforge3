package com.example.audiomidi.api.synth

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.audiomidi.api.model.MidiNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-performance polyphonic software synthesizer using Android AudioTrack.
 * Synthesizes transcribed MIDI notes in real time with harmonic overtones and ADSR envelope.
 */
class MidiSynthPlayer(
    private val sampleRate: Int = 44100
) {
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var playbackPositionMs: Long = 0L
        private set

    var volume: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            audioTrack?.setVolume(field)
        }

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.setVolume(volume)
    }

    /**
     * Plays a single MIDI note briefly (audition preview, e.g. when tapped on Piano Roll).
     */
    fun auditionNote(midiNote: Int, durationMs: Long = 400) {
        scope.launch {
            val freq = MidiNote.midiToFreq(midiNote)
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcm = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Fundamental + rich harmonics (piano/rhodes-like timbre)
                val s1 = sin(2.0 * PI * freq * t)
                val s2 = 0.45 * sin(4.0 * PI * freq * t)
                val s3 = 0.20 * sin(6.0 * PI * freq * t)
                val raw = (s1 + s2 + s3) / 1.65

                // Exponential decay envelope
                val env = exp(-3.5 * t)
                pcm[i] = (raw * env * 26000.0).toInt().toShort()
            }

            try {
                audioTrack?.play()
                audioTrack?.write(pcm, 0, pcm.size)
            } catch (_: Exception) {}
        }
    }

    /**
     * Synthesizes and plays the full sequence of transcribed MIDI notes.
     */
    fun playNotes(
        notes: List<MidiNote>,
        totalDurationMs: Long,
        startOffsetMs: Long = 0L,
        onProgress: ((Long) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stop()
        if (notes.isEmpty() || totalDurationMs <= 0) {
            onComplete?.invoke()
            return
        }

        isPlaying = true
        playbackPositionMs = startOffsetMs

        playJob = scope.launch {
            try {
                audioTrack?.play()

                // Render in small streaming chunks (~50ms) for low latency and smooth progress
                val chunkDurationMs = 50L
                val samplesPerChunk = (sampleRate * (chunkDurationMs / 1000.0)).toInt()
                val chunkBuffer = ShortArray(samplesPerChunk)
                val floatMix = FloatArray(samplesPerChunk)

                var currentMs = startOffsetMs

                while (isActive && currentMs < totalDurationMs && isPlaying) {
                    playbackPositionMs = currentMs
                    onProgress?.invoke(currentMs)

                    floatMix.fill(0f)
                    val chunkEndMs = currentMs + chunkDurationMs

                    // Find notes active in this chunk window
                    for (note in notes) {
                        if (note.endTimeMs < currentMs || note.startTimeMs > chunkEndMs) continue

                        val freq = note.frequencyHz.coerceIn(20f, 10000f)
                        val velNorm = note.velocity / 127f

                        for (i in 0 until samplesPerChunk) {
                            val sampleMs = currentMs + (i * 1000L / sampleRate)
                            if (sampleMs in note.startTimeMs until note.endTimeMs) {
                                val tNote = (sampleMs - note.startTimeMs) / 1000.0
                                val tTotal = sampleMs / 1000.0

                                // Synth harmonics
                                val s1 = sin(2.0 * PI * freq * tTotal)
                                val s2 = 0.40 * sin(4.0 * PI * freq * tTotal)
                                val s3 = 0.15 * sin(6.0 * PI * freq * tTotal)
                                val wave = (s1 + s2 + s3).toFloat()

                                // ADSR envelope: 15ms attack, gradual decay
                                val attackTime = 0.015
                                val env = if (tNote < attackTime) {
                                    (tNote / attackTime).toFloat()
                                } else {
                                    exp(-1.5 * (tNote - attackTime)).toFloat()
                                }

                                floatMix[i] += wave * env * velNorm * 0.45f
                            }
                        }
                    }

                    // Convert mixed floats to 16-bit PCM with soft saturation clipping
                    for (i in 0 until samplesPerChunk) {
                        val clamped = floatMix[i].coerceIn(-1.0f, 1.0f)
                        chunkBuffer[i] = (clamped * 32000f).toInt().toShort()
                    }

                    audioTrack?.write(chunkBuffer, 0, samplesPerChunk)
                    currentMs += chunkDurationMs
                }

                if (isActive && isPlaying) {
                    onProgress?.invoke(totalDurationMs)
                    onComplete?.invoke()
                }
            } catch (_: Exception) {
            } finally {
                isPlaying = false
            }
        }
    }

    fun pause() {
        isPlaying = false
        playJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    fun stop() {
        isPlaying = false
        playJob?.cancel()
        playbackPositionMs = 0L
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
