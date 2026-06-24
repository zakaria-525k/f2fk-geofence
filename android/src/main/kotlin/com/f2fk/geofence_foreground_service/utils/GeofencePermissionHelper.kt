package com.f2fk.geofence_foreground_service.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object GeofencePermissionHelper {
    fun logPermissionSnapshot(context: Context, step: String) {
        val fineGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }
        val batteryIgnored = isIgnoringBatteryOptimizations(context)

        GeofenceDebugLog.d(
            step,
            "fineLocation=$fineGranted coarseLocation=$coarseGranted " +
                "backgroundLocation=$backgroundGranted batteryOptimizationIgnored=$batteryIgnored " +
                "sdk=${Build.VERSION.SDK_INT} ${GeofenceDebugLog.contextSnapshot(context)}"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundGranted) {
            GeofenceDebugLog.w(
                "BACKGROUND_LOCATION_MISSING",
                "ACCESS_BACKGROUND_LOCATION is NOT granted. Geofence events will only " +
                    "be delivered while the app is in the foreground. Request 'Allow all the time' " +
                    "location permission in app settings."
            )
        }

        if (!batteryIgnored) {
            GeofenceDebugLog.w(
                "BATTERY_OPTIMIZATION_ACTIVE",
                "App is subject to battery optimization. This can delay or block " +
                    "background geofence delivery on some devices."
            )
        }
    }

    fun hasBackgroundLocationForGeofencing(context: Context): Boolean {
        val hasForegroundLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasForegroundLocation) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            GeofenceDebugLog.e("BATTERY_OPTIMIZATION_CHECK_FAILED", e.message ?: "unknown", e)
            false
        }
    }
}
