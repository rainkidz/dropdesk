package com.tubenime.app.ui.components

import android.app.Activity
import android.content.Intent
import com.tubenime.app.R

/**
 * Impact Cut antar-layar ala Shonen: potongan keras + punch overshoot,
 * sadar arah urutan tab (Home 0 / Browser 1 / Downloads 2 / Settings 3).
 * Maju (index naik) = hantaman dari kanan; mundur = dari kiri.
 * Destinasi non-tab (Premium/Cookie/Queue-detail) = netral maju.
 */
object NavAnim {
    const val TAB_HOME = 0
    const val TAB_BROWSER = 1
    const val TAB_DOWNLOADS = 2
    const val TAB_SETTINGS = 3

    /** Tab Queue berbagi slot Downloads. */
    const val TAB_QUEUE = TAB_DOWNLOADS

    /** Destinasi non-tab: animasi netral (rasa push maju). */
    const val TAB_NONE = -1

    fun go(activity: Activity, intent: Intent, from: Int, to: Int) {
        activity.startActivity(intent)
        val forward = to < 0 || to >= from
        activity.overridePendingTransition(
            if (forward) R.anim.punch_in_from_right else R.anim.punch_in_from_left,
            if (forward) R.anim.punch_out_to_left else R.anim.punch_out_to_right,
        )
    }
}
