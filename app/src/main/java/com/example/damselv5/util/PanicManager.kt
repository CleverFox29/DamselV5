package com.example.damselv5.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Handles the 10-second panic timer logic with countdown toasts.
 * Includes a small debounce to prevent accidental double-triggers.
 */
class PanicManager(
    private val context: Context,
    private val onTriggerEmergency: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var isTimerRunning = false
    private var countdownValue = 10
    private var currentToast: Toast? = null
    private var lastSignalTime: Long = 0

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdownValue > 0) {
                showToast("EMERGENCY in $countdownValue seconds! Press again to CANCEL.")
                countdownValue--
                handler.postDelayed(this, 1000)
            } else {
                Log.d("PanicManager", "Countdown finished! Triggering emergency.")
                onTriggerEmergency()
                isTimerRunning = false
            }
        }
    }

    /**
     * Called when "#" is received via BLE or Simulation.
     */
    fun handlePanicSignal() {
        val currentTime = System.currentTimeMillis()
        // Ignore signals received within 1 second of the last one to prevent double-triggers
        if (currentTime - lastSignalTime < 1000) {
            Log.d("PanicManager", "Ignored rapid duplicate panic signal.")
            return
        }
        lastSignalTime = currentTime

        handler.post {
            if (isTimerRunning) {
                // Second signal received within 10s -> Cancel
                cancelPanicTimer()
                showToast("Panic Alert CANCELED.")
                Log.d("PanicManager", "Panic Alert Canceled.")
            } else {
                // First signal received -> Start 10s countdown
                startPanicTimer()
                Log.d("PanicManager", "Panic Signal Received. Starting countdown.")
            }
        }
    }

    private fun startPanicTimer() {
        isTimerRunning = true
        countdownValue = 10
        handler.post(countdownRunnable)
    }

    private fun cancelPanicTimer() {
        handler.removeCallbacks(countdownRunnable)
        isTimerRunning = false
    }

    private fun showToast(message: String) {
        handler.post {
            currentToast?.cancel()
            currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            currentToast?.show()
        }
    }
}