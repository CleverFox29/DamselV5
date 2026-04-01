package com.example.damselv5.util

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

class SmsHelper(private val c: Context) {

    fun sendSms(pN: String, m: String) {
        try {
            val sM: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                c.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val p = sM.divideMessage(m)
            sM.sendMultipartTextMessage(pN, null, p, null, null)
            
        } catch (e: Exception) {

        }
    }
}