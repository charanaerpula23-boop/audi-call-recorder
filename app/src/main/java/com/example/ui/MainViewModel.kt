package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.CallRecordRepository
import com.example.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).callRecordDao()
    private val repository = CallRecordRepository(dao)
    private val settingsRepository = SettingsRepository(application)
    
    private var mediaPlayer: MediaPlayer? = null
    
    private val _playingFile = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val playingFile: StateFlow<String?> = _playingFile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    private val _isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    
    private val _searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )
    
    val records: StateFlow<List<CallRecord>> = kotlinx.coroutines.flow.combine(repository.allRecords, _searchQuery) { records, query ->
        if (query.isBlank()) {
            records
        } else {
            records.filter { 
                it.phoneNumber.contains(query, ignoreCase = true) || 
                (it.contactName?.contains(query, ignoreCase = true) == true)
            }
        }
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    val isAutoRecordEnabled: StateFlow<Boolean> = settingsRepository.isAutoRecordEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isRecordVoipCallsEnabled: StateFlow<Boolean> = settingsRepository.isRecordVoipCallsEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val recordMode: StateFlow<com.example.data.RecordMode> = settingsRepository.recordMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.data.RecordMode.ALL_NUMBERS
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteRecord(record: CallRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }
    
    fun setAutoRecord(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoRecordEnabled(enabled)
        }
    }

    fun setRecordVoipCalls(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRecordVoipCallsEnabled(enabled)
        }
    }
    
    fun setRecordMode(mode: com.example.data.RecordMode) {
        viewModelScope.launch {
            settingsRepository.setRecordMode(mode)
        }
    }

    fun playAudio(filePath: String) {
        if (mediaPlayer != null && _playingFile.value == filePath) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
                _isPlaying.value = false
            } else {
                mediaPlayer!!.start()
                _isPlaying.value = true
            }
        } else {
            stopAudio()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener { 
                        _playingFile.value = null
                        _isPlaying.value = false
                    }
                }
                _playingFile.value = filePath
                _isPlaying.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _playingFile.value = null
                _isPlaying.value = false
            }
        }
    }
    
    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
            _playingFile.value = null
            _isPlaying.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }

    fun deleteRecords(records: List<CallRecord>) {
        viewModelScope.launch {
            for (record in records) {
                repository.delete(record)
            }
        }
    }

    fun exportToDownloads(records: List<CallRecord>) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            for (record in records) {
                val file = record.file
                if (!file.exists()) continue

                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/ActiveDial")
                }
                
                try {
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { outStream ->
                            file.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    }
                } catch(e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
