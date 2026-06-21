package com.example.data

import kotlinx.coroutines.flow.Flow

class CallRecordRepository(private val callRecordDao: CallRecordDao) {
    val allRecords: Flow<List<CallRecord>> = callRecordDao.getAllRecords()

    suspend fun insert(record: CallRecord) {
        callRecordDao.insertRecord(record)
    }

    suspend fun update(record: CallRecord) {
        callRecordDao.updateRecord(record)
    }

    suspend fun delete(record: CallRecord) {
        // Also delete the physical file
        try {
            if (record.file.exists()) {
                record.file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        callRecordDao.deleteRecord(record)
    }
}
