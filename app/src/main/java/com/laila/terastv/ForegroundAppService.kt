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

        const val ACTION_HISTORY_POSTED = "com.laila.terastv.ACTION_HISTORY_POSTED"
        const val ACTION_REQUEST_RESET_TIMER = "com.laila.terastv.ACTION_REQUEST_RESET_TIMER"
        const val ACTION_TIMER_RESET_DONE = "com.laila.terastv.ACTION_TIMER_RESET_DONE"
        const val EXTRA_NEW_START_MS = "newStartMs"

        private val IGNORE = setOf(
            "com.android.systemui",
            "com.google.android.tvlauncher",
            "com.android.launcher",
            "com.google.android.leanbacklauncher"
        )

        // SharedPreferences keys
        private const val PREFS = "tv_prefs"
        private const val KEY_LAST_APP_TITLE = "last_app_title"
        private const val KEY_LAST_APP_TITLE_PKG = "last_app_title_pkg"
        private const val KEY_LAST_APP_TITLE_TIME = "last_app_title_time"
        // make title a little less strict so slow A11y updates are still picked up
        private const val TITLE_STALENESS_MS = 60_000L
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

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "SCREEN_OFF → lap & reset")
                    performResetAndPost("PowerOff")
                }
                Intent.ACTION_SCREEN_ON -> {
                    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val cur = prefs.getLong("tv_timer_start_ms", 0L)
                    if (cur <= 0L) {
                        val now = System.currentTimeMillis()
                        prefs.edit().putLong("tv_timer_start_ms", now).apply()
                        sendBroadcast(Intent(ACTION_TIMER_RESET_DONE).putExtra(EXTRA_NEW_START_MS, now))
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()

        registerReceiver(resetReceiver, IntentFilter(ACTION_REQUEST_RESET_TIMER))

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, screenFilter)
        }

        postPendingUptimeIfAny()
        startPolling()
    }

    override fun onDestroy() {
        try { unregisterReceiver(resetReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
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
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
        if (!hasUsageAccess()) Log.w(TAG, "Usage access NOT granted")
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

    /** Read the freshest captured title for this package; fallback to app label, with a last-resort title if label==pkg. */
    private fun getLatestTitleFor(pkg: String): String {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedPkg = prefs.getString(KEY_LAST_APP_TITLE_PKG, null)
        val title = prefs.getString(KEY_LAST_APP_TITLE, null)
        val ts = prefs.getLong(KEY_LAST_APP_TITLE_TIME, 0L)
        val age = System.currentTimeMillis() - ts
        val fresh = age in 0..TITLE_STALENESS_MS

        val label = appLabel(pkg)

        // Primary: exact package match + fresh + non-empty
        if (pkg == savedPkg && fresh && !title.isNullOrBlank()) {
            Log.d(TAG, "Using fresh title from prefs for $pkg: \"$title\" (age=${age}ms)")
            return title
        }

        // Fallback 1: if label is meaningful (not a package string), use it
        if (label.isNotBlank() && label != pkg) {
            Log.d(TAG, "Using app label for $pkg: \"$label\"")
            return label
        }

        // Fallback 2 (last resort): if saved title is fresh & non-empty, use it even if pkg didn’t match
        if (fresh && !title.isNullOrBlank()) {
            Log.d(TAG, "Using last-resort fresh title: \"$title\" (pkg mismatch, age=${age}ms)")
            return title
        }

        // Final fallback: package name
        Log.d(TAG, "Falling back to package name for $pkg")
        return pkg
    }

    private fun commitSession(pkg: String, label: String, start: Long, end: Long) {
        val secs = ((end - start) / 1000L).toInt().coerceAtLeast(1)
        if (secs < 2) return

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sn = prefs.getString("sn_tv", null) ?: return
        val npsn = prefs.getString("npsn", null) ?: return  // ★ include NPSN

        val tvStart = prefs.getLong("tv_timer_start_ms", System.currentTimeMillis())
        val tvSecs = (((end - tvStart).coerceAtLeast(0L)) / 1000L).toInt()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(end))

        val appTitle = getLatestTitleFor(pkg)

        val body = mapOf(
            "npsn" to npsn,                 // ★ now sent
            "sn_tv" to sn,
            "date" to nowStr,
            "app_name" to label,
            "app_title" to appTitle,
            "app_duration" to secs,
            "tv_duration" to tvSecs
        )

        Log.d(TAG, "⏹ end $label ($pkg) ${secs}s → POST /tv-history title=\"$appTitle\"")
        api.postHistory(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) sendBroadcast(Intent(ACTION_HISTORY_POSTED))
                else Log.w(TAG, "POST /tv-history HTTP ${response.code()} ${response.message()}")
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e(TAG, "POST /tv-history failed", t)
            }
        })
    }

    private fun performResetAndPost(label: String) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sn = prefs.getString("sn_tv", null) ?: return
        val npsn = prefs.getString("npsn", null) ?: return  // ★ include NPSN

        val start = prefs.getLong("tv_timer_start_ms", 0L)
        val now = System.currentTimeMillis()

        if (start <= 0L) {
            prefs.edit().putLong("tv_timer_start_ms", now).apply()
            sendBroadcast(Intent(ACTION_TIMER_RESET_DONE).putExtra(EXTRA_NEW_START_MS, now))
            return
        }

        val secs = (((now - start).coerceAtLeast(0L)) / 1000L).toInt()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))

        val body = mapOf(
            "npsn" to npsn,               // ★ now sent
            "sn_tv" to sn,
            "date" to nowStr,
            "app_name" to label,
            "app_title" to label,
            "app_duration" to secs,
            "tv_duration" to secs
        )

        Log.d(TAG, "Resetting timer → POST /tv-history ($label, $secs s)")
        api.postHistory(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                sendBroadcast(Intent(ACTION_HISTORY_POSTED))
                val newStart = System.currentTimeMillis()
                prefs.edit().putLong("tv_timer_start_ms", newStart).apply()
                sendBroadcast(Intent(ACTION_TIMER_RESET_DONE).putExtra(EXTRA_NEW_START_MS, newStart))
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e(TAG, "Reset POST failed", t)
                val newStart = System.currentTimeMillis()
                prefs.edit().putLong("tv_timer_start_ms", newStart).apply()
                sendBroadcast(Intent(ACTION_TIMER_RESET_DONE).putExtra(EXTRA_NEW_START_MS, newStart))
            }
        })
    }

    private fun postPendingUptimeIfAny() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean("pending_uptime", false)
        if (!pending) return

        val endMs = prefs.getLong("pending_uptime_end_ms", System.currentTimeMillis())
        val secs  = prefs.getInt("pending_uptime_secs", 0).coerceAtLeast(0)
        val sn    = prefs.getString("sn_tv", null) ?: run {
            prefs.edit().putBoolean("pending_uptime", false).apply()
            return
        }
        val npsn  = prefs.getString("npsn", null) ?: run {
            prefs.edit().putBoolean("pending_uptime", false).apply()
            return
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(endMs))
        val body = mapOf(
            "npsn" to npsn,             // ★ now sent
            "sn_tv" to sn,
            "date" to dateStr,
            "app_name" to "PowerOff",
            "app_title" to "PowerOff",
            "app_duration" to secs,
            "tv_duration" to secs
        )

        Log.d(TAG, "Posting pending uptime from shutdown ($secs s)")
        api.postHistory(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                prefs.edit()
                    .putBoolean("pending_uptime", false)
                    .remove("pending_uptime_end_ms")
                    .remove("pending_uptime_secs")
                    .apply()

                val newStart = System.currentTimeMillis()
                prefs.edit().putLong("tv_timer_start_ms", newStart).apply()
                sendBroadcast(Intent(ACTION_HISTORY_POSTED))
                sendBroadcast(Intent(ACTION_TIMER_RESET_DONE).putExtra(EXTRA_NEW_START_MS, newStart))
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e(TAG, "Posting pending uptime failed", t)
                val newStart = System.currentTimeMillis()
                prefs.edit()
                    .putBoolean("pending_uptime", false)
                    .remove("pending_uptime_end_ms")
                    .remove("pending_uptime_secs")
                    .putLong("tv_timer_start_ms", newStart)
                    .apply()
                sendBroadcast(Intent(ACTION_TIMER_RESET_DONE).putExtra(EXTRA_NEW_START_MS, newStart))
            }
        })
    }
}
