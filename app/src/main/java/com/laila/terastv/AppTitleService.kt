package com.laila.terastv.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Listens for UI changes and stores the most relevant on-screen title/tagline
 * into SharedPreferences so ForegroundAppService can attach it to /tv-history.
 */
class AppTitleService : AccessibilityService() {

    companion object {
        private const val TAG = "AppTitleService"

        // SharedPreferences keys — ForegroundAppService reads these
        private const val PREFS = "tv_prefs"
        private const val KEY_LAST_APP_TITLE = "last_app_title"
        private const val KEY_LAST_APP_TITLE_PKG = "last_app_title_pkg"
        private const val KEY_LAST_APP_TITLE_TIME = "last_app_title_time"

        // Debounce per package (ms) to avoid spamming updates
        private const val TITLE_UPDATE_DEBOUNCE_MS = 500L
    }

    private val lastUpdateTimeByPkg = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Defensive: ensure correct config even if XML is present
        serviceInfo = serviceInfo?.let { info ->
            info.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100
                flags = (AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS)
            }
        }
        Log.d(TAG, "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = (event.packageName ?: "").toString()
        if (pkg.isEmpty() || pkg == packageName) return

        // Simple per-pkg debounce
        val now = System.currentTimeMillis()
        val last = lastUpdateTimeByPkg[pkg] ?: 0L
        if (now - last < TITLE_UPDATE_DEBOUNCE_MS) return

        val source: AccessibilityNodeInfo = event.source ?: return

        // Collect visible text snippets from the view tree
        val candidates = mutableListOf<String>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) {
                node.text?.toString()?.let { candidates += it }
                node.contentDescription?.toString()?.let { candidates += it }
                if (Build.VERSION.SDK_INT >= 26) {
                    node.hintText?.toString()?.let { candidates += it }
                }
            }
            for (i in 0 until node.childCount) collect(node.getChild(i))
        }
        collect(source)

        val best = pickBestTitle(candidates)
        if (best != null) {
            // Persist for ForegroundAppService
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_APP_TITLE, best)
                .putString(KEY_LAST_APP_TITLE_PKG, pkg)
                .putLong(KEY_LAST_APP_TITLE_TIME, now)
                .apply()

            lastUpdateTimeByPkg[pkg] = now
            Log.d(TAG, "Captured title for $pkg → \"$best\"")
        }
    }

    override fun onInterrupt() { /* no-op */ }

    /**
     * Heuristic for choosing a good "title":
     * - prefer medium-length lines
     * - avoid all-caps noise or very short tokens
     * - pick the longest reasonable candidate
     */
    private fun pickBestTitle(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null

        val cleaned = candidates
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { it.length in 6..140 }               // reasonable screen text
            .filter { it.any { ch -> ch.isLetter() } }    // has letters
            .sortedByDescending { it.length }             // longer ≈ more descriptive

        return cleaned.firstOrNull()
    }
}
