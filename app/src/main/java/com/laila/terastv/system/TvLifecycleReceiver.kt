package com.laila.terastv.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.laila.terastv.ui.ForegroundAppService

class TvLifecycleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TvLifecycle"
        private const val PREFS = "tv_prefs"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive: $action")

        when (action) {
            // Normal boot (after user unlock on some devices)
            Intent.ACTION_BOOT_COMPLETED,
                // Direct-boot phase (before user unlock on newer Androids)
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
                // App updated/reinstalled: restart the service
            Intent.ACTION_MY_PACKAGE_REPLACED,
                // Some devices only allow starting long-running work after user unlock
            Intent.ACTION_USER_UNLOCKED -> {
                startTrackingService(context)
            }

            // Powering off / shutdown → mark a pending lap so it can be posted next launch
            Intent.ACTION_SHUTDOWN -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val start = prefs.getLong("tv_timer_start_ms", 0L)
                if (start > 0L) {
                    val now = System.currentTimeMillis()
                    val secs = (((now - start).coerceAtLeast(0L)) / 1000L).toInt()
                    Log.d(TAG, "Shutdown: saving pending uptime $secs s")
                    prefs.edit()
                        .putBoolean("pending_uptime", true)
                        .putLong("pending_uptime_end_ms", now)
                        .putInt("pending_uptime_secs", secs)
                        .apply()
                }
            }
        }
    }

    private fun startTrackingService(context: Context) {
        val svc = Intent(context, ForegroundAppService::class.java)

        // On Android O+ you must use startForegroundService from a receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
        Log.d(TAG, "ForegroundAppService start requested from receiver")
    }
}
