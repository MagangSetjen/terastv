package com.laila.terastv

import com.laila.terastv.network.TvHistoryDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class ApiStatus(val status: String, val message: String? = null)

data class TvHistoryResponse(val status: String, val data: List<TvHistoryDto> = emptyList())

interface LoggingApi {
    @POST("log_start.php") fun logStart(@Body body: Map<String, String>): Call<Void>
    @POST("log_end.php")   fun logEnd(@Body body: Map<String, String>): Call<Void>

    @POST("tv-history")
    fun postHistory(@Body body: Map<String, @JvmSuppressWildcards Any?>): Call<ApiStatus>

    @GET("tv-history")
    fun getUsageHistory(@Query("sn_tv") serial: String): Call<TvHistoryResponse>

    @GET("check-registration")
    fun checkRegistration(@Query("serial_number_tv") serialNumber: String): Call<CheckResponse>
}
