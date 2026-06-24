package com.f2fk.geofence_foreground_service

import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.f2fk.geofence_foreground_service.utils.GeofenceDebugLog

/**
 * Expedited WorkManager fallback when direct Dart execution from the broadcast receiver fails.
 */
object GeofenceWorkHelper {
    fun enqueueExpedited(
        context: android.content.Context,
        zoneId: String,
        payload: String?,
        isInDebug: Boolean
    ) {
        GeofenceDebugLog.w(
            "WORK_ENQUEUE",
            "zone=$zoneId payload=$payload ${GeofenceDebugLog.contextSnapshot(context)}"
        )

        val inputData = Data.Builder()
            .putString(GeofenceTriggerExecutor.ZONE_ID, zoneId)
            .putBoolean(GeofenceTriggerExecutor.IS_IN_DEBUG_MODE_KEY, isInDebug)
            .apply {
                payload?.let { putString(GeofenceTriggerExecutor.PAYLOAD_KEY, it) }
            }
            .build()

        val request = OneTimeWorkRequest.Builder(BackgroundWorker::class.java)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
