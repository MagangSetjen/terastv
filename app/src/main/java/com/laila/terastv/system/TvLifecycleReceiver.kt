package com.laila.terastv.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TvLifecycleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
        when (intent.action) {
            Intent.ACTION_SHUTDOWN -> {
                try {
                    val start = prefs.getLong("tv_timer_start_ms", 0L)
                    val end = System.currentTimeMillis()
                    if (start > 0L) {
                        val uptimeSecs = ((end - start) / 1000L).toInt().coerceAtLeast(0)
                        prefs.edit()
                            .putBoolean("pending_uptime", true)
                            .putLong("pending_uptime_end_ms", end)
                            .putInt("pending_uptime_secs", uptimeSecs)
                            .apply()
                        Log.d("TvLifecycle", "Saved pending TV uptime: ${uptimeSecs}s")
                    }
                } catch (t: Throwable) {
                    Log.e("TvLifecycle", "Failed to store pending uptime on shutdown", t)
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // Start a fresh timer right after boot (the service will post the pending lap)
                prefs.edit()
                    .putLong("tv_timer_start_ms", System.currentTimeMillis())
                    .apply()
                Log.d("TvLifecycle", "Boot completed → timer start reset")
            }
        }
    }
}
