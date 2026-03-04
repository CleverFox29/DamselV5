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

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): String {
        return try {
            // 1. Try to get a fresh current location, but don't wait more than 5 seconds
            val freshLocation: Location? = withTimeoutOrNull(5000) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            }

            if (freshLocation != null) {
                formatLocationString(freshLocation, "Current")
            } else {
                // 2. Fallback to Last Known Location if fresh fails or times out
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null) {
                    formatLocationString(lastLocation, "Last Known")
                } else {
                    "Location not available (GPS might be disabled)"
                }
            }
        } catch (e: Exception) {
            "Location unavailable: ${e.localizedMessage}"
        }
    }

    private fun formatLocationString(location: Location, type: String): String {
        val url = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(location.time))
        return "$type Location ($time): $url"
    }
}