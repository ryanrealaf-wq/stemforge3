package com.example.audiomidi.api.dsp

import kotlin.math.sqrt

/**
 * Implementation of the YIN fundamental frequency estimator (de Cheveigné & Kawahara, 2002).
 * Operates in the time domain, avoiding pitch octave doubling errors common in standard autocorrelation.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val bufferSize: Int = 1024,
    private val threshold: Float = 0.15f
) {
    private val halfBufferSize = bufferSize / 2
    private val difference = FloatArray(halfBufferSize)
    private val cumulativeMeanNormalizedDifference = FloatArray(halfBufferSize)

    data class PitchResult(
        val pitchHz: Float,
        val probability: Float,
        val rmsEnergy: Float
    )

    /**
     * Estimates fundamental frequency and probability for an audio window.
     */
    fun getPitch(audioBuffer: FloatArray, startIndex: Int = 0): PitchResult {
        val available = audioBuffer.size - startIndex
        if (available < bufferSize) {
            return PitchResult(0f, 0f, 0f)
        }

        // Calculate RMS Energy
        var sumSquares = 0.0
        for (i in 0 until bufferSize) {
            val sample = audioBuffer[startIndex + i]
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / bufferSize).toFloat()

        if (rms < 0.005f) { // Silence or very low noise floor
            return PitchResult(0f, 0f, rms)
        }

        // Step 1: Difference Function
        difference(audioBuffer, startIndex)

        // Step 2: Cumulative Mean Normalized Difference Function
        cumulativeMeanNormalizedDifference()

        // Step 3: Absolute Thresholding
        val tau = absoluteThreshold()

        if (tau != -1) {
            // Step 4: Parabolic Interpolation for exact peak
            val betterTau = parabolicInterpolation(tau)
            val pitch = sampleRate.toFloat() / betterTau
            val prob = (1f - cumulativeMeanNormalizedDifference[tau]).coerceIn(0f, 1f)
            return PitchResult(pitch, prob, rms)
        }

        return PitchResult(0f, 0f, rms)
    }

    private fun difference(buffer: FloatArray, startIndex: Int) {
        for (tau in 0 until halfBufferSize) {
            var sum = 0f
            for (i in 0 until halfBufferSize) {
                val delta = buffer[startIndex + i] - buffer[startIndex + i + tau]
                sum += delta * delta
            }
            difference[tau] = sum
        }
    }

    private fun cumulativeMeanNormalizedDifference() {
        cumulativeMeanNormalizedDifference[0] = 1f
        var runningSum = 0f
        for (tau in 1 until halfBufferSize) {
            runningSum += difference[tau]
            cumulativeMeanNormalizedDifference[tau] = if (runningSum > 0f) {
                (difference[tau] * tau) / runningSum
            } else {
                1f
            }
        }
    }

    private fun absoluteThreshold(): Int {
        var tau = 2
        while (tau < halfBufferSize) {
            if (cumulativeMeanNormalizedDifference[tau] < threshold) {
                while (tau + 1 < halfBufferSize && cumulativeMeanNormalizedDifference[tau + 1] < cumulativeMeanNormalizedDifference[tau]) {
                    tau++
                }
                return tau
            }
            tau++
        }

        // Fallback to global minimum if below relaxed threshold (0.35)
        var minTau = -1
        var minVal = 0.35f
        for (t in 2 until halfBufferSize) {
            if (cumulativeMeanNormalizedDifference[t] < minVal) {
                minVal = cumulativeMeanNormalizedDifference[t]
                minTau = t
            }
        }
        return minTau
    }

    private fun parabolicInterpolation(tau: Int): Float {
        if (tau <= 0 || tau >= halfBufferSize - 1) return tau.toFloat()

        val s0 = cumulativeMeanNormalizedDifference[tau - 1]
        val s1 = cumulativeMeanNormalizedDifference[tau]
        val s2 = cumulativeMeanNormalizedDifference[tau + 1]

        val denom = 2f * (2f * s1 - s2 - s0)
        if (denom == 0f) return tau.toFloat()

        val delta = (s2 - s0) / denom
        return (tau + delta).coerceIn(1f, (halfBufferSize - 1).toFloat())
    }
}
