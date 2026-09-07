package com.tubenime.app

/**
 * Pure (Android-free) rules describing what free vs premium users may download.
 * Kept in a plain Kotlin object so it can be unit-tested on the JVM.
 */
object TierRules {

    /** Free users may not download video streams taller than this. */
    const val FREE_MAX_VIDEO_HEIGHT = 720

    /** Free users may not download audio streams with a higher bitrate than this (kbps). */
    const val FREE_MAX_AUDIO_BITRATE = 128

    /** Maximum parallel downloads granted to premium users (free users are capped at 1). */
    const val PREMIUM_MAX_CONCURRENT = 3

    fun isVideoAllowed(premium: Boolean, height: Int): Boolean {
        // height <= 0 means "unknown" — allow it for both tiers rather than
        // accidentally hiding every format when metadata is missing.
        if (height <= 0) return true
        return premium || height <= FREE_MAX_VIDEO_HEIGHT
    }

    fun isAudioAllowed(premium: Boolean, bitrate: Int): Boolean {
        // bitrate <= 0 means "unknown" — allow it.
        if (bitrate <= 0) return true
        return premium || bitrate <= FREE_MAX_AUDIO_BITRATE
    }

    /** Filters a list of format choices down to what the current tier may see. */
    fun filterFormats(premium: Boolean, formats: List<FormatChoice>): List<FormatChoice> {
        return formats.filter { choice ->
            when (choice.type) {
                "audio" -> isAudioAllowed(premium, choice.bitrate)
                else -> isVideoAllowed(premium, choice.height)
            }
        }
    }
}
