package com.example.audiomidi.api.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Real-time microphone audio recorder capturing 16-bit PCM audio.
 */
class MicrophoneRecorder(
    private val sampleRate: Int = 22050
) {
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Starts recording live audio from microphone.
     *
     * @param onRmsUpdate Callback providing live audio RMS level (0.0 to 1.0) and elapsed milliseconds.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        onRmsUpdate: ((rms: Float, elapsedMs: Long) -> Unit)? = null
    ) {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, 2048)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        audioRecord?.startRecording()
        isRecording = true

        val capturedBytes = ByteArrayOutputStream()
        val readBuffer = ShortArray(1024)
        val startTime = System.currentTimeMillis()

        recordJob = scope.launch {
            try {
                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (readCount > 0) {
                        // Calculate RMS level
                        var sumSq = 0.0
                        for (i in 0 until readCount) {
                            val sample = readBuffer[i] / 32768.0f
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / readCount).toFloat().coerceIn(0f, 1f)

                        // Write to byte stream
                        val byteBuf = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until readCount) {
                            byteBuf.putShort(readBuffer[i])
                        }
                        capturedBytes.write(byteBuf.array())

                        val elapsed = System.currentTimeMillis() - startTime
                        onRmsUpdate?.invoke(rms, elapsed)
                    }
                }
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    /**
     * Stops recording and returns captured audio as normalized float samples (-1.0 to 1.0).
     */
    fun stopRecording(): FloatArray {
        isRecording = false
        recordJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        return FloatArray(0)
    }

    /**
     * Full capture session helper that provides the recorded FloatArray on completion.
     */
    class Session(val sampleRate: Int = 22050) {
        private var audioRecord: AudioRecord? = null
        private var recordJob: Job? = null
        private val scope = CoroutineScope(Dispatchers.IO)
        private val pcmChunks = ArrayList<ShortArray>()

        @Volatile
        var isRecording: Boolean = false
            private set

        @SuppressLint("MissingPermission")
        fun start(onRms: ((Float, Long) -> Unit)?) {
            pcmChunks.clear()
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, 2048)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            audioRecord?.startRecording()
            isRecording = true
            val startTime = System.currentTimeMillis()

            recordJob = scope.launch {
                val buffer = ShortArray(1024)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val chunk = ShortArray(read)
                        System.arraycopy(buffer, 0, chunk, 0, read)
                        synchronized(pcmChunks) {
                            pcmChunks.add(chunk)
                        }

                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val norm = buffer[i] / 32768f
                            sumSq += norm * norm
                        }
                        val rms = sqrt(sumSq / read).toFloat()
                        val elapsed = System.currentTimeMillis() - startTime
                        onRms?.invoke(rms, elapsed)
                    }
                }
            }
        }

        fun stop(): FloatArray {
            isRecording = false
            recordJob?.cancel()
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null

            val totalSamples = synchronized(pcmChunks) {
                pcmChunks.sumOf { it.size }
            }

            val result = FloatArray(totalSamples)
            var offset = 0
            synchronized(pcmChunks) {
                for (chunk in pcmChunks) {
                    for (i in chunk.indices) {
                        result[offset + i] = chunk[i] / 32768.0f
                    }
                    offset += chunk.size
                }
            }
            return result
        }
    }
}
