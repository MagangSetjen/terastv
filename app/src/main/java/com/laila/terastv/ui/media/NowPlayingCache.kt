package com.laila.terastv.ui.media

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

data class NowPlayingMeta(
    val packageName: String,
    val title: String? = null,
    val mediaUri: String? = null,     // only if the app exposes it (rare)
    val artUri: String? = null,       // "content://" or "http(s)://"
    val artDataUrl: String? = null,   // data:image/png;base64,… if only a Bitmap was available
    val updatedAt: Long = System.currentTimeMillis()
)

/** Thread-safe cache of last seen media metadata per package. */
object NowPlayingCache {
    private val map = ConcurrentHashMap<String, NowPlayingMeta>()

    fun put(
        pkg: String,
        title: String?,
        mediaUri: String?,
        artUri: String?,
        largeIconBitmap: Bitmap?
    ) {
        val dataUrl = largeIconBitmap?.let { bmp ->
            runCatching {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                "data:image/png;base64,$b64"
            }.getOrNull()
        }
        map[pkg] = NowPlayingMeta(
            packageName = pkg,
            title = title,
            mediaUri = mediaUri,
            artUri = artUri,
            artDataUrl = dataUrl,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun get(pkg: String): NowPlayingMeta? = map[pkg]
}
