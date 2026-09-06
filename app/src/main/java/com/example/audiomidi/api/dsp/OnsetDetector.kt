package com.example.audiomidi.api.dsp

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Onset detector combining spectral flux and RMS energy gradient to identify musical note attacks.
 */
class OnsetDetector(
    private val fftSize: Int = 1024,
    private val sensitivity: Float = 0.65f
) {
    private val fft = FastFourierTransform(fftSize)
    private val prevMagnitudes = FloatArray(fftSize / 2)
    private val currentMagnitudes = FloatArray(fftSize / 2)
    private var prevRms = 0f

    /**
     * Resets detector state between files/streams.
     */
    fun reset() {
        prevMagnitudes.fill(0f)
        currentMagnitudes.fill(0f)
        prevRms = 0f
    }

    /**
     * Analyzes an audio frame and determines if an onset occurred.
     *
     * @return Pair of (isOnset, spectralFlux)
     */
    fun processFrame(audioBuffer: FloatArray, startIndex: Int): Pair<Boolean, Float> {
        val available = audioBuffer.size - startIndex
        if (available < fftSize) return Pair(false, 0f)

        // Compute RMS
        var sumSquares = 0.0
        for (i in 0 until fftSize) {
            val s = audioBuffer[startIndex + i]
            sumSquares += s * s
        }
        val currentRms = sqrt(sumSquares / fftSize).toFloat()

        // Compute FFT magnitude spectrum
        val window = FloatArray(fftSize)
        System.arraycopy(audioBuffer, startIndex, window, 0, fftSize)
        fft.computeMagnitudeSpectrum(window, currentMagnitudes)

        // Calculate positive spectral flux: sum(max(0, |X_t| - |X_t-1|))
        var flux = 0f
        for (i in 0 until prevMagnitudes.size) {
            val diff = currentMagnitudes[i] - prevMagnitudes[i]
            if (diff > 0f) {
                flux += diff
            }
            prevMagnitudes[i] = currentMagnitudes[i]
        }

        // Energy ratio jump
        val rmsRatio = if (prevRms > 1e-4f) currentRms / prevRms else 1f
        prevRms = currentRms

        // Dynamic thresholding based on sensitivity parameter (0.1 to 0.9)
        val fluxThreshold = (1.0f - sensitivity).coerceIn(0.1f, 0.9f) * 12.0f
        val isOnset = (flux > fluxThreshold && currentRms > 0.015f) || (rmsRatio > 2.2f && currentRms > 0.02f)

        return Pair(isOnset, flux)
    }
}
