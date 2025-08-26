package com.laila.terastv

import com.laila.terastv.network.TvHistoryDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Generic “status” wrapper for simple ok/error responses
data class ApiStatus(
    val status: String,
    val message: String? = null
)

// History list wrapper: { status: "success", data: [...] }
data class TvHistoryResponse(
    val status: String,
    val data: List<TvHistoryDto> = emptyList()
)

interface LoggingApi {

    // Legacy endpoints left intact
    @POST("log_start.php")
    fun logStart(@Body body: Map<String, String>): Call<Void>

    @POST("log_end.php")
    fun logEnd(@Body body: Map<String, String>): Call<Void>

    // Main endpoints
    @POST("tv-history")
    fun postHistory(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Call<ApiStatus>

    @GET("tv-history")
    fun getUsageHistory(@Query("sn_tv") serial: String): Call<TvHistoryResponse>

    @GET("check-registration")
    fun checkRegistration(
        @Query("serial_number_tv") serialNumber: String
    ): Call<CheckResponse>
}
