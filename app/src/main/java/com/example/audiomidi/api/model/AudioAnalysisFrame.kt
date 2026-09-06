package com.example.audiomidi.api.model

/**
 * Diagnostic frame captured during audio signal processing.
 */
data class AudioAnalysisFrame(
    val timeMs: Long,
    val pitchHz: Float,
    val midiNote: Int,
    val rmsEnergy: Float,
    val spectralFlux: Float,
    val isOnset: Boolean
)
