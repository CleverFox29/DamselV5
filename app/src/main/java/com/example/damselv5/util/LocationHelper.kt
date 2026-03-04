package com.example.damselv5.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): String {
        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val url = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    continuation.resume("Last Location: $url")
                } else {
                    continuation.resume("Location not available")
                }
            }.addOnFailureListener {
                continuation.resume("Failed to get location")
            }
        }
    }
}