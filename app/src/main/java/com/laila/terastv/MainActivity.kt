package com.laila.terastv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.laila.terastv.ui.ForegroundAppService
import com.laila.terastv.ui.about.AboutScreen
import com.laila.terastv.ui.dashboard.DashboardScreen
import com.laila.terastv.ui.registration.RegistrationScreen
import com.laila.terastv.ui.splash.SplashScreen
import com.laila.terastv.ui.theme.TerasTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
        val savedSerial = prefs.getString("sn_tv", "") ?: ""
        Log.d(TAG, "Loaded serial from prefs: '$savedSerial'")

        // (opsional) Inisialisasi start time pertama kali
        if (prefs.getLong("tv_timer_start_ms", 0L) == 0L) {
            prefs.edit().putLong("tv_timer_start_ms", System.currentTimeMillis()).apply()
            Log.d(TAG, "Initialized tv_timer_start_ms")
        }

        // Flush pending uptime kalau ada (tidak blok UI)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                postPendingUptimeIfAny(prefs)
            } catch (t: Throwable) {
                Log.e(TAG, "postPendingUptimeIfAny failed", t)
            }
        }

        // Tentukan tujuan setelah splash (pakai prefs lokal)
        val initialRoute = if (savedSerial.isNotEmpty()) {
            fallbackDashboard(prefs, savedSerial)
        } else {
            "registration"
        }

        // Boot UI
        setNavHost(initialRoute)

        // Start service pemantauan
        startUsageService()

        // Best-effort cek backend (jangan blok UI)
        if (savedSerial.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val resp = RetrofitClient.api.checkRegistration(savedSerial).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body()
                        if (body != null && body.registered == true && body.data != null) {
                            val data = body.data
                            prefs.edit()
                                .putString("npsn", data.NPSN)
                                .putString("school_name", data.school_name)
                                .putString("sn_tv", data.sn_tv)
                                .apply()
                            Log.d(TAG, "Backend confirmed: ${data.school_name}")
                        } else {
                            Log.w(TAG, "Backend not confirmed; keeping local session")
                        }
                    } else {
                        Log.w(TAG, "checkRegistration HTTP ${resp.code()}; keeping local session")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "checkRegistration failed; keeping local session", e)
                }
            }
        }
    }

    private fun startUsageService() {
        try {
            val intent = Intent(this, ForegroundAppService::class.java)
            ContextCompat.startForegroundService(this, intent)
            Log.d(TAG, "Requested startForegroundService(ForegroundAppService)")
            Toast.makeText(this, "Starting usage tracking service…", Toast.LENGTH_SHORT).show()

            val usageEnabled = try {
                Settings.Secure.getInt(contentResolver, "usage_stats_enabled", 0) == 1
            } catch (_: Exception) {
                false
            }
            if (!usageEnabled) {
                Toast.makeText(
                    this,
                    "Please grant Usage Access for TerasTV to track app usage.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start ForegroundAppService", t)
            Toast.makeText(this, "Failed to start service: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setNavHost(initialRoute: String) {
        setContent {
            TerasTVTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    // ✅ tampilkan Splash beneran, lalu pindah setelah delay via onFinished
                    composable("splash") {
                        // Tidak ada LaunchedEffect navigate di sini!
                        SplashScreen(
                            enableTimer = true,              // Splash akan delay sesuai durationMillis di SplashScreen
                            onFinished = {
                                if (initialRoute.startsWith("dashboard")) {
                                    navController.navigate(initialRoute) {
                                        launchSingleTop = true
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("registration") {
                                        launchSingleTop = true
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable("registration") {
                        RegistrationScreen(navController = navController)
                    }

                    composable(
                        route = "dashboard?npsn={npsn}&nama_sekolah={nama_sekolah}&serial={serial}",
                        arguments = listOf(
                            navArgument("npsn") { type = NavType.StringType; defaultValue = "" },
                            navArgument("nama_sekolah") {
                                type = NavType.StringType; defaultValue = ""
                            },
                            navArgument("serial") { type = NavType.StringType; defaultValue = "" },
                        )
                    ) { backStackEntry ->
                        val npsn = backStackEntry.arguments?.getString("npsn") ?: ""
                        val namaSekolah = URLDecoder.decode(
                            backStackEntry.arguments?.getString("nama_sekolah") ?: "",
                            "UTF-8"
                        )
                        val serial = backStackEntry.arguments?.getString("serial") ?: ""

                        Log.d(
                            TAG,
                            "Dashboard args → npsn=$npsn, sekolah=$namaSekolah, serial=$serial"
                        )

                        DashboardScreen(
                            npsn = npsn,
                            namaSekolah = namaSekolah,
                            serial = serial,
                            onAboutClick = {
                                navController.navigate("about")
                            }
                        )
                    }
                    composable("about") {
                        AboutScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun fallbackDashboard(
        prefs: android.content.SharedPreferences,
        serial: String
    ): String {
        val npsn = prefs.getString("npsn", "") ?: ""
        val school = prefs.getString("school_name", "") ?: ""
        val encodedSchool = URLEncoder.encode(school, "UTF-8")
        return "dashboard?npsn=$npsn&nama_sekolah=$encodedSchool&serial=$serial"
    }

    private fun postPendingUptimeIfAny(prefs: android.content.SharedPreferences) {
        val hasPending = prefs.getBoolean("pending_uptime", false)
        if (!hasPending) return

        val sn = prefs.getString("sn_tv", null) ?: return
        val endMs = prefs.getLong("pending_uptime_end_ms", System.currentTimeMillis())
        val secs = prefs.getInt("pending_uptime_secs", 0)

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(endMs))
        val body = mapOf(
            "sn_tv" to sn,
            "date" to dateStr,
            "app_name" to "[TV UPTIME]",
            "app_url" to "device://shutdown",
            "thumbnail" to "",
            "app_duration" to secs,
            "tv_duration" to secs
        )

        try {
            val resp = RetrofitClient.api.postHistory(body).execute()
            Log.d(TAG, "POST uptime → ${resp.code()}")
        } catch (t: Throwable) {
            Log.e(TAG, "POST uptime failed", t)
        } finally {
            prefs.edit()
                .putBoolean("pending_uptime", false)
                .remove("pending_uptime_end_ms")
                .remove("pending_uptime_secs")
                .apply()
        }
    }
}
