package com.laila.terastv

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/api/") // 🔁 Replace with your actual backend URL
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: LoggingApi = retrofit.create(LoggingApi::class.java)
}