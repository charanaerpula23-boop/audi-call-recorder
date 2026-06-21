package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
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
    
    private val _searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )
    
    private val _isTranscribing = kotlinx.coroutines.flow.MutableStateFlow<Set<Int>>(emptySet())
    val isTranscribing: StateFlow<Set<Int>> = _isTranscribing.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val records: StateFlow<List<CallRecord>> = kotlinx.coroutines.flow.combine(repository.allRecords, _searchQuery) { records, query ->
        if (query.isBlank()) {
            records
        } else {
            records.filter { 
                it.phoneNumber.contains(query, ignoreCase = true) || 
                (it.contactName?.contains(query, ignoreCase = true) == true) ||
                (it.summary?.contains(query, ignoreCase = true) == true)
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteRecord(record: CallRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }
    
    fun transcribeRecording(record: CallRecord) {
        if (!record.file.exists()) return
        if (_isTranscribing.value.contains(record.id)) return
        
        _isTranscribing.value = _isTranscribing.value + record.id
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = record.file.readBytes()
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = "Please transcribe and summarize this phone call recording. Format your response with 'Transcription:' followed by the transcript, and then 'Summary:' followed by a short summary."),
                                Part(inlineData = InlineData(mimeType = "audio/mp4", data = base64Data))
                            )
                        )
                    )
                )

                val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    val summaryPart = if (responseText.contains("Summary:")) responseText.substringAfter("Summary:").trim() else "Summary not clearly identified."
                    repository.update(record.copy(transcription = responseText, summary = summaryPart))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isTranscribing.value = _isTranscribing.value - record.id
            }
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

    fun playAudio(filePath: String) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
