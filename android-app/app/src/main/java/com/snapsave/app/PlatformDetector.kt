package com.snapsave.app

/**
 * Detects platform from URL and extracts video IDs.
 * Platform enum is in Platform.kt
 */
object PlatformDetector {

    fun detect(url: String): Platform = Platform.detect(url)

    fun extractVideoId(url: String): String? = Platform.extractVideoId(url)
}
