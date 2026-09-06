package com.example.audiomidi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocumentationDialog(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .testTag("api_docs_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Packaged Android API (SDK)",
                        color = colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Package: com.example.audiomidi.api",
                color = colorScheme.primary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Ready for modular inclusion into Android DAWs, audio plugins, and music tech tools.",
                color = colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Code Example Card
            Text(
                text = "KOTLIN INTEGRATION EXAMPLE",
                color = colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.5f))
            ) {
                val code = """
// 1. Get Singleton API Instance
val converter = AudioToMidiConverter.getInstance()

// 2. Configure DSP Parameters
val config = ConversionConfig(
    algorithm = PitchAlgorithm.YIN, // or SPECTRAL_PEAKS
    sensitivity = 0.70f,
    quantization = QuantizationMode.SIXTEENTH,
    includePitchBends = true
)

// 3. Transcribe Audio (File, URI, or FloatArray)
val result = converter.convertUri(
    context = context,
    uri = audioContentUri,
    config = config
) { progress, statusMessage ->
    Log.d("AudioToMidi", "Progress: " + (progress * 100).toInt() + "% - " + statusMessage)
}

// 4. Access Notes & Musical Telemetry
val notes: List<MidiNote> = result.notes
val bpm: Int = result.estimatedBpm
val key: String = result.estimatedKey

// 5. Export Standard MIDI File (.mid)
val outputFile = File(context.filesDir, "transcribed.mid")
converter.exportMidiFile(result, outputFile)
converter.shareMidi(context, outputFile, "Share Transcribed MIDI")
                """.trimIndent()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = code,
                        color = colorScheme.primary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "API CAPABILITIES",
                color = colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val capabilities = listOf(
                "YIN Algorithm: Sub-bin parabolic pitch estimation without octave errors.",
                "Onset Tracking: Spectral flux & RMS energy gradient detection.",
                "Universal Decoder: MediaExtractor/MediaCodec (MP3, WAV, M4A, OGG, FLAC).",
                "Binary SMF Writer: Compliant Standard MIDI File Type 0/1 binary builder.",
                "AudioTrack Synthesizer: Instant preview playback with ADSR envelope."
            )

            capabilities.forEach { cap ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                    Text(cap, color = colorScheme.onSurface, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
