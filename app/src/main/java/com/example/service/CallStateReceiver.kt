package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.data.SettingsRepository

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            var state = 0
            if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
                state = TelephonyManager.CALL_STATE_IDLE
            } else if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                state = TelephonyManager.CALL_STATE_OFFHOOK
            } else if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                state = TelephonyManager.CALL_STATE_RINGING
            }

            Log.d("CallStateReceiver", "State: $stateStr, Number: $number")
            
            // Forward this information to our persistent Accessibility service or start Recording Service directly
            val settings = SettingsRepository(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                val autoRecord = settings.isAutoRecordEnabled.first()
                if (autoRecord) {
                   when (state) {
                       TelephonyManager.CALL_STATE_OFFHOOK -> {
                           // Start recording
                           val serviceIntent = Intent(context, CallRecordingService::class.java).apply {
                               action = CallRecordingService.ACTION_START_RECORDING
                               putExtra(CallRecordingService.EXTRA_PHONE_NUMBER, number ?: "Unknown")
                               // Naive assumption: if we were previously ringing, it's incoming. We can pass a default.
                           }
                           context.startForegroundService(serviceIntent)
                       }
                       TelephonyManager.CALL_STATE_IDLE -> {
                           // Stop recording
                           val serviceIntent = Intent(context, CallRecordingService::class.java).apply {
                               action = CallRecordingService.ACTION_STOP_RECORDING
                           }
                           context.startService(serviceIntent)
                       }
                   }
                }
            }
        }
    }
}
