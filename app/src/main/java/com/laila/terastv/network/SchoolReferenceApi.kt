package com.laila.terastv.network

import okhttp3.*
import java.io.IOException

object SchoolReferenceApi {
    private val client = OkHttpClient()

    fun lookupSchool(npsn: String, onResult: (Boolean, String?) -> Unit) {
        val url = "http://10.0.2.2:8000/api/school-reference?npsn=${npsn.trim()}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("SchoolReferenceApi failed: ${e.message}")
                onResult(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                println("SchoolReferenceApi response: $body")

                if (response.isSuccessful && body != null) {
                    onResult(true, body)
                } else {
                    println("SchoolReferenceApi error code: ${response.code}")
                    onResult(false, null)
                }
            }
        })
    }
}