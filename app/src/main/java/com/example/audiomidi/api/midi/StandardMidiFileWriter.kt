package com.example.audiomidi.api.midi

import com.example.audiomidi.api.model.MidiNote
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Standard MIDI File (SMF Type 0) binary encoder adhering to the Official MIDI 1.0 Specification.
 * Converts transcribed note sequences and pitch bend events into compliant .mid binary files.
 */
class StandardMidiFileWriter(
    val ppq: Int = 480 // Pulses / ticks per quarter note
) {
    private sealed class MidiEvent(val timeMs: Long, val priority: Int) : Comparable<MidiEvent> {
        override fun compareTo(other: MidiEvent): Int {
            val timeCmp = this.timeMs.compareTo(other.timeMs)
            return if (timeCmp != 0) timeCmp else this.priority.compareTo(other.priority)
        }

        abstract fun writeData(out: ByteArrayOutputStream)
    }

    private class NoteOnEvent(
        timeMs: Long,
        val channel: Int,
        val noteNumber: Int,
        val velocity: Int
    ) : MidiEvent(timeMs, priority = 2) {
        override fun writeData(out: ByteArrayOutputStream) {
            out.write(0x90 or (channel and 0x0F))
            out.write(noteNumber.coerceIn(0, 127))
            out.write(velocity.coerceIn(1, 127))
        }
    }

    private class NoteOffEvent(
        timeMs: Long,
        val channel: Int,
        val noteNumber: Int
    ) : MidiEvent(timeMs, priority = 1) { // NoteOff prioritized slightly before NoteOn if simultaneous
        override fun writeData(out: ByteArrayOutputStream) {
            out.write(0x80 or (channel and 0x0F))
            out.write(noteNumber.coerceIn(0, 127))
            out.write(0x00)
        }
    }

    private class PitchBendEvent(
        timeMs: Long,
        val channel: Int,
        val bendValue: Int // -8192 to +8191
    ) : MidiEvent(timeMs, priority = 3) {
        override fun writeData(out: ByteArrayOutputStream) {
            val normalized = (bendValue + 8192).coerceIn(0, 16383)
            val lsb = normalized and 0x7F
            val msb = (normalized shr 7) and 0x7F
            out.write(0xE0 or (channel and 0x0F))
            out.write(lsb)
            out.write(msb)
        }
    }

    /**
     * Encodes a list of MidiNote objects into a byte array containing a complete SMF Type 0 file.
     */
    fun writeToBytes(
        notes: List<MidiNote>,
        bpm: Int = 120,
        trackName: String = "Transcribed Audio",
        includePitchBends: Boolean = true
    ): ByteArray {
        val eventList = mutableListOf<MidiEvent>()

        for (note in notes) {
            val channel = 0
            val noteNumber = note.noteNumber.coerceIn(0, 127)
            val velocity = note.velocity.coerceIn(1, 127)

            eventList.add(NoteOnEvent(note.startTimeMs, channel, noteNumber, velocity))
            eventList.add(NoteOffEvent(note.endTimeMs, channel, noteNumber))

            if (includePitchBends && note.pitchBends.isNotEmpty()) {
                for (bend in note.pitchBends) {
                    val bendTime = note.startTimeMs + bend.timeOffsetMs
                    eventList.add(PitchBendEvent(bendTime, channel, bend.bendValue))
                }
            }
        }

        eventList.sort()

        // Track data stream
        val trackStream = ByteArrayOutputStream()

        // 1. Meta Event: Set Tempo (Microseconds per Quarter Note)
        val usPerQuarter = (60_000_000.0 / bpm).roundToInt()
        writeVariableLength(0, trackStream) // delta time 0
        trackStream.write(0xFF)
        trackStream.write(0x51)
        trackStream.write(0x03)
        trackStream.write((usPerQuarter shr 16) and 0xFF)
        trackStream.write((usPerQuarter shr 8) and 0xFF)
        trackStream.write(usPerQuarter and 0xFF)

        // 2. Meta Event: Time Signature (4/4 time)
        writeVariableLength(0, trackStream)
        trackStream.write(0xFF)
        trackStream.write(0x58)
        trackStream.write(0x04)
        trackStream.write(0x04) // Numerator: 4
        trackStream.write(0x02) // Denominator: 2^2 = 4
        trackStream.write(0x18) // MIDI clocks per metronome click: 24
        trackStream.write(0x08) // 32nd notes per MIDI quarter note: 8

        // 3. Meta Event: Track Name
        val nameBytes = trackName.toByteArray(Charsets.UTF_8)
        writeVariableLength(0, trackStream)
        trackStream.write(0xFF)
        trackStream.write(0x03)
        writeVariableLength(nameBytes.size.toLong(), trackStream)
        trackStream.write(nameBytes)

        // 4. Note & Pitch Bend Events
        var currentTick = 0L
        val msPerTick = (60_000.0 / (bpm * ppq))

        for (event in eventList) {
            val eventTick = (event.timeMs / msPerTick).toLong()
            val deltaTicks = maxOf(0L, eventTick - currentTick)
            currentTick += deltaTicks

            writeVariableLength(deltaTicks, trackStream)
            event.writeData(trackStream)
        }

        // 5. Meta Event: End of Track (0xFF 0x2F 0x00)
        writeVariableLength(0, trackStream)
        trackStream.write(0xFF)
        trackStream.write(0x2F)
        trackStream.write(0x00)

        // Full SMF Type 0 File Construction
        val fileStream = ByteArrayOutputStream()

        // MThd Header Chunk
        fileStream.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt32BE(6, fileStream)              // Header length = 6
        writeInt16BE(0, fileStream)              // Format = 0 (single track)
        writeInt16BE(1, fileStream)              // Number of tracks = 1
        writeInt16BE(ppq, fileStream)            // PPQ division

        // MTrk Track Chunk
        val trackBytes = trackStream.toByteArray()
        fileStream.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeInt32BE(trackBytes.size, fileStream)
        fileStream.write(trackBytes)

        return fileStream.toByteArray()
    }

    /**
     * Writes MIDI binary data to an output file.
     */
    fun writeToFile(
        notes: List<MidiNote>,
        outputFile: File,
        bpm: Int = 120,
        trackName: String = "Transcribed Audio",
        includePitchBends: Boolean = true
    ): Boolean {
        return try {
            val bytes = writeToBytes(notes, bpm, trackName, includePitchBends)
            FileOutputStream(outputFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeVariableLength(value: Long, out: ByteArrayOutputStream) {
        var v = value
        val buffer = ByteArray(8)
        var i = 0
        buffer[i++] = (v and 0x7F).toByte()
        v = v shr 7
        while (v > 0) {
            buffer[i++] = ((v and 0x7F) or 0x80).toByte()
            v = v shr 7
        }
        while (i > 0) {
            out.write(buffer[--i].toInt())
        }
    }

    private fun writeInt32BE(value: Int, out: ByteArrayOutputStream) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeInt16BE(value: Int, out: ByteArrayOutputStream) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
