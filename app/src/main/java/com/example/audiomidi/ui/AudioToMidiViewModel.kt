package com.example.audiomidi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiomidi.api.AudioToMidiConverter
import com.example.audiomidi.api.audio.AudioPlayer
import com.example.audiomidi.api.audio.MicrophoneRecorder
import com.example.audiomidi.api.model.ConversionConfig
import com.example.audiomidi.api.model.ConversionResult
import com.example.audiomidi.api.model.MidiNote
import com.example.audiomidi.api.synth.MidiSynthPlayer
import com.example.audiomidi.data.AppDatabase
import com.example.audiomidi.data.ConversionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

data class ConversionUiState(
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f,
    val conversionStatusMessage: String = "",
    val conversionResult: ConversionResult? = null,
    val sourceAudioName: String = "",
    val sourceAudioDurationMs: Long = 0L,
    val rawSamples: FloatArray = FloatArray(0),
    val isRecordingMic: Boolean = false,
    val micRecordingElapsedMs: Long = 0L,
    val micRmsLevel: Float = 0f,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playOriginalAudio: Boolean = false,
    val playMidiSynth: Boolean = true,
    val synthVolume: Float = 0.85f,
    val audioVolume: Float = 0.85f,
    val errorMessage: String? = null,
    val savedMidiFile: File? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversionUiState
        if (isConverting != other.isConverting) return false
        if (conversionProgress != other.conversionProgress) return false
        if (conversionStatusMessage != other.conversionStatusMessage) return false
        if (conversionResult != other.conversionResult) return false
        if (sourceAudioName != other.sourceAudioName) return false
        if (isRecordingMic != other.isRecordingMic) return false
        if (isPlaying != other.isPlaying) return false
        if (playbackPositionMs != other.playbackPositionMs) return false
        if (playOriginalAudio != other.playOriginalAudio) return false
        if (playMidiSynth != other.playMidiSynth) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isConverting.hashCode()
        result = 31 * result + conversionProgress.hashCode()
        result = 31 * result + conversionStatusMessage.hashCode()
        result = 31 * result + (conversionResult?.hashCode() ?: 0)
        result = 31 * result + sourceAudioName.hashCode()
        return result
    }
}

class AudioToMidiViewModel(application: Application) : AndroidViewModel(application) {

    private val converter = AudioToMidiConverter.getInstance()
    private val database = AppDatabase.getInstance(application)
    private val synthPlayer = MidiSynthPlayer()
    private val audioPlayer = AudioPlayer(application)
    private var micSession: MicrophoneRecorder.Session? = null

    private val _uiState = MutableStateFlow(ConversionUiState())
    val uiState: StateFlow<ConversionUiState> = _uiState.asStateFlow()

    private val _config = MutableStateFlow(ConversionConfig())
    val config: StateFlow<ConversionConfig> = _config.asStateFlow()

