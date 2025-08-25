package com.laila.terastv.ui

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import androidx.core.graphics.drawable.IconCompat
import java.io.ByteArrayOutputStream

class NowPlayingListener : NotificationListenerService() {

    private val prefs by lazy {
        getSharedPreferences("tv_prefs", MODE_PRIVATE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return

            // Media-style notifications usually have a media session / transport controls.
            val isMedia = n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            if (!isMedia) return

            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            if (title.isNullOrEmpty()) return

            // Prefer large image from the notification; fall back to app icon.
            val iconBase64 = tryGetLargeIconBase64(n) ?: run {
                val appIcon = packageManager.getApplicationIcon(sbn.packageName)
                drawableToBase64(appIcon)
            }

            // Store the most recent "now playing" snapshot
            prefs.edit()
                .putString("np_pkg", sbn.packageName)
                .putString("np_title", title)
                .putString("np_icon_b64", iconBase64)
                .putLong("np_timestamp", System.currentTimeMillis())
                .apply()

            Log.d("NowPlaying", "Saved: pkg=${sbn.packageName}, title=$title, icon=${iconBase64?.length}")
        } catch (t: Throwable) {
            Log.e("NowPlaying", "onNotificationPosted error", t)
        }
    }

    // --- helpers ---

    private fun tryGetLargeIconBase64(n: Notification): String? {
        // Large icon can live in extras or as modern Icon on API 23+
        val bmp: Bitmap? = when {
            Build.VERSION.SDK_INT >= 23 -> {
                try {
                    val icon = n.getLargeIcon()
                    if (icon != null) IconCompat.createFromIcon(this, icon).loadDrawable(this)?.let {
                        drawableToBitmap(it)
                    } else null
                } catch (_: Throwable) { null }
            }
            else -> null
        } ?: run {
            // Sometimes present in extras as a Bitmap
            (n.extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                ?: (n.largeIcon as? Bitmap)
        }

        return bmp?.let { bitmapToBase64(it) }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun drawableToBase64(drawable: Drawable): String {
        return bitmapToBase64(drawableToBitmap(drawable))
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
