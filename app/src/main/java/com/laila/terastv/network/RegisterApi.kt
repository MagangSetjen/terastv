package com.laila.terastv.network

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object RegisterApi {
    private val client = OkHttpClient()

    fun registerSchoolOnce(
        context: Context,
        npsn: String,
        schoolName: String,
        snTv: String,
        onResult: (Boolean, String) -> Unit
    ) {
        Log.d("RegisterApi", "Attempting backend registration")

        val json = """
            {
                "npsn": "$npsn",
                "nama_sekolah": "$schoolName",
                "serial_number_tv": "$snTv"
            }
        """.trimIndent()

        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://10.0.2.2:8000/api/school-registration")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    val prefs = context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_registered", true).apply()
                    onResult(true, "Registrasi berhasil")
                } else {
                    onResult(false, "Gagal: $body")
                }
            }
        })
    }
}