package com.example.audiomidi.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.audiomidi.api.dsp.AudioDecoder
import com.example.audiomidi.api.dsp.AudioToMidiEngine
import com.example.audiomidi.api.model.ConversionConfig
import com.example.audiomidi.api.model.ConversionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Public packaged API interface for Audio-to-MIDI transcription on Android.
 * Integrates directly into third-party Android music production, DAW, and audio analysis apps.
 */
interface AudioToMidiApi {

    /**
     * Transcribes raw PCM float audio samples into a structured ConversionResult with notes and SMF bytes.
     */
    suspend fun convertAudio(
        samples: FloatArray,
        sampleRate: Int,
        config: ConversionConfig = ConversionConfig(),
        onProgress: ((Float, String) -> Unit)? = null
    ): ConversionResult

    /**
     * Decodes and transcribes an audio file on disk (WAV, MP3, M4A, OGG, FLAC).
     */
    suspend fun convertFile(
        file: File,
        config: ConversionConfig = ConversionConfig(),
        onProgress: ((Float, String) -> Unit)? = null
    ): ConversionResult

    /**
     * Decodes and transcribes audio from an Android content URI.
     */
    suspend fun convertUri(
        context: Context,
        uri: Uri,
        config: ConversionConfig = ConversionConfig(),
        onProgress: ((Float, String) -> Unit)? = null
    ): ConversionResult

    /**
     * Exports conversion result as a .mid Standard MIDI File to the specified destination.
     */
    fun exportMidiFile(result: ConversionResult, destination: File): Boolean

    /**
     * Saves result to internal application cache directory.
     */
    fun saveToCache(context: Context, result: ConversionResult, fileName: String = "transcription.mid"): File

    /**
     * Launches the Android system share sheet to send the MIDI file to DAWs or cloud drives.
     */
    fun shareMidi(context: Context, midiFile: File, title: String = "Share Transcribed MIDI")
}

/**
 * Production-ready singleton implementation of [AudioToMidiApi].
 */
class AudioToMidiConverter private constructor() : AudioToMidiApi {

    private val engine = AudioToMidiEngine()

    override suspend fun convertAudio(
        samples: FloatArray,
        sampleRate: Int,
        config: ConversionConfig,
        onProgress: ((Float, String) -> Unit)?
    ): ConversionResult {
        return engine.transcribe(samples, sampleRate, config, onProgress)
    }

    override suspend fun convertFile(
        file: File,
        config: ConversionConfig,
        onProgress: ((Float, String) -> Unit)?
    ): ConversionResult = withContext(Dispatchers.Default) {
        onProgress?.invoke(0.02f, "Decoding media container ${file.name}...")
        val decoded = AudioDecoder.decodeFile(file, config.sampleRate) { decProg ->
            onProgress?.invoke(0.02f + decProg * 0.10f, "Decoding audio ${file.name} (${(decProg * 100).toInt()}%)")
        }

        engine.transcribe(decoded.samples, decoded.sampleRate, config, onProgress)
    }

    override suspend fun convertUri(
        context: Context,
        uri: Uri,
        config: ConversionConfig,
        onProgress: ((Float, String) -> Unit)?
    ): ConversionResult = withContext(Dispatchers.Default) {
        onProgress?.invoke(0.02f, "Decoding audio stream...")
        val decoded = AudioDecoder.decodeUri(context, uri, config.sampleRate) { decProg ->
            onProgress?.invoke(0.02f + decProg * 0.10f, "Decoding audio stream (${(decProg * 100).toInt()}%)")
        }

        engine.transcribe(decoded.samples, decoded.sampleRate, config, onProgress)
    }

    override fun exportMidiFile(result: ConversionResult, destination: File): Boolean {
        return try {
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { fos ->
                fos.write(result.midiFileBytes)
                fos.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun saveToCache(context: Context, result: ConversionResult, fileName: String): File {
        val cacheDir = File(context.cacheDir, "midi_exports").apply { mkdirs() }
        val sanitized = if (fileName.endsWith(".mid", ignoreCase = true)) fileName else "$fileName.mid"
        val file = File(cacheDir, sanitized)
        exportMidiFile(result, file)
        return file
    }

    override fun shareMidi(context: Context, midiFile: File, title: String) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                midiFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/midi"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, midiFile.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: AudioToMidiConverter? = null

        fun getInstance(): AudioToMidiConverter {
            return instance ?: synchronized(this) {
                instance ?: AudioToMidiConverter().also { instance = it }
            }
        }
    }
}
