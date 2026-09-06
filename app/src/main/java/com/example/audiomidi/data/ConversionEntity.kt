package com.example.audiomidi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversions")
data class ConversionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val timestamp: Long,
    val durationMs: Long,
    val noteCount: Int,
    val estimatedBpm: Int,
    val estimatedKey: String,
    val midiFilePath: String,
    val notesSummaryJson: String
)
