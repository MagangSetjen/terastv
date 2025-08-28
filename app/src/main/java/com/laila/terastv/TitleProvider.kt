package com.laila.terastv.title

import android.content.Context
import android.content.pm.PackageManager

class TitleProvider private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)

    fun lastTitle(): String? = prefs.getString("last_title", null)

    fun update(pkg: String, candidate: String?) {
        val title = candidate?.takeIf { it.isNotBlank() } ?: appLabel(pkg)
        prefs.edit().putString("last_title", title).apply()
    }

    private fun appLabel(pkg: String): String {
        return try {
            val pm: PackageManager = ctx.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai)?.toString() ?: pkg
        } catch (_: Exception) {
            pkg
        }
    }

    companion object {
        @Volatile private var INSTANCE: TitleProvider? = null
        fun get(ctx: Context): TitleProvider =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TitleProvider(ctx.applicationContext).also { INSTANCE = it }
            }
    }
}