    private val _history = MutableStateFlow<List<ConversionEntity>>(emptyList())
    val history: StateFlow<List<ConversionEntity>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            database.conversionDao().getAllConversions().collectLatest { list ->
                _history.value = list
            }
        }
    }

    fun updateConfig(newConfig: ConversionConfig) {
        _config.value = newConfig
    }

    /**
     * Start conversion from an audio content URI selected via system document picker.
     */
    fun convertUri(uri: Uri, fileName: String = "Selected Audio") {
        stopPlayback()
        _uiState.value = _uiState.value.copy(
            isConverting = true,
            conversionProgress = 0.05f,
            conversionStatusMessage = "Starting audio transcription...",
            sourceAudioName = fileName,
            errorMessage = null
        )

        // Preload original audio for synchronized playback
        audioPlayer.loadUri(uri) { durationMs ->
            _uiState.value = _uiState.value.copy(sourceAudioDurationMs = durationMs)
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = converter.convertUri(
                    context = getApplication(),
                    uri = uri,
                    config = _config.value
                ) { prog, msg ->
                    _uiState.value = _uiState.value.copy(
                        conversionProgress = prog,
                        conversionStatusMessage = msg
                    )
                }

                val savedFile = converter.saveToCache(
                    context = getApplication(),
                    result = result,
                    fileName = "${fileName.substringBeforeLast('.')}_transcribed.mid"
                )

                saveToDatabase(fileName, result, savedFile)

                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    conversionProgress = 1.0f,
                    conversionStatusMessage = "Complete! ${result.notes.size} notes extracted",
                    conversionResult = result,
                    savedMidiFile = savedFile,
                    sourceAudioDurationMs = maxOf(_uiState.value.sourceAudioDurationMs, result.durationMs)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    errorMessage = "Failed to convert audio: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    /**
     * Toggles live microphone recording session.
     */
    fun toggleMicrophoneRecording() {
        if (_uiState.value.isRecordingMic) {
            // Stop recording and convert captured samples
            val samples = micSession?.stop() ?: FloatArray(0)
            micSession = null

            _uiState.value = _uiState.value.copy(
                isRecordingMic = false,
                micRmsLevel = 0f,
                rawSamples = samples
            )

            if (samples.isNotEmpty()) {
                convertSamples(samples, sampleRate = 22050, title = "Microphone Recording")
            }
        } else {
            // Start recording
            stopPlayback()
            val session = MicrophoneRecorder.Session(sampleRate = 22050)
            micSession = session

            _uiState.value = _uiState.value.copy(
                isRecordingMic = true,
                micRecordingElapsedMs = 0L,
                sourceAudioName = "Live Microphone",
                errorMessage = null
            )

            session.start { rms, elapsedMs ->
                _uiState.value = _uiState.value.copy(
                    micRmsLevel = rms,
                    micRecordingElapsedMs = elapsedMs
                )
            }
        }
    }

    /**
     * Transcribes raw PCM float audio samples.
     */
    fun convertSamples(samples: FloatArray, sampleRate: Int = 22050, title: String = "Live Recording") {
        stopPlayback()
        _uiState.value = _uiState.value.copy(
            isConverting = true,
            conversionProgress = 0.05f,
            conversionStatusMessage = "Processing PCM audio...",
            sourceAudioName = title,
            rawSamples = samples,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = converter.convertAudio(
                    samples = samples,
                    sampleRate = sampleRate,
                    config = _config.value
                ) { prog, msg ->
                    _uiState.value = _uiState.value.copy(
                        conversionProgress = prog,
                        conversionStatusMessage = msg
                    )
                }

                val savedFile = converter.saveToCache(
                    context = getApplication(),
                    result = result,
                    fileName = "${title.replace(' ', '_')}.mid"
                )

                saveToDatabase(title, result, savedFile)

                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    conversionProgress = 1.0f,
                    conversionStatusMessage = "Complete! ${result.notes.size} notes extracted",
                    conversionResult = result,
                    savedMidiFile = savedFile,
                    sourceAudioDurationMs = result.durationMs
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    errorMessage = "Transcription failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    private fun saveToDatabase(title: String, result: ConversionResult, midiFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ConversionEntity(
                title = title,
                timestamp = System.currentTimeMillis(),
                durationMs = result.durationMs,
                noteCount = result.notes.size,
                estimatedBpm = result.estimatedBpm,
                estimatedKey = result.estimatedKey,
                midiFilePath = midiFile.absolutePath,
                notesSummaryJson = "${result.notes.take(5).joinToString { it.pitchName }}..."
            )
            database.conversionDao().insert(entity)
        }
    }

    /**
     * Auditions a single note (plays brief tone).
     */
    fun auditionNote(note: MidiNote) {
        synthPlayer.auditionNote(note.noteNumber)
    }

    /**
     * Toggles synchronized playback of Transcribed MIDI and/or Original Audio.
     */
    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val result = _uiState.value.conversionResult ?: return
        if (result.notes.isEmpty()) return

        _uiState.value = _uiState.value.copy(isPlaying = true)
        val startOffset = _uiState.value.playbackPositionMs
        val totalDuration = maxOf(result.durationMs, _uiState.value.sourceAudioDurationMs)

        if (_uiState.value.playOriginalAudio) {
            audioPlayer.seekTo(startOffset)
            audioPlayer.play()
        }

        if (_uiState.value.playMidiSynth) {
            synthPlayer.playNotes(
                notes = result.notes,
                totalDurationMs = totalDuration,
                startOffsetMs = startOffset,
                onProgress = { pos ->
                    _uiState.value = _uiState.value.copy(playbackPositionMs = pos)
                },
                onComplete = {
                    stopPlayback()
                }
            )
        }
    }

    fun stopPlayback() {
        synthPlayer.stop()
        audioPlayer.pause()
        _uiState.value = _uiState.value.copy(isPlaying = false, playbackPositionMs = 0L)
    }

    fun seekPlayback(posMs: Long) {
        _uiState.value = _uiState.value.copy(playbackPositionMs = posMs)
        if (_uiState.value.isPlaying) {
            synthPlayer.stop()
            audioPlayer.seekTo(posMs)
            startPlayback()
        }
    }

    fun setMidiSolo(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(playMidiSynth = enabled)
        if (!enabled && !_uiState.value.playOriginalAudio) {
            _uiState.value = _uiState.value.copy(playOriginalAudio = true)
        }
    }

    fun setAudioSolo(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(playOriginalAudio = enabled)
        if (!enabled && !_uiState.value.playMidiSynth) {
            _uiState.value = _uiState.value.copy(playMidiSynth = true)
        }
    }

    fun shareMidi() {
        val file = _uiState.value.savedMidiFile ?: return
        converter.shareMidi(getApplication(), file, "Share Transcribed MIDI")
    }

    fun deleteHistoryItem(entity: ConversionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.conversionDao().delete(entity)
            try {
                File(entity.midiFilePath).delete()
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        synthPlayer.release()
        audioPlayer.release()
        micSession?.stop()
    }
}
