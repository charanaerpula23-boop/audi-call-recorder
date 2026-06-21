package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val contactName: String?,
    val timestamp: Long,
    val durationMillis: Long,
    val filePath: String,
    val isIncoming: Boolean,
    val transcription: String? = null,
    val summary: String? = null
) {
    val file: File
        get() = File(filePath)
}
