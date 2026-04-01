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


class EmergencyCallActivity : ComponentActivity() {
    override fun onCreate(sI: Bundle?) {
        super.onCreate(sI)

        
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

        val pN = intent.getStringExtra("PHONE_NUMBER")
        
        
        if (!pN.isNullOrBlank()) {
            mC(pN)
        } else {
            finish()
        }
    }

    private fun mC(n: String) {
        val nm = PhoneNumberUtils.normalizeNumber(n)
        val pU = Uri.parse("tel:$nm")
        
        
        val cI = Intent(Intent.ACTION_CALL, pU).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            startActivity(cI)
            
            
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
        } catch (e: Exception) {
            
            
            val dI = Intent(Intent.ACTION_DIAL, pU).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(dI)
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 2000)
            } catch (e2: Exception) {
                
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (!isFinishing) {
            finish()
        }
    }
}