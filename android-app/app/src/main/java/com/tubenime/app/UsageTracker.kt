package com.tubenime.app

import android.content.Context

/**
 * Tracks which platforms produced successful downloads, bucketed by week,
 * so product decisions (how deep to go on anime content vs. staying a
 * neutral downloader) can be driven by real usage.
 *
 * Counts live only on-device in SharedPreferences — nothing leaves the phone.
 * Unknown platforms (direct CDN/media links with no recognizable host) are
 * ignored because they carry no signal.
 */
object UsageTracker {

    private const val PREFS = "usage_prefs"
    private const val KEY_WEEK = "week"
    private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L // epoch-aligned 7-day bucket

    private fun keyFor(platform: Platform) = "count_${platform.name}"

    /** Records one successful download from [platform]. Safe from any thread. */
    fun record(context: Context, platform: Platform) {
        if (platform == Platform.UNKNOWN) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val week = (now / WEEK_MS).toInt()

        if (prefs.getInt(KEY_WEEK, 0) != week) {
            // New week bucket — discard last week's counts
            prefs.edit()
                .clear()
                .putInt(KEY_WEEK, week)
                .putInt(keyFor(platform), 1)
                .apply()
        } else {
            prefs.edit()
                .putInt(keyFor(platform), prefs.getInt(keyFor(platform), 0) + 1)
                .apply()
        }
    }

    /** Per-platform download counts for the current week. Empty when none recorded yet. */
    fun weeklyMix(context: Context): Map<Platform, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val week = (System.currentTimeMillis() / WEEK_MS).toInt()
        if (prefs.getInt(KEY_WEEK, 0) != week) return emptyMap()

        val result = linkedMapOf<Platform, Int>()
        for (platform in Platform.values()) {
            if (platform == Platform.UNKNOWN) continue
            val count = prefs.getInt(keyFor(platform), 0)
            if (count > 0) result[platform] = count
        }
        return result
    }
}
