package com.f2fk.geofence_foreground_service

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture

/**
 * WorkManager fallback worker that delegates to [GeofenceTriggerExecutor].
 *
 * Geofence triggers are handled directly in [GeofenceBroadcastReceiver] for immediate background
 * execution. This worker remains for compatibility with any in-flight WorkManager jobs.
 */
class BackgroundWorker(
    applicationContext: Context,
    private val workerParams: WorkerParameters
) : ListenableWorker(applicationContext, workerParams) {

    private var completer: CallbackToFutureAdapter.Completer<Result>? = null

    private val resolvableFuture = CallbackToFutureAdapter.getFuture { completer ->
        this.completer = completer
        null
    }

    override fun startWork(): ListenableFuture<Result> {
        val zoneId = workerParams.inputData.getString(GeofenceTriggerExecutor.ZONE_ID)
        if (zoneId.isNullOrEmpty()) {
            completer?.set(Result.failure())
            return resolvableFuture
        }

        val payload = workerParams.inputData.getString(GeofenceTriggerExecutor.PAYLOAD_KEY)
        val isInDebug = workerParams.inputData.getBoolean(
            GeofenceTriggerExecutor.IS_IN_DEBUG_MODE_KEY,
            false
        )

        GeofenceTriggerExecutor.execute(
            applicationContext,
            zoneId,
            payload,
            isInDebug
        ) { success ->
            completer?.set(if (success) Result.success() else Result.retry())
        }

        return resolvableFuture
    }
}
