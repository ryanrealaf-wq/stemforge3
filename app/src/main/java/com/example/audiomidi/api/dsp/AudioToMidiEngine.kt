package com.example.audiomidi.api.dsp

import com.example.audiomidi.api.midi.StandardMidiFileWriter
import com.example.audiomidi.api.model.AudioAnalysisFrame
import com.example.audiomidi.api.model.ConversionConfig
import com.example.audiomidi.api.model.ConversionResult
import com.example.audiomidi.api.model.MidiNote
import com.example.audiomidi.api.model.PitchAlgorithm
import com.example.audiomidi.api.model.PitchBendPoint
import com.example.audiomidi.api.model.QuantizationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Core Audio to MIDI Transcription Engine.
 * Converts raw PCM audio samples into musical MIDI notes using time/frequency domain DSP.
 */
class AudioToMidiEngine {

    /**
     * Transcribes audio samples into structured MIDI notes and binary SMF file.
     */
    suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        config: ConversionConfig = ConversionConfig(),
        onProgress: ((Float, String) -> Unit)? = null
    ): ConversionResult = withContext(Dispatchers.Default) {
        if (samples.isEmpty()) {
            return@withContext ConversionResult(
                notes = emptyList(),
                estimatedBpm = config.targetBpm,
                estimatedKey = "C Major",
                durationMs = 0L,
                sampleRate = sampleRate,
                midiFileBytes = ByteArray(0)
            )
        }

        onProgress?.invoke(0.05f, "Initializing DSP detectors...")
        val durationMs = (samples.size.toDouble() * 1000.0 / sampleRate).toLong()
        val windowSize = config.windowSizeSamples
        val hopSize = config.hopSizeSamples

        val yinDetector = YinPitchDetector(sampleRate, windowSize, threshold = 0.18f)
        val onsetDetector = OnsetDetector(windowSize, sensitivity = config.sensitivity)
        val fft = FastFourierTransform(windowSize)
        val magnitudes = FloatArray(windowSize / 2)

        val totalFrames = (samples.size - windowSize) / hopSize
        if (totalFrames <= 0) {
            return@withContext ConversionResult(
                notes = emptyList(),
                estimatedBpm = config.targetBpm,
                estimatedKey = "C Major",
                durationMs = durationMs,
                sampleRate = sampleRate,
                midiFileBytes = ByteArray(0)
            )
        }

        val analysisFrames = ArrayList<AudioAnalysisFrame>(totalFrames)
        var maxRmsObserved = 0.001f

        // First pass: Frame analysis
        onProgress?.invoke(0.15f, "Analyzing pitch and transient onsets...")
        for (f in 0 until totalFrames) {
            if (f % 100 == 0) {
                coroutineContext.ensureActive()
                val progress = 0.15f + 0.45f * (f.toFloat() / totalFrames)
                onProgress?.invoke(progress, "Analyzing audio frame ${f + 1}/$totalFrames")
            }

            val startIndex = f * hopSize
            val timeMs = (startIndex.toDouble() * 1000.0 / sampleRate).toLong()

            // Onset tracking
            val (isOnset, flux) = onsetDetector.processFrame(samples, startIndex)

            // Pitch estimation according to selected algorithm
            var pitchHz = 0f
            var rmsEnergy = 0f

            when (config.algorithm) {
                PitchAlgorithm.YIN -> {
                    val yinResult = yinDetector.getPitch(samples, startIndex)
                    pitchHz = yinResult.pitchHz
                    rmsEnergy = yinResult.rmsEnergy
                }
                PitchAlgorithm.SPECTRAL_PEAKS -> {
                    val window = FloatArray(windowSize)
                    System.arraycopy(samples, startIndex, window, 0, windowSize)
                    fft.computeMagnitudeSpectrum(window, magnitudes)
                    val minF = MidiNote.midiToFreq(config.minMidiNote)
                    val maxF = MidiNote.midiToFreq(config.maxMidiNote)
                    pitchHz = fft.estimateDominantFrequency(magnitudes, sampleRate, minF, maxF)

                    var sumSq = 0.0
                    for (s in window) sumSq += s * s
                    rmsEnergy = kotlin.math.sqrt(sumSq / windowSize).toFloat()
                }
                PitchAlgorithm.AUTO -> {
                    val yinResult = yinDetector.getPitch(samples, startIndex)
                    rmsEnergy = yinResult.rmsEnergy
                    pitchHz = if (yinResult.probability > 0.4f) {
                        yinResult.pitchHz
                    } else {
                        val window = FloatArray(windowSize)
                        System.arraycopy(samples, startIndex, window, 0, windowSize)
                        fft.computeMagnitudeSpectrum(window, magnitudes)
                        val minF = MidiNote.midiToFreq(config.minMidiNote)
                        val maxF = MidiNote.midiToFreq(config.maxMidiNote)
                        fft.estimateDominantFrequency(magnitudes, sampleRate, minF, maxF)
                    }
                }
            }

            if (rmsEnergy > maxRmsObserved) {
                maxRmsObserved = rmsEnergy
            }

            val midiNote = if (pitchHz > 20f) MidiNote.freqToMidi(pitchHz) else 0
            val validMidi = if (midiNote in config.minMidiNote..config.maxMidiNote) midiNote else 0

            analysisFrames.add(
                AudioAnalysisFrame(
                    timeMs = timeMs,
                    pitchHz = pitchHz,
                    midiNote = validMidi,
                    rmsEnergy = rmsEnergy,
                    spectralFlux = flux,
                    isOnset = isOnset
                )
            )
        }

        // Second pass: Note segmentation
        onProgress?.invoke(0.65f, "Segmenting notes and calculating velocities...")
        val rawNotes = segmentNotes(analysisFrames, config, maxRmsObserved)

        // Third pass: Quantization & Pitch Bend refinement
        onProgress?.invoke(0.80f, "Quantizing notes and tuning MIDI expression...")
        val finalNotes = processNoteRefinement(rawNotes, config)

        // Musical Key and Tempo Estimation
        onProgress?.invoke(0.90f, "Estimating musical key and tempo...")
        val estimatedBpm = BpmEstimator.estimateBpm(finalNotes, config.targetBpm)
        val estimatedKey = KeyEstimator.estimateKey(finalNotes)

        // Encode binary Standard MIDI File
        onProgress?.invoke(0.95f, "Building Standard MIDI File (SMF)...")
        val midiWriter = StandardMidiFileWriter(ppq = 480)
        val midiBytes = midiWriter.writeToBytes(
            notes = finalNotes,
            bpm = estimatedBpm,
            trackName = "Transcribed ($estimatedKey)",
            includePitchBends = config.includePitchBends
        )

        onProgress?.invoke(1.0f, "Transcription complete (${finalNotes.size} notes detected)")

        ConversionResult(
            notes = finalNotes,
            estimatedBpm = estimatedBpm,
            estimatedKey = estimatedKey,
            durationMs = durationMs,
            sampleRate = sampleRate,
            midiFileBytes = midiBytes,
            analysisFrames = analysisFrames
        )
    }

    private fun segmentNotes(
        frames: List<AudioAnalysisFrame>,
        config: ConversionConfig,
        maxRmsObserved: Float
    ): List<MidiNote> {
        val notes = mutableListOf<MidiNote>()
        if (frames.isEmpty()) return notes

        // Energy threshold tuned by sensitivity (0.1 to 0.9)
        // High sensitivity = lower energy threshold
        val energyThreshold = maxRmsObserved * (1.05f - config.sensitivity * 0.9f).coerceIn(0.04f, 0.40f)

        var currentNoteNumber = 0
        var noteStartMs = 0L
        var notePeakRms = 0f
        var noteSumFreq = 0.0
        var noteFrameCount = 0
        val currentPitchBends = mutableListOf<PitchBendPoint>()

        fun closeCurrentNote(endMs: Long) {
            if (currentNoteNumber > 0 && noteFrameCount > 0) {
                val duration = endMs - noteStartMs
                if (duration >= config.minNoteDurationMs) {
                    val avgFreq = (noteSumFreq / noteFrameCount).toFloat()
                    val velocity = if (config.dynamicVelocity && maxRmsObserved > 0f) {
                        val norm = (notePeakRms / maxRmsObserved).coerceIn(0.1f, 1.0f)
                        (norm * 90f + 37f).toInt().coerceIn(30, 127)
                    } else {
                        100
                    }

                    notes.add(
                        MidiNote(
                            noteNumber = currentNoteNumber,
                            pitchName = MidiNote.midiToName(currentNoteNumber),
                            frequencyHz = avgFreq,
                            startTimeMs = noteStartMs,
                            durationMs = duration,
                            velocity = velocity,
                            confidence = 0.85f,
                            pitchBends = ArrayList(currentPitchBends)
                        )
                    )
                }
            }
            currentNoteNumber = 0
            noteFrameCount = 0
            notePeakRms = 0f
            noteSumFreq = 0.0
            currentPitchBends.clear()
        }

        for (frame in frames) {
            val hasSound = frame.rmsEnergy >= energyThreshold && frame.midiNote in config.minMidiNote..config.maxMidiNote

            if (!hasSound) {
                // Silence or below threshold
                if (currentNoteNumber > 0) {
                    closeCurrentNote(frame.timeMs)
                }
                continue
            }

            val noteDiff = abs(frame.midiNote - currentNoteNumber)
            val isNewNote = (currentNoteNumber == 0) || (frame.isOnset && noteDiff >= 1) || (noteDiff >= 2)

            if (isNewNote) {
                if (currentNoteNumber > 0) {
                    closeCurrentNote(frame.timeMs)
                }
                currentNoteNumber = frame.midiNote
                noteStartMs = frame.timeMs
                notePeakRms = frame.rmsEnergy
                noteSumFreq = frame.pitchHz.toDouble()
                noteFrameCount = 1
            } else {
                // Continuation of current note
                noteFrameCount++
                noteSumFreq += frame.pitchHz
                if (frame.rmsEnergy > notePeakRms) {
                    notePeakRms = frame.rmsEnergy
                }

                // Microtonal pitch bend calculation in cents (-100 to +100 cents -> -4096 to +4096)
                if (config.includePitchBends && frame.pitchHz > 10f) {
                    val nominalFreq = MidiNote.midiToFreq(currentNoteNumber)
                    val cents = 1200.0 * (Math.log(frame.pitchHz.toDouble() / nominalFreq) / Math.log(2.0))
                    val bend = (cents * (8192.0 / 200.0)).toInt().coerceIn(-8192, 8191)
                    currentPitchBends.add(PitchBendPoint(timeOffsetMs = frame.timeMs - noteStartMs, bendValue = bend))
                }
            }
        }

        if (currentNoteNumber > 0) {
            closeCurrentNote(frames.last().timeMs + 20)
        }

        return notes
    }

    private fun processNoteRefinement(
        notes: List<MidiNote>,
        config: ConversionConfig
    ): List<MidiNote> {
        if (config.quantization == QuantizationMode.NONE || notes.isEmpty()) {
            return notes
        }

        // Quantization step in ms based on target BPM and grid subdivision
        // Quarter note = 60,000 / BPM
        val quarterNoteMs = 60_000.0 / config.targetBpm
        val gridStepMs = quarterNoteMs / (config.quantization.division / 4.0)

        return notes.map { note ->
            val snappedStart = (Math.round(note.startTimeMs / gridStepMs) * gridStepMs).roundToLong()
            val snappedDuration = maxOf(
                config.minNoteDurationMs,
                (Math.round(note.durationMs / gridStepMs) * gridStepMs).roundToLong()
            )
            note.copy(
                startTimeMs = maxOf(0L, snappedStart),
                durationMs = snappedDuration
            )
        }
    }
}
