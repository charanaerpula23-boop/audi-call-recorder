package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.data.SettingsRepository

class CallAccessibilityService : AccessibilityService() {

    private val TAG = "CallAccessibilitySvc"
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var settingsRepository: SettingsRepository
    
    private var isRecording = false
    private var currentNumber: String? = null
    private var isIncoming = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        
        settingsRepository = SettingsRepository(applicationContext)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        registerCallStateListener()
    }

    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
            telephonyManager.registerTelephonyCallback(
                mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state, null) // Number is restricted in TelephonyCallback
                    }
                }
            )
        }
    }

    private fun handleCallState(state: Int, number: String?) {
        Log.d(TAG, "Call State Changed: $state")
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                if (number != null) currentNumber = number
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered or outgoing call
                if (number != null) currentNumber = number
                CoroutineScope(Dispatchers.IO).launch {
                    val autoRecord = settingsRepository.isAutoRecordEnabled.first()
                    if (autoRecord && !isRecording) {
                        startRecordingService(currentNumber ?: "Unknown", isIncoming)
                        isRecording = true
                    }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording) {
                    stopRecordingService()
                    isRecording = false
                }
                currentNumber = null
                isIncoming = false
            }
        }
    }

    private var currentVoipPackage: String? = null
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Fallback for number extraction and more robust detection if needed
        // Sometimes TelephonyCallback doesn't yield the number or misses events on custom ROMs
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            Log.d(TAG, "Window State Changed: $packageName")
            
            val isVoip = packageName == "com.whatsapp" || packageName == "com.instagram.android"
            if (isVoip) {
                CoroutineScope(Dispatchers.IO).launch {
                    val recordVoip = settingsRepository.isRecordVoipCallsEnabled.first()
                    if (!recordVoip) return@launch
                    
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val isInCommunication = audioManager.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
                    
                    if (isInCommunication && !isRecording) {
                        val appName = if (packageName == "com.whatsapp") "WhatsApp" else "Instagram"
                        startRecordingService("$appName Call", incoming = false)
                        isRecording = true
                        currentVoipPackage = packageName
                    }
                }
            } else {
                // If not voip package but we are recording a voip call and audio manager says no longer in comms
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                if (isRecording && currentVoipPackage != null && audioManager.mode != android.media.AudioManager.MODE_IN_COMMUNICATION) {
                    stopRecordingService()
                    isRecording = false
                    currentVoipPackage = null
                }
            }
        } else if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (isRecording && currentVoipPackage != null) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                if (audioManager.mode != android.media.AudioManager.MODE_IN_COMMUNICATION) {
                    stopRecordingService()
                    isRecording = false
                    currentVoipPackage = null
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    private fun startRecordingService(number: String, incoming: Boolean) {
        val intent = Intent(this, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_START_RECORDING
            putExtra(CallRecordingService.EXTRA_PHONE_NUMBER, number)
            putExtra(CallRecordingService.EXTRA_IS_INCOMING, incoming)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopRecordingService() {
        val intent = Intent(this, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_STOP_RECORDING
        }
        startService(intent) // or startForegroundService(intent)
    }
}
