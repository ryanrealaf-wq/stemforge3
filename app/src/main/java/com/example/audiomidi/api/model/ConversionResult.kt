package com.example.audiomidi.api.model

/**
 * Result data produced by the audio to MIDI conversion process.
 */
data class ConversionResult(
    val notes: List<MidiNote>,
    val estimatedBpm: Int,
    val estimatedKey: String,
    val durationMs: Long,
    val sampleRate: Int,
    val midiFileBytes: ByteArray,
    val analysisFrames: List<AudioAnalysisFrame> = emptyList(),
    val averageVelocity: Int = if (notes.isNotEmpty()) notes.map { it.velocity }.average().toInt() else 0,
    val highestNote: String = if (notes.isNotEmpty()) notes.maxByOrNull { it.noteNumber }?.pitchName ?: "--" else "--",
    val lowestNote: String = if (notes.isNotEmpty()) notes.minByOrNull { it.noteNumber }?.pitchName ?: "--" else "--"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversionResult
        if (notes != other.notes) return false
        if (estimatedBpm != other.estimatedBpm) return false
        if (estimatedKey != other.estimatedKey) return false
        if (durationMs != other.durationMs) return false
        if (sampleRate != other.sampleRate) return false
        if (!midiFileBytes.contentEquals(other.midiFileBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = notes.hashCode()
        result = 31 * result + estimatedBpm
        result = 31 * result + estimatedKey.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + midiFileBytes.contentHashCode()
        return result
    }
}
