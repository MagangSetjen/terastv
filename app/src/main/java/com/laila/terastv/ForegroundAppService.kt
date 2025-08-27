package com.laila.terastv.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.laila.terastv.LoggingApi
import com.laila.terastv.MainActivity
import com.laila.terastv.R
import com.laila.terastv.RetrofitClient
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ForegroundAppService : LifecycleService() {

    companion object {
        private const val TAG = "FgAppService"
        private const val CHANNEL_ID = "app_usage_channel"
        private const val CHANNEL_NAME = "App Usage Tracking"
        private const val NOTIF_ID = 7101
        private const val TICK_MS = 1000L

        // Broadcasts
        const val ACTION_HISTORY_POSTED = "com.laila.terastv.ACTION_HISTORY_POSTED"
        const val ACTION_REQUEST_RESET_TIMER = "com.laila.terastv.ACTION_REQUEST_RESET_TIMER"

        private val IGNORE = setOf(
            "com.android.systemui",
            "com.google.android.tvlauncher",
            "com.android.launcher",
            "com.google.android.leanbacklauncher"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api: LoggingApi by lazy { RetrofitClient.api }

    private var currentPkg: String? = null
    private var currentLabel: String = ""
    private var sessionStartMs: Long = 0L

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_REQUEST_RESET_TIMER) {
                Log.d(TAG, "Manual reset requested")
                performResetAndPost("PowerOff")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        registerReceiver(resetReceiver, IntentFilter(ACTION_REQUEST_RESET_TIMER))
        startPolling()
    }

    override fun onDestroy() {
        try { unregisterReceiver(resetReceiver) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun startForegroundInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
                ch.setShowBadge(false)
                mgr.createNotificationChannel(ch)
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Tracking App Usage")
            .setContentText("Monitoring foreground apps…")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    private fun startPolling() {
        if (!hasUsageAccess()) {
            Log.w(TAG, "Usage access NOT granted")
        }
        sessionStartMs = System.currentTimeMillis()
        currentPkg = null
        currentLabel = ""

        scope.launch {
            while (isActive) {
                try {
                    val top = getTopPackage()
                    val ourPkg = packageName

                    if (top != null) {
                        val topIsIgnored = isIgnored(top, ourPkg)

                        if (currentPkg != null && currentPkg != top) {
                            if (!isIgnored(currentPkg!!, ourPkg)) {
                                commitSession(
                                    pkg = currentPkg!!,
                                    label = currentLabel,
                                    start = sessionStartMs,
                                    end = System.currentTimeMillis()
                                )
                            }
                            if (!topIsIgnored) {
                                currentPkg = top
                                currentLabel = appLabel(top)
                                sessionStartMs = System.currentTimeMillis()
                                maybeStartTvTimer()
                                Log.d(TAG, "▶ start $currentLabel ($top)")
                            } else {
                                currentPkg = null
                                currentLabel = ""
                                sessionStartMs = System.currentTimeMillis()
                            }
                        }

                        if (currentPkg == null && !topIsIgnored) {
                            currentPkg = top
                            currentLabel = appLabel(top)
                            sessionStartMs = System.currentTimeMillis()
                            maybeStartTvTimer()
                            Log.d(TAG, "▶ start $currentLabel ($top)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "poll error", e)
                }
                delay(TICK_MS)
            }
        }
    }

    private fun maybeStartTvTimer() {
        val prefs = getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
        if (prefs.getLong("tv_timer_start_ms", 0L) == 0L) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong("tv_timer_start_ms", now).apply()
            sendBroadcast(Intent(ACTION_HISTORY_POSTED))
        }
    }

    private fun isIgnored(pkg: String, ourPkg: String) = pkg == ourPkg || IGNORE.contains(pkg)

    private fun hasUsageAccess(): Boolean =
        try { Settings.Secure.getInt(contentResolver, "usage_stats_enabled", 0) == 1 }
        catch (_: Exception) { true }

    private fun getTopPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usm.queryEvents(begin, end)

        var lastPkg: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPkg = e.packageName
            }
        }
        return lastPkg
    }

    private fun appLabel(pkg: String): String =
        try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))?.toString() ?: pkg }
        catch (_: Exception) { pkg }

    private fun commitSession(pkg: String, label: String, start: Long, end: Long) {
        val secs = ((end - start) / 1000L).toInt().coerceAtLeast(1)
        if (secs < 2) return

        val prefs = getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
        val sn = prefs.getString("sn_tv", null) ?: return

        val tvStart = prefs.getLong("tv_timer_start_ms", System.currentTimeMillis())
        val tvSecs = (((end - tvStart).coerceAtLeast(0L)) / 1000L).toInt()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(end))

        val body = mapOf(
            "sn_tv" to sn,
            "date" to nowStr,
            "app_name" to label,
            "app_url" to pkg,
            "thumbnail" to "",
            "app_duration" to secs,
            "tv_duration" to tvSecs
        )

        Log.d(TAG, "⏹ end $label ($pkg) ${secs}s → POST /tv-history")
        api.postHistory(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (!response.isSuccessful) {
                    Log.w(TAG, "POST /tv-history HTTP ${response.code()} ${response.message()}")
                }
                sendBroadcast(Intent(ACTION_HISTORY_POSTED))
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e(TAG, "POST /tv-history failed", t)
            }
        })
    }

    /** Record a reset row (PowerOff) and immediately restart the timer. */
    private fun performResetAndPost(label: String) {
        val prefs = getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
        val sn = prefs.getString("sn_tv", null) ?: return

        val start = prefs.getLong("tv_timer_start_ms", 0L)
        val secsRaw = if (start > 0L)
            (((System.currentTimeMillis() - start).coerceAtLeast(0L)) / 1000L).toInt()
        else 0
        val safeSecs = if (secsRaw <= 0) 1 else secsRaw

        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val body = mapOf(
            "sn_tv" to sn,
            "date" to nowStr,
            "app_name" to label,  // “PowerOff”
            "app_url" to "",
            "thumbnail" to "",
            "app_duration" to safeSecs,
            "tv_duration" to safeSecs
        )

        Log.d(TAG, "Resetting timer → POST /tv-history ($label, ${safeSecs}s)")
        api.postHistory(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                // Snap UI: start timer immediately after reset
                val now = System.currentTimeMillis()
                prefs.edit().putLong("tv_timer_start_ms", now).apply()

                // Tell UI to refresh the table and timer
                sendBroadcast(Intent(ACTION_HISTORY_POSTED))
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e(TAG, "Reset POST failed", t)
                // Even if POST fails, restart the timer so UI doesn’t freeze
                val now = System.currentTimeMillis()
                prefs.edit().putLong("tv_timer_start_ms", now).apply()
                sendBroadcast(Intent(ACTION_HISTORY_POSTED))
            }
        })
    }
}
