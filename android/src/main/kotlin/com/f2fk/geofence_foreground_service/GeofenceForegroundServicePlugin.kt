package com.f2fk.geofence_foreground_service

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.f2fk.geofence_foreground_service.models.NotificationIconData
import com.f2fk.geofence_foreground_service.models.Zone
import com.f2fk.geofence_foreground_service.models.ZonesList
import com.f2fk.geofence_foreground_service.utils.GeofenceDebugLog
import com.f2fk.geofence_foreground_service.utils.GeofencePermissionHelper
import com.f2fk.geofence_foreground_service.utils.SharedPreferenceHelper
import com.f2fk.geofence_foreground_service.utils.calculateCenter
import com.f2fk.geofence_foreground_service.utils.extraNameGen
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** GeofenceForegroundServicePlugin */
class GeofenceForegroundServicePlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    companion object {
        const val geofenceRegisterFailure: Int = 525601
        const val geofenceRemoveFailure: Int = 525602

        // Plugin registration is now handled automatically by Flutter v2 embedding
        // No manual plugin registration needed
    }

    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    private var channelId: String? = null
    private var contentTitle: String? = null
    private var contentText: String? = null
    private var serviceId: Int? = null

    private var isInDebugMode: Boolean = false
    private var iconData: NotificationIconData? = null

    private var activity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(
            flutterPluginBinding.binaryMessenger, "ps.byshy.geofence/foreground_geofence_foreground_service"
        )

        channel.setMethodCallHandler(this)

        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "startGeofencingService" -> {
                try {
                    // No foreground service is started anymore. Geofencing relies solely on the
                    // platform Geofence API (delivered to a BroadcastReceiver), which is compliant
                    // with the Google Play foreground service policy and does not require
                    // FOREGROUND_SERVICE_LOCATION. We only persist the configuration needed to
                    // deliver triggers to the Dart `backgroundTriggerHandler`.
                    SharedPreferenceHelper.saveCallbackDispatcherHandleKey(
                        context, call.argument<Long>(Constants.callbackHandle)!!
                    )

                    isInDebugMode = call.argument<Boolean>(Constants.isInDebugMode) ?: false
                    SharedPreferenceHelper.saveIsInDebugMode(context, isInDebugMode)

                    channelId = call.argument<String>(Constants.channelId)
                    contentTitle = call.argument<String>(Constants.contentTitle)
                    contentText = call.argument<String>(Constants.contentText)
                    serviceId = call.argument<Int>(Constants.serviceId)

                    val iconDataJson: Map<String, Any>? = call.argument<Map<String, Any>>(
                        Constants.iconData
                    )

                    if (iconDataJson != null) {
                        iconData = NotificationIconData.fromJson(
                            iconDataJson
                        )
                    }

                    GeofencePermissionHelper.logPermissionSnapshot(context, "PERMISSIONS_AT_START")
                    GeofenceDebugLog.d(
                        "SERVICE_STARTED",
                        "callbackRegistered=true isDebug=$isInDebugMode " +
                            GeofenceDebugLog.contextSnapshot(context)
                    )

                    result.success(true)
                } catch (e: Exception) {
                    GeofenceDebugLog.e("SERVICE_START_FAILED", GeofenceDebugLog.contextSnapshot(context), e)
                    result.success(false)
                }
            }

            "stopGeofencingService" -> {
                // There is no longer a foreground service to stop. Registered geofences keep
                // working through the platform Geofence API and are removed explicitly via
                // removeGeofence/removeAllGeoFences. Kept as a no-op for API compatibility.
                result.success(true)
            }

            "isForegroundServiceRunning" -> {
                // Maintained for API compatibility. Reflects whether geofencing has been
                // initialized (a callback handle is registered) since there is no longer a
                // foreground service running.
                result.success(SharedPreferenceHelper.hasCallbackHandle(context))
            }

            "addGeofence" -> {
                val zone: Zone = Zone.fromJson(call.arguments as Map<String, Any>)

                addGeofence(zone, result)
            }

            "addGeoFences" -> {
                val zonesList: ZonesList = ZonesList.fromJson(call.arguments as Map<String, Any>)

                addGeoFences(zonesList, result)
            }

            "removeGeofence" -> {
                val zonesId: String = call.argument(Constants.zoneId)!!

                removeGeofence(listOf(zonesId), result)
            }

            "removeAllGeoFences" -> {
                removeAllGeoFences(result)
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    private fun addGeofence(zone: Zone, result: Result) {
        if (!SharedPreferenceHelper.hasCallbackHandle(context)) {
            result.error(
                "1",
                "You have not properly initialized the Flutter Geofence foreground service Plugin. " + "You should ensure you have called the 'startGeofencingService' function first! " + "The `callbackDispatcher` is a top level function. See example in repository.",
                null
            )
            return
        }

        val geofencingRequest = GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)

        val centerCoordinate: LatLng = calculateCenter(
            zone.coordinates ?: emptyList()
        )

        val geofenceBuilder = Geofence.Builder().setRequestId(zone.zoneId).setCircularRegion(
                centerCoordinate.latitude, centerCoordinate.longitude, zone.radius
            ).setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setLoiteringDelay(120000)

        if (zone.notificationResponsivenessMs != null) {
            Log.v("addGeofence", "Setting notification responsiveness to ${zone.notificationResponsivenessMs}")
            geofenceBuilder.setNotificationResponsiveness(zone.notificationResponsivenessMs)
        }

        var geofence = geofenceBuilder.build()

        geofencingRequest.addGeofence(geofence)

        val pendingIntent: PendingIntent = getGeofencePendingIntent()

        val geofencingClient = LocationServices.getGeofencingClient(context)

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
//            return
        }

        GeofencePermissionHelper.logPermissionSnapshot(context, "PERMISSIONS_AT_REGISTER")
        GeofenceDebugLog.d(
            "GEOFENCE_REGISTER_REQUEST",
            "zone=${zone.zoneId} lat=${centerCoordinate.latitude} lng=${centerCoordinate.longitude} " +
                "radius=${zone.radius} responsivenessMs=${zone.notificationResponsivenessMs} " +
                "backgroundGeofencingAllowed=${GeofencePermissionHelper.hasBackgroundLocationForGeofencing(context)} " +
                GeofenceDebugLog.contextSnapshot(context)
        )

        geofencingClient.addGeofences(geofencingRequest.build(), pendingIntent).addOnSuccessListener {
                GeofenceDebugLog.d(
                    "GEOFENCE_REGISTER_SUCCESS",
                    "zone=${zone.zoneId} pendingIntentAction=${Constants.geofenceBroadcastAction}"
                )
                result.success(true)
            }.addOnFailureListener { e ->
                val stackTraceString = e.stackTraceToString()
                GeofenceDebugLog.e(
                    "GEOFENCE_REGISTER_FAILED",
                    "zone=${zone.zoneId} message=${e.message}",
                    e
                )

                result.error(
                    geofenceRegisterFailure.toString(), e.message, stackTraceString
                )
            }
    }

    private fun addGeoFences(zones: ZonesList, result: Result) {
        (zones.zones ?: emptyList()).forEach {
            addGeofence(it, result)
        }
    }

    private fun removeGeofence(geofenceRequestIds: List<String>, result: Result) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        geofencingClient.removeGeofences(geofenceRequestIds).addOnSuccessListener {
            result.success(true)
        }.addOnFailureListener { e: java.lang.Exception? ->
            result.error(
                geofenceRemoveFailure.toString(), e?.message, e?.stackTrace
            )
        }
    }

    private fun removeAllGeoFences(result: Result) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        val pendingIntent: PendingIntent = getGeofencePendingIntent()

        geofencingClient.removeGeofences(pendingIntent).addOnSuccessListener {
            result.success(true)
        }.addOnFailureListener { e ->
            val stackTraceString = e.stackTraceToString()
            result.error(
                geofenceRemoveFailure.toString(), e.message, stackTraceString
            )
        }
    }

    /**
     * Builds the [PendingIntent] used by the platform Geofence API to deliver transitions.
     *
     * The intent targets [GeofenceBroadcastReceiver] (a [android.content.BroadcastReceiver]), so no
     * foreground service is required. A single, stable PendingIntent is shared across all
     * registered geofences so that it can also be used to remove them.
     */
    private fun getGeofencePendingIntent(): PendingIntent {
        val geofenceIntent = Intent(context, GeofenceBroadcastReceiver::class.java)
        geofenceIntent.action = Constants.geofenceBroadcastAction
        geofenceIntent.setPackage(context.packageName)

        geofenceIntent.putExtra(
            context.extraNameGen(Constants.isInDebugMode), isInDebugMode
        )

        val flags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(context, 0, geofenceIntent, flags)
    }


    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {}
}
