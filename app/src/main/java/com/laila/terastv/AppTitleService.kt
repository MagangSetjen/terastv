package com.laila.terastv.title

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

class AppTitleService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var lastPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AppTitleService", "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        lastPkg = pkg

        // We care about both: state & content changes
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        // Debounce a little so content has time to settle
        pendingRunnable?.let { main.removeCallbacks(it) }
        pendingRunnable = Runnable { extractAndSaveTitle(pkg) }
        main.postDelayed(pendingRunnable!!, 300L)
    }

    private fun extractAndSaveTitle(pkg: String) {
        val root = rootInActiveWindow ?: run {
            TitleProvider.get(this).update(pkg, null) // keep at least the app label fallback
            return
        }

        // Traverse the tree and pick the best candidate string
        var best: String? = null
        var bestScore = 0.0

        fun consider(text: CharSequence?) {
            if (text.isNullOrBlank()) return
            val s = text.toString().trim()

            // Filter obviously-useless strings
            if (s.equals(pkg, ignoreCase = true)) return
            if (s.length < 4) return
            val lower = s.lowercase()
            if (lower in listOf("home","search","settings","more","ok","cancel","back")) return

            // Heuristics: prefer longer, and with separators that titles often contain
            var score = Math.pow(s.length.toDouble(), 1.15)
            if (s.contains("•") || s.contains(" - ") || s.contains(": ") || s.contains("|")) score += 20
            if (s.any { it.isLowerCase() } && s.any { it.isUpperCase() }) score += 5
            if (s.count { it.isWhitespace() } >= 2) score += 5

            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                consider(node.text)
                consider(node.contentDescription)
                for (i in 0 until node.childCount) {
                    walk(node.getChild(i))
                }
            } finally {
                // Avoid recycling here; framework manages node lifecycle for rootInActiveWindow traversal
            }
        }

        walk(root)

        // If nothing meaningful, fall back to app label
        TitleProvider.get(this).update(pkg, best)
        Log.d("AppTitleService", "pkg=$pkg  title=${best ?: "(fallback)"}")
    }

    override fun onInterrupt() { /* no-op */ }
}
