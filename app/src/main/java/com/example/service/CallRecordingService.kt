package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.CallRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.ContactsContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.pm.ServiceInfo

class CallRecordingService : Service() {

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
        const val EXTRA_IS_INCOMING = "EXTRA_IS_INCOMING"
        
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "CallRecordingChannel"
        private val TAG = "CallRecordingService"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null
    private var startTimeMillis: Long = 0
    
    private var phoneNumber: String = "Unknown"
    private var isIncoming: Boolean = false

    private lateinit var repository: CallRecordRepository

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(this).callRecordDao()
        repository = CallRecordRepository(dao)
        createNotificationChannel()
    }

    private fun getContactName(phoneNumber: String, context: Context): String? {
        if (phoneNumber == "Unknown" || phoneNumber.isBlank()) return null
        var name: String? = null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving contact name", e)
        }
        return name
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                if (!isRecording) {
                    phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
                    isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
                    startForegroundWithNotification()
                    startRecording()
                }
            }
            ACTION_STOP_RECORDING -> {
                if (isRecording) {
                    stopRecording()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ActiveDial")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Built-in icon for proof of concept
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) { // Wait, Build.VERSION_CODES.Q and above API 30+ 
            try {
                if (Build.VERSION.SDK_INT >= 34) { // Android 14+ specific foreground service type
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Foreground service start failed", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startRecording() {
        try {
            // Devices on API 31+ might use MediaRecorder(Context). Legacy ones use empty constructor.
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Important: Best audio source for recording both sides can be VOICE_COMMUNICATION or VOICE_RECOGNITION
            // Default VOICE_CALL is heavily restricted.
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder?.setAudioEncodingBitRate(128000)
            mediaRecorder?.setAudioSamplingRate(44100)

            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "Call_${phoneNumber}_$timestamp.m4a")
            currentFilePath = file.absolutePath

            mediaRecorder?.setOutputFile(currentFilePath)
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isRecording = true
            startTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "Recording started: $currentFilePath")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            isRecording = false
            currentFilePath = null
            // Fallback attempts could go here (e.g. try MIC source instead)
        }
    }

    private fun stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                Log.d(TAG, "Recording stopped")
                saveRecordToDatabase()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording cleanly", e)
            }
        }
    }

    private fun saveRecordToDatabase() {
        if (currentFilePath != null) {
            val duration = System.currentTimeMillis() - startTimeMillis
            val name = getContactName(phoneNumber, this)
            val record = CallRecord(
                phoneNumber = phoneNumber,
                contactName = name,
                timestamp = startTimeMillis,
                durationMillis = duration,
                filePath = currentFilePath!!,
                isIncoming = isIncoming
            )
            CoroutineScope(Dispatchers.IO).launch {
                repository.insert(record)
            }
            currentFilePath = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (isRecording) {
            stopRecording()
        }
        super.onDestroy()
    }
}
