package com.example.audiomidi.api.model

/**
 * Represents a single transcribed MIDI note event.
 *
 * @param noteNumber MIDI note number (0 - 127). Middle C (C4) is 60.
 * @param pitchName Musical pitch name representation (e.g. "C4", "F#3").
 * @param frequencyHz Fundamental frequency in Hertz (e.g. 440.0 Hz for A4).
 * @param startTimeMs Offset in milliseconds when the note starts.
 * @param durationMs Duration of the note in milliseconds.
 * @param velocity MIDI note velocity (1 - 127), corresponding to dynamic volume/energy.
 * @param confidence Confidence score of pitch detection (0.0 to 1.0).
 * @param pitchBends Continuous pitch bend tracking points during the note duration.
 */
data class MidiNote(
    val noteNumber: Int,
    val pitchName: String,
    val frequencyHz: Float,
    val startTimeMs: Long,
    val durationMs: Long,
    val velocity: Int = 100,
    val confidence: Float = 1.0f,
    val pitchBends: List<PitchBendPoint> = emptyList()
) {
    val endTimeMs: Long get() = startTimeMs + durationMs

    companion object {
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /**
         * Convert MIDI note number (0-127) to standard scientific pitch notation (e.g., 60 -> "C4").
         */
        fun midiToName(midi: Int): String {
            if (midi !in 0..127) return "--"
            val octave = (midi / 12) - 1
            val note = NOTE_NAMES[midi % 12]
            return "$note$octave"
        }

        /**
         * Convert frequency in Hz to nearest MIDI note number (440Hz -> 69).
         */
        fun freqToMidi(freqHz: Float): Int {
            if (freqHz <= 8.0f) return 0
            val midi = 69.0 + 12.0 * (Math.log(freqHz.toDouble() / 440.0) / Math.log(2.0))
            return Math.round(midi).toInt().coerceIn(0, 127)
        }

        /**
         * Convert MIDI note number to fundamental frequency in Hz.
         */
        fun midiToFreq(midi: Int): Float {
            return (440.0 * Math.pow(2.0, (midi - 69).toDouble() / 12.0)).toFloat()
        }

        /**
         * Calculate pitch deviation in cents from nearest standard equal temperament semitone.
         */
        fun pitchCentsDeviation(freqHz: Float, midiNote: Int): Float {
            if (freqHz <= 0f) return 0f
            val idealFreq = midiToFreq(midiNote)
            return (1200.0 * (Math.log(freqHz.toDouble() / idealFreq.toDouble()) / Math.log(2.0))).toFloat()
        }
    }
}

/**
 * Represents microtonal pitch bend variation over time for expressive MIDI playback.
 * Bend value ranges from -8192 to +8191 where 0 is neutral pitch.
 */
data class PitchBendPoint(
    val timeOffsetMs: Long,
    val bendValue: Int
)
