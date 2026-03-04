package com.example.damselv5.util

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * Helper class to send background SMS with support for long messages.
 */
class SmsHelper(private val context: Context) {

    fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            // Use multi-part SMS to ensure links and long text aren't corrupted or split
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            
            Log.d("SmsHelper", "Emergency multi-part SMS initiated to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SmsHelper", "Emergency SMS failed for $phoneNumber: ${e.message}")
        }
    }
}