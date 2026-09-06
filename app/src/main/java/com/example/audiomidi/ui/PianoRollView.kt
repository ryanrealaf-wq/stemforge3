package com.example.audiomidi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiomidi.api.model.MidiNote
import kotlin.math.max

/**
 * Interactive DAW-grade Piano Roll Component.
 * Displays musical pitch lanes, note blocks colored by dynamic velocity, playhead tracking, and touch inspection.
 */
@Composable
fun PianoRollView(
    notes: List<MidiNote>,
    totalDurationMs: Long,
    playbackPositionMs: Long,
    onNoteTapped: (MidiNote) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    if (notes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(colorScheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colorScheme.outline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No notes to display",
                    color = colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Text(
                    text = "Select an audio file or record live microphone input",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    // Determine vertical range of notes
    val minNote = max(24, (notes.minOfOrNull { it.noteNumber } ?: 60) - 2)
    val maxNote = minOf(108, (notes.maxOfOrNull { it.noteNumber } ?: 72) + 2)
    val noteRange = maxOf(12, maxNote - minNote + 1)

    // Zoom scale state (pixels per millisecond)
    var zoomFactor by remember { mutableFloatStateOf(0.18f) }
    var selectedNote by remember { mutableStateOf<MidiNote?>(null) }

    val safeDurationMs = maxOf(totalDurationMs, notes.maxOfOrNull { it.endTimeMs } ?: 1000L)
    val canvasWidthDp = maxOf(400.dp, (safeDurationMs * zoomFactor).dp)
    val laneHeightPx = 18f
    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("piano_roll_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header with range info & Zoom controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Piano Roll • ${notes.size} Notes (${MidiNote.midiToName(minNote)} - ${MidiNote.midiToName(maxNote)})",
                        color = colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { zoomFactor = (zoomFactor / 1.3f).coerceAtLeast(0.06f) },
                        modifier = Modifier.size(32.dp).testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${(zoomFactor * 500).toInt()}%",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { zoomFactor = (zoomFactor * 1.3f).coerceAtMost(0.80f) },
                        modifier = Modifier.size(32.dp).testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Piano Roll Body: Fixed Piano Keys on Left + Horizontally Scrollable Timeline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .border(1.dp, colorScheme.outline, RoundedCornerShape(10.dp))
            ) {
                // Left Vertical Piano Keyboard Keys
                Canvas(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .background(colorScheme.surfaceVariant)
                ) {
                    drawPianoKeys(minNote, maxNote, laneHeightPx, textMeasurer, colorScheme)
                }

                // Horizontal scroll container for Timeline & Notes
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState)
                ) {
                    val primaryColor = colorScheme.primary
                    val secondaryColor = colorScheme.secondary
                    val tertiaryColor = colorScheme.tertiary
                    val errorColor = colorScheme.error

                    Canvas(
                        modifier = Modifier
                            .width(canvasWidthDp)
                            .fillMaxHeight()
                            .pointerInput(notes, zoomFactor, minNote) {
                                detectTapGestures { offset ->
                                    val tappedTimeMs = (offset.x / zoomFactor).toLong()
                                    val laneIndex = (offset.y / laneHeightPx).toInt()
                                    val tappedMidi = maxNote - laneIndex

                                    val match = notes.firstOrNull { note ->
                                        note.noteNumber == tappedMidi &&
                                                tappedTimeMs in (note.startTimeMs - 50)..(note.endTimeMs + 50)
                                    }

                                    if (match != null) {
                                        selectedNote = match
                                        onNoteTapped(match)
                                    }
                                }
                            }
                    ) {
                        // 1. Draw musical grid lines
                        drawGridAndBeats(
                            width = size.width,
                            height = size.height,
                            minNote = minNote,
                            maxNote = maxNote,
                            laneHeightPx = laneHeightPx,
                            zoomFactor = zoomFactor,
                            totalDurationMs = safeDurationMs,
                            textMeasurer = textMeasurer,
                            colorScheme = colorScheme
                        )

                        // 2. Draw Note Rectangles
                        for (note in notes) {
                            val lane = maxNote - note.noteNumber
                            if (lane < 0 || lane >= noteRange) continue

                            val x = note.startTimeMs * zoomFactor
                            val w = maxOf(4f, note.durationMs * zoomFactor)
                            val y = lane * laneHeightPx + 1.5f
                            val h = laneHeightPx - 3f

                            // Color gradient based on velocity
                            val velRatio = (note.velocity / 127f).coerceIn(0.2f, 1.0f)
                            val noteColor = when {
                                velRatio > 0.80f -> tertiaryColor
                                velRatio > 0.55f -> primaryColor
                                else -> secondaryColor
                            }

                            val isSelected = selectedNote == note

                            drawRoundRect(
                                color = if (isSelected) Color.White else noteColor,
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(4f, 4f)
                            )

                            // Inner border for crisp visual definition
                            drawRoundRect(
                                color = if (isSelected) primaryColor else Color(0x66FFFFFF),
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                            )
                        }

                        // 3. Draw Active Playhead
                        if (playbackPositionMs in 0..safeDurationMs) {
                            val playheadX = playbackPositionMs * zoomFactor
                            drawLine(
                                color = errorColor,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, size.height),
                                strokeWidth = 2.5f
                            )
                            drawCircle(
                                color = errorColor,
                                radius = 4f,
                                center = Offset(playheadX, 6f)
                            )
                        }
                    }
                }
            }

            // Note Inspector Pill when a note is selected
            AnimatedVisibility(
                visible = selectedNote != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedNote?.let { note ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = note.pitchName,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MIDI ${note.noteNumber} • ${note.frequencyHz.toInt()} Hz • Vel: ${note.velocity} • ${note.durationMs}ms",
                                    color = colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            IconButton(
                                onClick = { selectedNote = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPianoKeys(
    minNote: Int,
    maxNote: Int,
    laneHeight: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    val blackNotePattern = booleanArrayOf(false, true, false, true, false, false, true, false, true, false, true, false)

    for (midi in maxNote downTo minNote) {
        val laneIndex = maxNote - midi
        val y = laneIndex * laneHeight
        val semitone = midi % 12
        val isBlack = blackNotePattern[semitone]

        // Key background
        drawRect(
            color = if (isBlack) colorScheme.background else colorScheme.surface,
            topLeft = Offset(0f, y),
            size = Size(size.width, laneHeight)
        )

        // Divider line
        drawLine(
            color = colorScheme.outline.copy(alpha = 0.5f),
            start = Offset(0f, y + laneHeight),
            end = Offset(size.width, y + laneHeight),
            strokeWidth = 0.5f
        )

        // Label C notes (e.g. C3, C4, C5)
        if (semitone == 0) {
            val name = MidiNote.midiToName(midi)
            drawText(
                textMeasurer = textMeasurer,
                text = name,
                topLeft = Offset(4f, y + 2f),
                style = TextStyle(
                    color = colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

private fun DrawScope.drawGridAndBeats(
    width: Float,
    height: Float,
    minNote: Int,
    maxNote: Int,
    laneHeightPx: Float,
    zoomFactor: Float,
    totalDurationMs: Long,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    val blackNotePattern = booleanArrayOf(false, true, false, true, false, false, true, false, true, false, true, false)

    // Horizontal lane shading
    for (midi in maxNote downTo minNote) {
        val laneIndex = maxNote - midi
        val y = laneIndex * laneHeightPx
        val isBlackKey = blackNotePattern[midi % 12]

        if (isBlackKey) {
            drawRect(
                color = colorScheme.background.copy(alpha = 0.6f),
                topLeft = Offset(0f, y),
                size = Size(width, laneHeightPx)
            )
        }

        // Semitone divider
        drawLine(
            color = colorScheme.outline.copy(alpha = 0.25f),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 0.5f
        )
    }

    // Vertical time lines (every 500ms or 1s depending on zoom)
    val timeStepMs = if (zoomFactor > 0.2f) 250L else if (zoomFactor > 0.1f) 500L else 1000L
    var t = 0L

    while (t <= totalDurationMs) {
        val x = t * zoomFactor
        val isMajorSecond = (t % 1000L == 0L)

        drawLine(
            color = if (isMajorSecond) colorScheme.outline.copy(alpha = 0.7f) else colorScheme.outline.copy(alpha = 0.3f),
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = if (isMajorSecond) 1.2f else 0.6f
        )

        if (isMajorSecond) {
            val seconds = t / 1000.0
            drawText(
                textMeasurer = textMeasurer,
                text = "${seconds}s",
                topLeft = Offset(x + 4f, 2f),
                style = TextStyle(
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
        t += timeStepMs
    }
}
