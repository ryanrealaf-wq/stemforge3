package com.example

import com.example.audiomidi.api.dsp.BpmEstimator
import com.example.audiomidi.api.dsp.KeyEstimator
import com.example.audiomidi.api.dsp.YinPitchDetector
import com.example.audiomidi.api.midi.StandardMidiFileWriter
import com.example.audiomidi.api.model.MidiNote
import com.example.audiomidi.api.model.QuantizationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

  @Test
  fun testMidiPitchConversions() {
    // 440 Hz is Concert A4 (MIDI 69)
    val a4Midi = MidiNote.freqToMidi(440.0f)
    assertEquals(69, a4Midi)
    assertEquals("A4", MidiNote.midiToName(69))

    // Middle C is C4 (MIDI 60)
    val c4Midi = MidiNote.freqToMidi(261.63f)
    assertEquals(60, c4Midi)
    assertEquals("C4", MidiNote.midiToName(60))

    // Frequency calculation
    val freqA4 = MidiNote.midiToFreq(69)
    assertEquals(440.0f, freqA4, 0.01f)

    // Boundaries
    val lowC = MidiNote.freqToMidi(32.7f)
    assertEquals(24, lowC) // C1
    assertEquals("C1", MidiNote.midiToName(24))
  }

  @Test
  fun testStandardMidiFileWriter() {
    val writer = StandardMidiFileWriter(ppq = 480)
    val notes = listOf(
      MidiNote(
        noteNumber = 60,
        pitchName = "C4",
        frequencyHz = 261.63f,
        startTimeMs = 0L,
        durationMs = 500L,
        velocity = 100
      ),
      MidiNote(
        noteNumber = 64,
        pitchName = "E4",
        frequencyHz = 329.63f,
        startTimeMs = 500L,
        durationMs = 500L,
        velocity = 95
      )
    )

    val midiBytes = writer.writeToBytes(notes, bpm = 120, trackName = "Test Track")

    // Check SMF Header signature "MThd"
    assertTrue(midiBytes.size > 22)
    assertEquals('M'.code.toByte(), midiBytes[0])
    assertEquals('T'.code.toByte(), midiBytes[1])
    assertEquals('h'.code.toByte(), midiBytes[2])
    assertEquals('d'.code.toByte(), midiBytes[3])

    // Verify format 0 and 1 track
    assertEquals(0, midiBytes[9].toInt())
    assertEquals(1, midiBytes[11].toInt())

    // Check Track signature "MTrk" at index 14
    assertEquals('M'.code.toByte(), midiBytes[14])
    assertEquals('T'.code.toByte(), midiBytes[15])
    assertEquals('r'.code.toByte(), midiBytes[16])
    assertEquals('k'.code.toByte(), midiBytes[17])
  }

  @Test
  fun testYinPitchDetectionOnPureTone() {
    val sampleRate = 22050
    val durationSamples = 2048
    val targetFreq = 440.0 // A4

    val audio = FloatArray(durationSamples)
    for (i in 0 until durationSamples) {
      val t = i.toDouble() / sampleRate
      audio[i] = sin(2.0 * PI * targetFreq * t).toFloat()
    }

    val yin = YinPitchDetector(sampleRate = sampleRate, bufferSize = 1024, threshold = 0.15f)
    val result = yin.getPitch(audio, 0)

    assertTrue("YIN probability should be high on pure tone", result.probability > 0.8f)
    assertEquals(targetFreq.toFloat(), result.pitchHz, 5.0f)
  }

  @Test
  fun testKeyEstimationCMajor() {
    // C Major scale notes: C4 (60), D4 (62), E4 (64), F4 (65), G4 (67), A4 (69), B4 (71), C5 (72)
    val notes = listOf(60, 62, 64, 65, 67, 69, 71, 72).mapIndexed { idx, pitch ->
      MidiNote(
        noteNumber = pitch,
        pitchName = MidiNote.midiToName(pitch),
        frequencyHz = MidiNote.midiToFreq(pitch),
        startTimeMs = (idx * 500).toLong(),
        durationMs = 400L,
        velocity = if (pitch == 60 || pitch == 67 || pitch == 64) 110 else 80 // Emphasize C, E, G tonic triad
      )
    }

    val estimatedKey = KeyEstimator.estimateKey(notes)
    assertTrue("Estimated key should be C Major but was $estimatedKey", estimatedKey.contains("C Major"))
  }

  @Test
  fun testBpmEstimation() {
    // Sequence of 8 notes spaced evenly by 500ms -> 120 BPM
    val notes = (0 until 8).map { idx ->
      MidiNote(
        noteNumber = 60,
        pitchName = "C4",
        frequencyHz = 261.63f,
        startTimeMs = (idx * 500).toLong(),
        durationMs = 300L,
        velocity = 90
      )
    }

    val bpm = BpmEstimator.estimateBpm(notes, defaultBpm = 100)
    assertEquals(120, bpm)
  }

  @Test
  fun testQuantizationModes() {
    assertEquals(0, QuantizationMode.NONE.division)
    assertEquals(4, QuantizationMode.QUARTER.division)
    assertEquals(8, QuantizationMode.EIGHTH.division)
    assertEquals(16, QuantizationMode.SIXTEENTH.division)
  }
}
