package com.f2fk.geofence_foreground_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.f2fk.geofence_foreground_service.utils.GeofenceDebugLog
import com.f2fk.geofence_foreground_service.utils.SharedPreferenceHelper
import com.f2fk.geofence_foreground_service.utils.extraNameGen
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofence transitions delivered by the platform Geofence API.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val receiveStartedAt = System.currentTimeMillis()
        val pendingResult = goAsync()

        GeofenceDebugLog.d(
            "BROADCAST_RECEIVED",
            "${GeofenceDebugLog.contextSnapshot(context)} action=${intent.action}"
        )

        val geofencingEvent = try {
            GeofencingEvent.fromIntent(intent)
        } catch (e: Exception) {
            GeofenceDebugLog.e("BROADCAST_PARSE_FAILED", GeofenceDebugLog.contextSnapshot(context), e)
            pendingResult.finish()
            return
        }

        if (geofencingEvent == null) {
            GeofenceDebugLog.e("BROADCAST_NULL_EVENT", GeofenceDebugLog.contextSnapshot(context))
            pendingResult.finish()
            return
        }

        if (geofencingEvent.hasError()) {
            GeofenceDebugLog.e(
                "BROADCAST_GEOFENCE_ERROR",
                "errorCode=${geofencingEvent.errorCode} ${GeofenceDebugLog.contextSnapshot(context)}"
            )
            pendingResult.finish()
            return
        }

        if (!SharedPreferenceHelper.hasCallbackHandle(context)) {
            GeofenceDebugLog.e(
                "BROADCAST_NO_CALLBACK",
                "callback handle missing ${GeofenceDebugLog.contextSnapshot(context)}"
            )
            pendingResult.finish()
            return
        }

        val isInDebugMode = intent.getBooleanExtra(
            context.extraNameGen(Constants.isInDebugMode),
            SharedPreferenceHelper.isInDebugMode(context)
        )

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences

        if (triggeringGeofences.isNullOrEmpty()) {
            GeofenceDebugLog.e(
                "BROADCAST_NO_TRIGGERING_GEOFENCES",
                GeofenceDebugLog.contextSnapshot(context)
            )
            pendingResult.finish()
            return
        }

        val appContext = context.applicationContext
        val payload = geofenceTransition.toString()
        val zoneIds = triggeringGeofences.map { it.requestId }

        GeofenceDebugLog.d(
            "BROADCAST_PARSED",
            "zones=$zoneIds transition=${GeofenceDebugLog.transitionName(geofenceTransition)} " +
                "isDebug=$isInDebugMode ${GeofenceDebugLog.contextSnapshot(context)}"
        )

        val wakeLock = acquireWakeLock(appContext)
        GeofenceDebugLog.d(
            "WAKE_LOCK",
            if (wakeLock != null) "acquired" else "failed to acquire"
        )

        processZonesSequentially(
            appContext,
            zoneIds,
            index = 0,
            payload = payload,
            isInDebugMode = isInDebugMode,
            receiveStartedAt = receiveStartedAt
        ) {
            releaseWakeLock(wakeLock)
            GeofenceDebugLog.d(
                "BROADCAST_COMPLETE",
                "elapsedMs=${System.currentTimeMillis() - receiveStartedAt} " +
                    GeofenceDebugLog.contextSnapshot(appContext)
            )
            pendingResult.finish()
        }
    }

    private fun processZonesSequentially(
        context: Context,
        zoneIds: List<String>,
        index: Int,
        payload: String,
        isInDebugMode: Boolean,
        receiveStartedAt: Long,
        onAllComplete: () -> Unit
    ) {
        if (index >= zoneIds.size) {
            onAllComplete()
            return
        }

        val zoneId = zoneIds[index]
        GeofenceDebugLog.d(
            "TRIGGER_START",
            "zone=$zoneId payload=$payload index=$index/${zoneIds.size} " +
                "elapsedSinceBroadcastMs=${System.currentTimeMillis() - receiveStartedAt} " +
                GeofenceDebugLog.contextSnapshot(context)
        )

        GeofenceTriggerExecutor.execute(
            context,
            zoneId,
            payload,
            isInDebugMode
        ) { success ->
            GeofenceDebugLog.d(
                "TRIGGER_END",
                "zone=$zoneId success=$success ${GeofenceDebugLog.contextSnapshot(context)}"
            )

            if (!success) {
                GeofenceDebugLog.w(
                    "TRIGGER_FALLBACK_WORK",
                    "zone=$zoneId enqueueing expedited WorkManager job"
                )
                GeofenceWorkHelper.enqueueExpedited(
                    context,
                    zoneId,
                    payload,
                    isInDebugMode
                )
            }

            processZonesSequentially(
                context,
                zoneIds,
                index + 1,
                payload,
                isInDebugMode,
                receiveStartedAt,
                onAllComplete
            )
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "geofence_foreground_service:trigger"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            GeofenceDebugLog.e("WAKE_LOCK", "acquire failed", e)
            null
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
                GeofenceDebugLog.d("WAKE_LOCK", "released")
            }
        } catch (e: Exception) {
            GeofenceDebugLog.e("WAKE_LOCK", "release failed", e)
        }
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
