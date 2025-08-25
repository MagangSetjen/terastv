package com.laila.terastv

import android.content.Context
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// ✅ SharedPreferences helpers
// Only track registration locally after confirmed backend success
fun isAlreadyRegistered(context: Context): Boolean {
    // This now just tells you what the *last backend-confirmed* status was
    val prefs = context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_registered", false)
}

fun markAsRegistered(context: Context, npsn: String, schoolName: String, snTv: String) {
    // Store full registration details after backend success
    val prefs = context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("is_registered", true)
        .putString("npsn", npsn)
        .putString("school_name", schoolName)
        .putString("sn_tv", snTv)
        .apply()
}

fun clearRegistration(context: Context) {
    // Call this if backend says not registered
    val prefs = context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
}

// ✅ Retrofit interface for backend check
interface RegistrationApi {
    @GET("check-registration")
    fun checkRegistration(@Query("sn_tv") snTv: String): Call<CheckResponse>
}

// ✅ Response models
data class CheckResponse(
    val registered: Boolean,
    val data: SchoolData? = null
)

data class SchoolData(
    val NPSN: String,
    val school_name: String,
    val sn_tv: String
)