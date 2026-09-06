package com.example.audiomidi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiomidi.api.model.ConversionConfig
import com.example.audiomidi.api.model.PitchAlgorithm
import com.example.audiomidi.api.model.QuantizationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionSettingsDialog(
    currentConfig: ConversionConfig,
    onDismiss: () -> Unit,
    onSave: (ConversionConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    var algorithm by remember { mutableStateOf(currentConfig.algorithm) }
    var sensitivity by remember { mutableFloatStateOf(currentConfig.sensitivity) }
    var minDurationMs by remember { mutableFloatStateOf(currentConfig.minNoteDurationMs.toFloat()) }
    var targetBpm by remember { mutableIntStateOf(currentConfig.targetBpm) }
    var quantization by remember { mutableStateOf(currentConfig.quantization) }
    var pitchBends by remember { mutableStateOf(currentConfig.includePitchBends) }
    var dynamicVelocity by remember { mutableStateOf(currentConfig.dynamicVelocity) }

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
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "DSP Transcription Settings",
                        color = colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Detection Algorithm Selection
            Text(
                text = "PITCH ESTIMATION ALGORITHM",
                color = colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            PitchAlgorithm.values().forEach { algo ->
                val isSelected = algorithm == algo
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { algorithm = algo },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, if (isSelected) colorScheme.primary else colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = algo.displayName,
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = algo.description,
                                color = if (isSelected) colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Sensitivity Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "NOTE SENSITIVITY",
                    color = colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "${(sensitivity * 100).toInt()}%",
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0.1f..0.95f,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.outline
                ),
                modifier = Modifier.testTag("sensitivity_slider")
            )
            Text(
                text = "Higher sensitivity detects softer notes and subtle pitch inflections.",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Minimum Note Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MINIMUM NOTE DURATION",
                    color = colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "${minDurationMs.toInt()} ms",
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = minDurationMs,
                onValueChange = { minDurationMs = it },
                valueRange = 30f..250f,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.secondary,
                    activeTrackColor = colorScheme.secondary,
                    inactiveTrackColor = colorScheme.outline
                )
            )
            Text(
                text = "Filters out short noise transients and percussive artifacts.",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Quantization Mode
            Text(
                text = "GRID TIMING QUANTIZATION",
                color = colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuantizationMode.values().take(4).forEach { qMode ->
                    val isSel = quantization == qMode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isSel) colorScheme.primary else colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { quantization = qMode }
                    ) {
                        Text(
                            text = qMode.label,
                            color = if (isSel) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Toggles: Pitch Bends & Dynamic Velocity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Continuous Pitch Bends",
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Encodes vibrato and portamento glides",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = pitchBends,
                    onCheckedChange = { pitchBends = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.onPrimary,
                        checkedTrackColor = colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Dynamic Velocity Tracking",
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Scales note velocity based on audio RMS energy",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = dynamicVelocity,
                    onCheckedChange = { dynamicVelocity = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.onPrimary,
                        checkedTrackColor = colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply Button
            Button(
                onClick = {
                    val updated = currentConfig.copy(
                        algorithm = algorithm,
                        sensitivity = sensitivity,
                        minNoteDurationMs = minDurationMs.toLong(),
                        targetBpm = targetBpm,
                        quantization = quantization,
                        includePitchBends = pitchBends,
                        dynamicVelocity = dynamicVelocity
                    )
                    onSave(updated)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_settings_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Apply Settings",
                    color = colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
