package com.example.damselv5.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast

class PanicManager(
    private val c: Context,
    private val oCU: (Int) -> Unit,
    private val oTE: () -> Unit
) {

    private val h: Handler = Handler(Looper.getMainLooper())
    private var iTR: Boolean = false
    private var cV: Int = 4
    private var cT: Toast? = null
    private var lST: Long = 0

    private val cR: Runnable = object : Runnable {
        override fun run() {
            if (cV > 0) {
                sT("EMERGENCY in " + cV + " seconds! Press again to CANCEL.")
                vS()
                oCU.invoke(cV)
                cV = cV - 1
                h.postDelayed(this, 1000)

            } else {
                oCU.invoke(0)
                oTE.invoke()
                iTR = false

            }

        }

    }

    fun handlePanicSignal() {
        val currentTime: Long = System.currentTimeMillis()
        
        if (currentTime - lST < 1000) {
            return

        }
        lST = currentTime

        h.post(Runnable {
            if (iTR == true) {
                cPT()
                sT("Panic Alert CANCELED.")

            } else {
                sPT()

            }

        })

    }

    private fun sPT() {
        iTR = true
        cV = 4
        h.post(cR)

    }

    private fun cPT() {
        h.removeCallbacks(cR)
        iTR = false
        oCU.invoke(-1) 

    }

    private fun sT(m: String) {
        h.post(Runnable {
            if (cT != null) {
                cT!!.cancel()

            }
            cT = Toast.makeText(c, m, Toast.LENGTH_SHORT)
            if (cT != null) {
                cT!!.show()

            }

        })

    }

    private fun vS() {
        val v: Vibrator? = c.getSystemService(Vibrator::class.java)
        if (v != null) {
            if (v.hasVibrator() == true) {
                v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))

            }

        }

    }
}