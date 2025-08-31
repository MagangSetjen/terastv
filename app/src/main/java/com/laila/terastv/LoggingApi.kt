package com.laila.terastv

import com.laila.terastv.network.TvHistoryDto
import okhttp3.ResponseBody
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

    // postHistory now returns raw body (server sometimes returns no/short JSON)
    @POST("tv-history")
    fun postHistory(@Body body: Map<String, @JvmSuppressWildcards Any?>): Call<ResponseBody>

    // ✅ include npsn + sn_tv and optional date range
    @GET("tv-history")
    fun getUsageHistory(
        @Query("npsn") npsn: String,
        @Query("sn_tv") serial: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null
    ): Call<TvHistoryResponse>

    @GET("check-registration")
    fun checkRegistration(@Query("serial_number_tv") serialNumber: String): Call<CheckResponse>
}
