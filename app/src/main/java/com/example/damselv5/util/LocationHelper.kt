package com.example.damselv5.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*

class LocationHelper(private val c: Context) {

    private val fLC: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(c)

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): String {
        return try {
            
            val fL: Location? = withTimeoutOrNull(5000) {
                fLC.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            }

            if (fL != null) {
                fLS(fL, "Current")
            } else {
                
                val lL = fLC.lastLocation.await()
                if (lL != null) {
                    fLS(lL, "Last Known")
                } else {
                    "Location not available (GPS might be disabled)"
                }
            }
        } catch (e: Exception) {
            "Location unavailable: ${e.localizedMessage}"
        }
    }

    private fun fLS(l: Location, t: String): String {
        val u = "https://www.google.com/maps/search/?api=1&query=${l.latitude},${l.longitude}"
        val tm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(l.time))
        return "$t Location ($tm): $u"
    }
}