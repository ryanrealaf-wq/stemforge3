package com.example.audiomidi.api.model

/**
 * Detection algorithm selection for audio to MIDI transcription.
 */
enum class PitchAlgorithm(val displayName: String, val description: String) {
    YIN(
        displayName = "YIN Pitch Tracker",
        description = "High-precision autocorrelation difference algorithm. Superior for vocals, woodwinds, brass, and solo instruments."
    ),
    SPECTRAL_PEAKS(
        displayName = "Harmonic Spectral Peak",
        description = "FFT spectral analysis with harmonic peak extraction. Effective for rich harmonic instruments like synth and piano."
    ),
    AUTO(
        displayName = "Adaptive Hybrid",
        description = "Combines onset transient detection with YIN fundamental frequency tracking for general acoustic audio."
    )
}

/**
 * Quantization grid subdivision for snapping notes to musical timing.
 */
enum class QuantizationMode(val division: Int, val label: String) {
    NONE(0, "Off (Free Timing)"),
    QUARTER(4, "1/4 Beat"),
    EIGHTH(8, "1/8 Beat"),
    SIXTEENTH(16, "1/16 Beat"),
    THIRTY_SECOND(32, "1/32 Beat")
}

/**
 * Configuration parameters for the Audio to MIDI conversion engine.
 */
data class ConversionConfig(
    val algorithm: PitchAlgorithm = PitchAlgorithm.YIN,
    val minMidiNote: Int = 24,         // C1 (32.7 Hz)
    val maxMidiNote: Int = 96,         // C7 (2093 Hz)
    val sensitivity: Float = 0.65f,    // 0.0 - 1.0, higher detects softer notes
    val minNoteDurationMs: Long = 70,  // Filter out spurious short transients
    val targetBpm: Int = 120,          // Base tempo for MIDI file header and quantization
    val quantization: QuantizationMode = QuantizationMode.NONE,
    val includePitchBends: Boolean = true,
    val dynamicVelocity: Boolean = true,
    val sampleRate: Int = 22050,       // Standard 22.05 kHz analysis rate
    val hopSizeSamples: Int = 256,     // ~11.6ms analysis step size
    val windowSizeSamples: Int = 1024  // ~46.4ms analysis window
)
