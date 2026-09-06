package com.example.audiomidi.api.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fast Fourier Transform (FFT) implementation using Cooley-Tukey Radix-2 algorithm.
 * Optimized for real-time spectral analysis in audio processing.
 */
class FastFourierTransform(val n: Int) {
    init {
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, received: $n" }
    }

    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)
    private val bitReverse = IntArray(n)
    private val hannWindow = FloatArray(n)

    init {
        for (i in 0 until n / 2) {
            val angle = -2.0 * PI * i / n
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }

        // Bit reversal table
        var j = 0
        for (i in 0 until n - 1) {
            bitReverse[i] = j
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }
        bitReverse[n - 1] = n - 1

        // Precompute Hann window
        for (i in 0 until n) {
            hannWindow[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
        }
    }

    /**
     * Applies Hann window in-place to input array.
     */
    fun applyHannWindow(input: FloatArray, output: FloatArray) {
        val len = minOf(input.size, n)
        for (i in 0 until len) {
            output[i] = input[i] * hannWindow[i]
        }
        for (i in len until n) {
            output[i] = 0f
        }
    }

    /**
     * Performs forward FFT on complex data (real and imag arrays).
     */
    fun transform(real: FloatArray, imag: FloatArray) {
        require(real.size >= n && imag.size >= n)

        // Bit-reversal permutation
        for (i in 0 until n) {
            val rev = bitReverse[i]
            if (i < rev) {
                val tempR = real[i]
                real[i] = real[rev]
                real[rev] = tempR

                val tempI = imag[i]
                imag[i] = imag[rev]
                imag[rev] = tempI
            }
        }

        // Cooley-Tukey Radix-2 butterflies
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val step = n / len

            for (i in 0 until n step len) {
                var k = 0
                for (j in 0 until halfLen) {
                    val c = cosTable[k]
                    val s = sinTable[k]

                    val uR = real[i + j]
                    val uI = imag[i + j]

                    val vR = real[i + j + halfLen] * c - imag[i + j + halfLen] * s
                    val vI = real[i + j + halfLen] * s + imag[i + j + halfLen] * c

                    real[i + j] = uR + vR
                    imag[i + j] = uI + vI
                    real[i + j + halfLen] = uR - vR
                    imag[i + j + halfLen] = uI - vI

                    k += step
                }
            }
            len = len shl 1
        }
    }

    /**
     * Computes the magnitude spectrum from real audio window.
     * Output size will be (n / 2) + 1 representing frequencies from 0 to Nyquist.
     */
    fun computeMagnitudeSpectrum(audioWindow: FloatArray, magnitudes: FloatArray) {
        val real = FloatArray(n)
        val imag = FloatArray(n)
        applyHannWindow(audioWindow, real)
        transform(real, imag)

        val half = n / 2
        val outLen = minOf(magnitudes.size, half)
        for (i in 0 until outLen) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
    }

    /**
     * Estimates fundamental frequency by detecting prominent harmonic peaks in the magnitude spectrum.
     */
    fun estimateDominantFrequency(magnitudes: FloatArray, sampleRate: Int, minFreq: Float, maxFreq: Float): Float {
        val half = n / 2
        val binWidth = sampleRate.toFloat() / n
        val minBin = (minFreq / binWidth).toInt().coerceIn(1, half - 2)
        val maxBin = (maxFreq / binWidth).toInt().coerceIn(minBin + 1, half - 2)

        var peakBin = -1
        var peakMag = 0f

        for (bin in minBin..maxBin) {
            val m = magnitudes[bin]
            if (m > peakMag && m > magnitudes[bin - 1] && m > magnitudes[bin + 1]) {
                peakMag = m
                peakBin = bin
            }
        }

        if (peakBin <= 0 || peakMag <= 1e-4f) return 0f

        // Parabolic interpolation around peak bin for sub-bin precision
        val alpha = magnitudes[peakBin - 1]
        val beta = magnitudes[peakBin]
        val gamma = magnitudes[peakBin + 1]
        val delta = 0.5f * (alpha - gamma) / (alpha - 2f * beta + gamma + 1e-12f)
        val interpolatedBin = peakBin + delta

        return interpolatedBin * binWidth
    }
}
