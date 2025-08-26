package com.laila.terastv.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Matches /api/tv-history response items.
 * `date` may come as a string or an object, so we accept JsonElement.
 */
data class TvHistoryDto(
    @SerializedName("sn_tv") val snTv: String,
    @SerializedName("date") val date: JsonElement?,      // tolerant to string/object
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("app_name") val appName: String,
    @SerializedName("app_url") val appUrl: String,
    @SerializedName("app_duration") val appDuration: Int?,   // seconds
    @SerializedName("tv_duration")  val tvDuration: Int?     // seconds
)
