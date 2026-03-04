package com.example.damselv5.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * A transparent activity that breaks through background restrictions to initiate an emergency call.
 */
class EmergencyCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure this activity can appear over the lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
        Log.d("EmergencyCallActivity", "Attempting to call: $phoneNumber")
        
        if (!phoneNumber.isNullOrBlank()) {
            makeCall(phoneNumber)
        } else {
            finish()
        }
    }

    private fun makeCall(number: String) {
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        val phoneUri = Uri.parse("tel:$normalized")
        
        // Try direct call first
        val callIntent = Intent(Intent.ACTION_CALL, phoneUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            startActivity(callIntent)
            Log.d("EmergencyCallActivity", "Direct call intent sent.")
            // Delay finishing to ensure the dialer has time to take over
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
        } catch (e: Exception) {
            Log.e("EmergencyCallActivity", "Direct call failed: ${e.message}, falling back to dialer")
            // Fallback to dialer
            val dialIntent = Intent(Intent.ACTION_DIAL, phoneUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(dialIntent)
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 2000)
            } catch (e2: Exception) {
                Log.e("EmergencyCallActivity", "Dialer fallback failed: ${e2.message}")
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // If we are no longer visible, we can safely finish
        if (!isFinishing) {
            finish()
        }
    }
}