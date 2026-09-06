package com.example.audiomidi.api.dsp

import com.example.audiomidi.api.model.MidiNote
import kotlin.math.sqrt

/**
 * Musical key estimator based on the Krumhansl-Schmuckler Key-Finding Algorithm.
 * Evaluates note pitch class distribution against standard Major and Minor perceptual profiles.
 */
object KeyEstimator {
    // Krumhansl-Kessler key profiles for 12 chromatic pitch classes
    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    private val PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Estimates key from a list of transcribed MIDI notes, weighting by note duration and velocity.
     */
    fun estimateKey(notes: List<MidiNote>): String {
        if (notes.isEmpty()) return "C Major"

        // 12-bin chromatic pitch class histogram
        val pitchClassProfile = FloatArray(12)
        for (note in notes) {
            val pitchClass = (note.noteNumber % 12).coerceIn(0, 11)
            val weight = (note.durationMs / 1000f) * (note.velocity / 127f)
            pitchClassProfile[pitchClass] += weight
        }

        // Normalize
        val total = pitchClassProfile.sum()
        if (total > 0f) {
            for (i in 0 until 12) pitchClassProfile[i] /= total
        }

        var bestScore = -Float.MAX_VALUE
        var bestKey = "C Major"

        // Test each of the 12 pitch classes for Major and Minor
        for (root in 0 until 12) {
            // Major
            val majScore = correlation(pitchClassProfile, MAJOR_PROFILE, root)
            if (majScore > bestScore) {
                bestScore = majScore
                bestKey = "${PITCH_NAMES[root]} Major"
            }

            // Minor
            val minScore = correlation(pitchClassProfile, MINOR_PROFILE, root)
            if (minScore > bestScore) {
                bestScore = minScore
                bestKey = "${PITCH_NAMES[root]} Minor"
            }
        }

        return bestKey
    }

    private fun correlation(profile: FloatArray, template: FloatArray, rootOffset: Int): Float {
        val n = 12
        var meanP = 0f
        var meanT = 0f
        for (i in 0 until n) {
            val rotatedIdx = (i - rootOffset + n) % n
            meanP += profile[rotatedIdx]
            meanT += template[i]
        }
        meanP /= n
        meanT /= n

        var num = 0f
        var denP = 0f
        var denT = 0f
        for (i in 0 until n) {
            val rotatedIdx = (i - rootOffset + n) % n
            val dp = profile[rotatedIdx] - meanP
            val dt = template[i] - meanT
            num += dp * dt
            denP += dp * dp
            denT += dt * dt
        }

        val denom = sqrt(denP * denT)
        return if (denom > 1e-6f) num / denom else 0f
    }
}
