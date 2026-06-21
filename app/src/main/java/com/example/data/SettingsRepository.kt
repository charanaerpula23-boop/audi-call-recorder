package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val AUTO_RECORD_KEY = booleanPreferencesKey("auto_record")
    private val RECORD_VOIP_CALLS_KEY = booleanPreferencesKey("record_voip_calls")

    val isAutoRecordEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_RECORD_KEY] ?: false
    }

    val isRecordVoipCallsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[RECORD_VOIP_CALLS_KEY] ?: false
    }

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RECORD_KEY] = enabled
        }
    }

    suspend fun setRecordVoipCallsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RECORD_VOIP_CALLS_KEY] = enabled
        }
    }
}
