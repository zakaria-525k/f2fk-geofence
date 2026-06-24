package com.f2fk.geofence_foreground_service.utils

import android.content.Context

object SharedPreferenceHelper {
    private const val SHARED_PREFS_FILE_NAME = "geofence_foreground_service_plugin"
    private const val CALLBACK_DISPATCHER_HANDLE_KEY = "ps.byshy.geofence.CALLBACK_DISPATCHER_HANDLE_KEY"
    private const val IS_IN_DEBUG_MODE_KEY = "ps.byshy.geofence.IS_IN_DEBUG_MODE"
    private fun Context.prefs() = getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    fun saveCallbackDispatcherHandleKey(ctx: Context, callbackHandle: Long) {
        ctx.prefs()
            .edit()
            .putLong(CALLBACK_DISPATCHER_HANDLE_KEY, callbackHandle)
            .apply()
    }

    fun saveIsInDebugMode(ctx: Context, isInDebugMode: Boolean) {
        ctx.prefs()
            .edit()
            .putBoolean(IS_IN_DEBUG_MODE_KEY, isInDebugMode)
            .apply()
    }

    fun getCallbackHandle(ctx: Context): Long {
        return ctx.prefs().getLong(CALLBACK_DISPATCHER_HANDLE_KEY, -1L)
    }

    fun isInDebugMode(ctx: Context): Boolean {
        return ctx.prefs().getBoolean(IS_IN_DEBUG_MODE_KEY, false)
    }

    fun hasCallbackHandle(ctx: Context) = ctx.prefs().contains(CALLBACK_DISPATCHER_HANDLE_KEY)
}
