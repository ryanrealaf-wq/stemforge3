package com.example.audiomidi.api.dsp

import com.example.audiomidi.api.model.MidiNote
import kotlin.math.roundToInt

/**
 * Tempo / BPM estimator analyzing inter-onset intervals (IOI) from transcribed notes.
 */
object BpmEstimator {
    /**
     * Estimates BPM between 60 and 180 from note start times.
     */
    fun estimateBpm(notes: List<MidiNote>, defaultBpm: Int = 120): Int {
        if (notes.size < 4) return defaultBpm

        val intervals = mutableListOf<Long>()
        val sortedNotes = notes.sortedBy { it.startTimeMs }

        for (i in 0 until sortedNotes.size - 1) {
            val delta = sortedNotes[i + 1].startTimeMs - sortedNotes[i].startTimeMs
            if (delta in 150..2000) { // Reasonable musical interval (30 to 400 BPM range)
                intervals.add(delta)
            }
        }

        if (intervals.isEmpty()) return defaultBpm

        // Cluster intervals into common beat subdivisions (quarter, eighth)
        // Group by 25ms bins
        val binCounts = mutableMapOf<Int, Int>()
        for (interval in intervals) {
            val bin = ((interval / 25) * 25).toInt()
            binCounts[bin] = (binCounts[bin] ?: 0) + 1
        }

        val mostFrequentInterval = binCounts.maxByOrNull { it.value }?.key ?: 500

        // Convert beat duration in ms to BPM: BPM = 60,000 / beatDurationMs
        var rawBpm = (60000.0 / mostFrequentInterval).roundToInt()

        // Bring into standard 70 - 160 BPM window
        while (rawBpm < 70) rawBpm *= 2
        while (rawBpm > 170) rawBpm /= 2

        return rawBpm.coerceIn(60, 200)
    }
}
