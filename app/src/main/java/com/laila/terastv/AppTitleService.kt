package com.laila.terastv.title

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that tries to infer a "title" from the current app's
 * active window hierarchy. If we can’t find anything meaningful, callers should
 * fall back to the app label.
 */
class AppTitleService : AccessibilityService() {

    companion object {
        private const val TAG = "AppTitleService"

        // Debounce to let UI settle after window/content changes
        private const val DEBOUNCE_MS = 300L

        // To avoid log spam: dump at most once every 20s per package
        private const val DUMP_COOLDOWN_MS = 20_000L

        // Turn on to see the full tree once per cooldown
        private const val ENABLE_DEBUG_DUMP = true
    }

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var lastPkg: String? = null

    // track last dump time per package (reduce log spam)
    private val lastDumpAt = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "connected")
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        lastPkg = pkg

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        pending?.let { main.removeCallbacks(it) }
        pending = Runnable { extractAndSaveTitle(pkg) }
        main.postDelayed(pending!!, DEBOUNCE_MS)
    }

    private fun extractAndSaveTitle(pkg: String) {
        val root = rootInActiveWindow ?: run {
            TitleProvider.get(this).update(pkg, null) // keep app-label fallback
            return
        }

        // Optional one-shot dump for debugging (YT/Netflix often hide text)
        maybeDumpTreeOnce(pkg, root)

        var best: String? = null
        var bestScore = 0.0

        fun consider(raw: CharSequence?) {
            if (raw.isNullOrBlank()) return
            val s = raw.toString().trim()
            if (s.equals(pkg, ignoreCase = true)) return
            if (s.length < 3) return

            val lower = s.lowercase()
            // obvious junk
            if (lower in setOf("home", "search", "settings", "ok", "cancel", "back", "more")) return
            if (s.startsWith("com.") && !s.contains(' ')) return // package-ish

            // scoring heuristics
            var score = Math.pow(s.length.toDouble(), 1.10)
            if (s.contains("•") || s.contains(" - ") || s.contains(": ") || s.contains("|")) score += 12
            if (s.count { it.isWhitespace() } >= 2) score += 6
            if (s.any { it.isLowerCase() } && s.any { it.isUpperCase() }) score += 3
            if (s.length >= 24) score += 3

            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                // Main fields
                consider(node.text)
                consider(node.contentDescription)

                // Some apps place meaningful strings in viewIdResourceName
                node.viewIdResourceName?.let { id ->
                    // id itself isn’t a title, but sometimes it carries the text for simple UIs
                    // so we only use it as a weak hint if it *contains* words like "title"
                    val idLower = id.lowercase()
                    if (idLower.contains("title") || idLower.contains("headline")) {
                        // give a tiny nudge—actual text should come from text/desc nearby
                        // no direct "consider(id)" here to avoid garbage, but leave hook:
                        // consider(id)
                    }
                }

                // Class-based hints (very weak)
                node.className?.let { className ->
                    val c = className.toString().lowercase()
                    if (c.contains("text") || c.contains("title")) {
                        consider(node.text)
                        consider(node.contentDescription)
                    }
                }

                // YouTube/Browser-ish quick hooks:
                // Titles often live in specific nodes near player controls—walk all children.
                for (i in 0 until node.childCount) {
                    walk(node.getChild(i))
                }
            } catch (_: Throwable) {
                // ignore bad nodes
            }
        }

        walk(root)

        TitleProvider.get(this).update(pkg, best)
        Log.d(TAG, "pkg=$pkg  title=${best ?: "(fallback)"}")
    }

    private fun maybeDumpTreeOnce(pkg: String, root: AccessibilityNodeInfo) {
        if (!ENABLE_DEBUG_DUMP) return
        val now = System.currentTimeMillis()
        val last = lastDumpAt[pkg] ?: 0L
        if (now - last < DUMP_COOLDOWN_MS) return
        lastDumpAt[pkg] = now

        fun esc(cs: CharSequence?): String =
            cs?.toString()?.replace("\n", " ")?.trim()?.take(140) ?: ""

        fun dump(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            val indent = " ".repeat((depth.coerceAtMost(8)) * 2)
            try {
                Log.d(
                    "AppTitleDump",
                    "$indent id=${node.viewIdResourceName} class=${node.className} " +
                            "txt='${esc(node.text)}' desc='${esc(node.contentDescription)}'"
                )
                val count = node.childCount
                for (i in 0 until count) dump(node.getChild(i), depth + 1)
            } catch (_: Throwable) {
                // ignore
            }
        }

        Log.d("AppTitleDump", "===== DUMP for $pkg =====")
        dump(root, 0)
        Log.d("AppTitleDump", "===== END DUMP =====")
    }
}
