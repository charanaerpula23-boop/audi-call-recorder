package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<CallRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CallRecord)

    @androidx.room.Update
    suspend fun updateRecord(record: CallRecord)

    @Delete
    suspend fun deleteRecord(record: CallRecord)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)
}
