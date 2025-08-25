package com.laila.terastv.ui.media

import android.app.Notification
import android.app.Service
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNowPlayingListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NowPlayingListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { sbn -> handle(sbn) }
        } catch (_: Exception) { /* ignore */ }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val pkg = sbn.packageName ?: return

        // We only care about transport/media style notifications
        if (n.category != Notification.CATEGORY_TRANSPORT && n.extras == null) return

        val extras = n.extras
        var title: String? = extras?.getString(Notification.EXTRA_TITLE)
        var artUri: String? = null
        var mediaUri: String? = null
        var largeIcon: Bitmap? = null

        // Large icon directly on the notification
        largeIcon = (extras?.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
            ?: n.getLargeIcon()?.loadDrawable(this)?.let { null } // we only send bitmap if provided in extras

        // Try to get a MediaSession and read richer metadata
        val token = extras?.getParcelable("android.mediaSession") as? MediaSession.Token
        if (token != null) {
            try {
                val controller = MediaController(this, token)
                val md = controller.metadata
                if (md != null) {
                    title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                        ?: md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: title

                    artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                        ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI) ?: artUri

                    // Rarely exposed; many apps won’t provide this.
                    mediaUri = if (Build.VERSION.SDK_INT >= 21) {
                        md.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
                    } else null

                    // Some apps put artwork as bitmap only; keep ours if we already have one.
                    if (largeIcon == null) {
                        largeIcon = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaController read failed for $pkg", e)
            }
        }

        NowPlayingCache.put(
            pkg = pkg,
            title = title,
            mediaUri = mediaUri,
            artUri = artUri,
            largeIconBitmap = largeIcon
        )
        Log.d(TAG, "now playing [$pkg] title=$title, artUri=$artUri, mediaUri=$mediaUri, bmp=${largeIcon != null}")
    }
}
