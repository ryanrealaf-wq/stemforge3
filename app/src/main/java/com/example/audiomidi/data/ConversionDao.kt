package com.example.audiomidi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversions ORDER BY timestamp DESC")
    fun getAllConversions(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions WHERE id = :id")
    suspend fun getConversionById(id: Long): ConversionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversion: ConversionEntity): Long

    @Delete
    suspend fun delete(conversion: ConversionEntity)

    @Query("DELETE FROM conversions")
    suspend fun clearAll()
}
