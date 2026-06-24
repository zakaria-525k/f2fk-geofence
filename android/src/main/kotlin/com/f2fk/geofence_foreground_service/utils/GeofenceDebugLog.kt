package com.f2fk.geofence_foreground_service.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.google.android.gms.location.Geofence

object GeofenceDebugLog {
    const val TAG = "GeofencePlugin"

    fun d(step: String, details: String = "") {
        val suffix = if (details.isEmpty()) "" else " | $details"
        Log.d(TAG, "[$step]${suffix}")
    }

    fun w(step: String, details: String = "") {
        val suffix = if (details.isEmpty()) "" else " | $details"
        Log.w(TAG, "[$step]${suffix}")
    }

    fun e(step: String, details: String = "", throwable: Throwable? = null) {
        val suffix = if (details.isEmpty()) "" else " | $details"
        if (throwable != null) {
            Log.e(TAG, "[$step]${suffix}", throwable)
        } else {
            Log.e(TAG, "[$step]${suffix}")
        }
    }

    fun contextSnapshot(context: Context): String {
        return buildString {
            append("pid=${Process.myPid()}")
            append(" time=${System.currentTimeMillis()}")
            append(" thread=${Thread.currentThread().name}")
            append(" appState=${describeAppState(context)}")
        }
    }

    fun transitionName(transition: Int): String {
        return when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER(1)"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT(2)"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL(4)"
            else -> "UNKNOWN($transition)"
        }
    }

    private fun describeAppState(context: Context): String {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processInfo = activityManager.runningAppProcesses?.firstOrNull {
                it.pid == Process.myPid()
            }

            if (processInfo == null) {
                "UNKNOWN"
            } else {
                when (processInfo.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ->
                        "FOREGROUND"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE ->
                        "VISIBLE"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE ->
                        "FOREGROUND_SERVICE"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING ->
                        "TOP_SLEEPING"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND ->
                        "BACKGROUND"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED ->
                        "CACHED"
                    else -> "OTHER(${processInfo.importance})"
                }
            }
        } catch (e: Exception) {
            "ERROR(${e.message})"
        }
    }
}
