package com.f2fk.geofence_foreground_service

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.f2fk.geofence_foreground_service.utils.DebugHelper
import com.f2fk.geofence_foreground_service.utils.GeofenceDebugLog
import com.f2fk.geofence_foreground_service.utils.SharedPreferenceHelper
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the Dart `backgroundTriggerHandler` in a headless Flutter engine on the main thread.
 */
object GeofenceTriggerExecutor {
    private const val CALLBACK_TIMEOUT_MS = 60_000L

    const val BACKGROUND_CHANNEL_NAME =
        "ps.byshy.geofence/background_geofence_foreground_service"
    const val BACKGROUND_CHANNEL_INITIALIZED = "backgroundChannelInitialized"
    const val PAYLOAD_KEY = "ps.byshy.geofence.INPUT_DATA"
    const val ZONE_ID = "ps.byshy.geofence.ZONE_ID"
    const val IS_IN_DEBUG_MODE_KEY = "ps.byshy.geofence.IS_IN_DEBUG_MODE_KEY"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val flutterLoader = FlutterLoader()

    fun execute(
        applicationContext: Context,
        zoneId: String,
        payload: String?,
        isInDebug: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        GeofenceDebugLog.d(
            "EXECUTOR_SCHEDULED",
            "zone=$zoneId payload=$payload ${GeofenceDebugLog.contextSnapshot(applicationContext)}"
        )

        mainHandler.post {
            val randomThreadIdentifier = Random().nextInt()
            val startTime = System.currentTimeMillis()
            var engine: FlutterEngine? = null
            var backgroundChannel: MethodChannel? = null
            val completed = AtomicBoolean(false)
            lateinit var timeoutRunnable: Runnable

            fun finish(success: Boolean) {
                if (!completed.compareAndSet(false, true)) {
                    GeofenceDebugLog.w(
                        "EXECUTOR_FINISH_SKIPPED",
                        "zone=$zoneId already completed"
                    )
                    return
                }

                mainHandler.removeCallbacks(timeoutRunnable)

                val fetchDuration = System.currentTimeMillis() - startTime
                GeofenceDebugLog.d(
                    "EXECUTOR_FINISH",
                    "zone=$zoneId success=$success durationMs=$fetchDuration " +
                        GeofenceDebugLog.contextSnapshot(applicationContext)
                )

                if (isInDebug) {
                    val workerResult = if (success) {
                        androidx.work.ListenableWorker.Result.success()
                    } else {
                        androidx.work.ListenableWorker.Result.failure()
                    }
                    DebugHelper.postTaskCompleteNotification(
                        applicationContext,
                        randomThreadIdentifier,
                        zoneId,
                        payload,
                        fetchDuration,
                        workerResult
                    )
                }

                engine?.destroy()
                engine = null
                onComplete(success)
            }

            timeoutRunnable = Runnable {
                GeofenceDebugLog.e(
                    "EXECUTOR_TIMEOUT",
                    "zone=$zoneId payload=$payload timeoutMs=$CALLBACK_TIMEOUT_MS " +
                        GeofenceDebugLog.contextSnapshot(applicationContext)
                )
                finish(false)
            }

            mainHandler.postDelayed(timeoutRunnable, CALLBACK_TIMEOUT_MS)

            try {
                GeofenceDebugLog.d(
                    "EXECUTOR_FLUTTER_INIT_START",
                    "zone=$zoneId ${GeofenceDebugLog.contextSnapshot(applicationContext)}"
                )
                ensureFlutterInitialized(applicationContext)
                GeofenceDebugLog.d(
                    "EXECUTOR_FLUTTER_INIT_DONE",
                    "zone=$zoneId loaderInitialized=${flutterLoader.initialized()}"
                )

                val callbackHandle = SharedPreferenceHelper.getCallbackHandle(applicationContext)
                if (callbackHandle == -1L) {
                    GeofenceDebugLog.e("EXECUTOR_NO_CALLBACK", "zone=$zoneId")
                    finish(false)
                    return@post
                }

                val callbackInfo =
                    FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
                if (callbackInfo == null) {
                    GeofenceDebugLog.e(
                        "EXECUTOR_INVALID_CALLBACK",
                        "zone=$zoneId handle=$callbackHandle"
                    )
                    finish(false)
                    return@post
                }

                val dartBundlePath = flutterLoader.findAppBundlePath()
                GeofenceDebugLog.d(
                    "EXECUTOR_CALLBACK_INFO",
                    "zone=$zoneId handle=$callbackHandle " +
                        "callbackName=${callbackInfo.callbackName} " +
                        "callbackClass=${callbackInfo.callbackClassName} " +
                        "bundlePath=$dartBundlePath"
                )

                if (isInDebug) {
                    DebugHelper.postTaskStarting(
                        applicationContext,
                        randomThreadIdentifier,
                        zoneId,
                        payload,
                        callbackHandle,
                        callbackInfo,
                        dartBundlePath
                    )
                }

                engine = FlutterEngine(applicationContext)
                val flutterEngine = engine!!

                backgroundChannel = MethodChannel(
                    flutterEngine.dartExecutor,
                    BACKGROUND_CHANNEL_NAME
                )

                backgroundChannel!!.setMethodCallHandler { call: MethodCall, _: MethodChannel.Result ->
                    GeofenceDebugLog.d(
                        "EXECUTOR_DART_TO_NATIVE",
                        "zone=$zoneId method=${call.method} args=${call.arguments}"
                    )

                    if (call.method == BACKGROUND_CHANNEL_INITIALIZED) {
                        GeofenceDebugLog.d(
                            "EXECUTOR_DART_ISOLATE_READY",
                            "zone=$zoneId invoking onResultSend payload=$payload"
                        )

                        backgroundChannel!!.invokeMethod(
                            "onResultSend",
                            mapOf(ZONE_ID to zoneId, PAYLOAD_KEY to payload),
                            object : MethodChannel.Result {
                                override fun notImplemented() {
                                    GeofenceDebugLog.e(
                                        "EXECUTOR_DART_RESULT",
                                        "zone=$zoneId onResultSend notImplemented"
                                    )
                                    finish(false)
                                }

                                override fun error(
                                    errorCode: String,
                                    errorMessage: String?,
                                    errorDetails: Any?
                                ) {
                                    GeofenceDebugLog.e(
                                        "EXECUTOR_DART_RESULT",
                                        "zone=$zoneId errorCode=$errorCode errorMessage=$errorMessage " +
                                            "details=$errorDetails"
                                    )
                                    finish(false)
                                }

                                override fun success(receivedResult: Any?) {
                                    val wasSuccessful =
                                        receivedResult?.let { it as? Boolean } == true
                                    GeofenceDebugLog.d(
                                        "EXECUTOR_DART_RESULT",
                                        "zone=$zoneId result=$receivedResult success=$wasSuccessful"
                                    )
                                    finish(wasSuccessful)
                                }
                            }
                        )
                    }
                }

                GeofenceDebugLog.d(
                    "EXECUTOR_DART_START",
                    "zone=$zoneId payload=$payload ${GeofenceDebugLog.contextSnapshot(applicationContext)}"
                )
                flutterEngine.dartExecutor.executeDartCallback(
                    DartExecutor.DartCallback(
                        applicationContext.assets,
                        dartBundlePath,
                        callbackInfo
                    )
                )
            } catch (e: Exception) {
                GeofenceDebugLog.e(
                    "EXECUTOR_FAILED",
                    "zone=$zoneId ${GeofenceDebugLog.contextSnapshot(applicationContext)}",
                    e
                )
                finish(false)
            }
        }
    }

    private fun ensureFlutterInitialized(applicationContext: Context) {
        if (!flutterLoader.initialized()) {
            flutterLoader.startInitialization(applicationContext)
        }
        flutterLoader.ensureInitializationComplete(applicationContext, null)
    }
}
