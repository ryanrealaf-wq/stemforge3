package com.example.audiomidi.api.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal Android Audio Decoder using native MediaExtractor and MediaCodec.
 * Decodes MP3, WAV, M4A, AAC, OGG, and FLAC streams into normalized mono PCM float buffers.
 */
object AudioDecoder {

    data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DecodedAudio
            if (sampleRate != other.sampleRate) return false
            if (durationMs != other.durationMs) return false
            if (!samples.contentEquals(other.samples)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    /**
     * Decodes audio from an Android content URI.
     */
    suspend fun decodeUri(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = 22050,
        onProgress: ((Float) -> Unit)? = null
    ): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            decodeExtractor(extractor, targetSampleRate, onProgress)
        } finally {
            extractor.release()
        }
    }

    /**
     * Decodes audio from a File.
     */
    suspend fun decodeFile(
        file: File,
        targetSampleRate: Int = 22050,
        onProgress: ((Float) -> Unit)? = null
    ): DecodedAudio = withContext(Dispatchers.IO) {
        // Check if plain WAV file first for ultra-fast reading
        if (file.name.endsWith(".wav", ignoreCase = true)) {
            val fastWav = tryReadWavDirectly(file, targetSampleRate)
            if (fastWav != null) return@withContext fastWav
        }

        val extractor = MediaExtractor()
        try {
            val fis = FileInputStream(file)
            extractor.setDataSource(fis.fd)
            fis.close()
            decodeExtractor(extractor, targetSampleRate, onProgress)
        } finally {
            extractor.release()
        }
    }

    /**
     * Core decoding pipeline using MediaCodec.
     */
    private fun decodeExtractor(
        extractor: MediaExtractor,
        targetSampleRate: Int,
        onProgress: ((Float) -> Unit)?
    ): DecodedAudio {
        var audioTrackIndex = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            throw IllegalArgumentException("No audio track found in media container.")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
        val originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmList = ArrayList<FloatArray>()
        var totalRawSamples = 0L

        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false
        val timeoutUs = 5000L

        try {
            while (true) {
                if (!isEOS) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()

                                if (durationUs > 0 && onProgress != null) {
                                    val prog = (sampleTime.toFloat() / durationUs).coerceIn(0f, 0.9f)
                                    onProgress(prog)
                                }
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortCount = bufferInfo.size / 2
                        val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                        val monoSamples = if (channelCount == 1) {
                            FloatArray(shortCount).also { arr ->
                                for (i in 0 until shortCount) {
                                    arr[i] = shortBuffer.get() / 32768.0f
                                }
                            }
                        } else {
                            val monoCount = shortCount / channelCount
                            FloatArray(monoCount).also { arr ->
                                for (i in 0 until monoCount) {
                                    var sum = 0f
                                    for (c in 0 until channelCount) {
                                        sum += shortBuffer.get() / 32768.0f
                                    }
                                    arr[i] = sum / channelCount
                                }
                            }
                        }

                        pcmList.add(monoSamples)
                        totalRawSamples += monoSamples.size
                    }

                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Handled automatically
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }

        // Concatenate all decoded chunks
        val combined = FloatArray(totalRawSamples.toInt())
        var offset = 0
        for (chunk in pcmList) {
            System.arraycopy(chunk, 0, combined, offset, chunk.size)
            offset += chunk.size
        }

        // Resample if needed
        val finalSamples = if (originalSampleRate != targetSampleRate && originalSampleRate > 0) {
            resampleLinear(combined, originalSampleRate, targetSampleRate)
        } else {
            combined
        }

        val durationMs = if (targetSampleRate > 0) {
            (finalSamples.size.toDouble() * 1000.0 / targetSampleRate).toLong()
        } else {
            0L
        }

        onProgress?.invoke(1.0f)
        return DecodedAudio(finalSamples, targetSampleRate, durationMs)
    }

    /**
     * Direct parser for standard 16-bit PCM WAV files without MediaCodec overhead.
     */
    private fun tryReadWavDirectly(file: File, targetSampleRate: Int): DecodedAudio? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null
            val riff = String(bytes, 0, 4)
            val wave = String(bytes, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") return null

            var ptr = 12
            var sampleRate = 44100
            var channels = 1
            var bitsPerSample = 16
            var dataOffset = 44
            var dataSize = 0

            while (ptr + 8 <= bytes.size) {
                val chunkId = String(bytes, ptr, 4)
                val chunkSize = ByteBuffer.wrap(bytes, ptr + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (chunkId == "fmt ") {
                    val audioFormat = ByteBuffer.wrap(bytes, ptr + 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    if (audioFormat != 1) return null // Only PCM supported
                    channels = ByteBuffer.wrap(bytes, ptr + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    sampleRate = ByteBuffer.wrap(bytes, ptr + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample = ByteBuffer.wrap(bytes, ptr + 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                } else if (chunkId == "data") {
                    dataOffset = ptr + 8
                    dataSize = chunkSize
                    break
                }
                ptr += 8 + chunkSize
            }

            if (dataSize <= 0 || bitsPerSample != 16) return null

            val numSamples = dataSize / (2 * channels)
            val mono = FloatArray(numSamples)
            val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until numSamples) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += bb.short / 32768f
                }
                mono[i] = sum / channels
            }

            val resampled = if (sampleRate != targetSampleRate) {
                resampleLinear(mono, sampleRate, targetSampleRate)
            } else {
                mono
            }
            val durationMs = (resampled.size * 1000L) / targetSampleRate
            DecodedAudio(resampled, targetSampleRate, durationMs)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fast linear interpolation resampler.
     */
    private fun resampleLinear(input: FloatArray, inRate: Int, outRate: Int): FloatArray {
        if (inRate == outRate || input.isEmpty()) return input
        val ratio = inRate.toDouble() / outRate.toDouble()
        val outLen = (input.size / ratio).toInt()
        val output = FloatArray(outLen)

        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()

            val s0 = input[srcIdx.coerceIn(0, input.size - 1)]
            val s1 = input[(srcIdx + 1).coerceIn(0, input.size - 1)]
            output[i] = s0 + frac * (s1 - s0)
        }
        return output
    }
}
