package com.snapsave.app

enum class Platform(val displayName: String, val color: Int) {
    YOUTUBE("YouTube", 0xFFFF0000.toInt()),
    TIKTOK("TikTok", 0xFFEE1D52.toInt()),
    FACEBOOK("Facebook", 0xFF1877F2.toInt()),
    INSTAGRAM("Instagram", 0xFFE4405F.toInt()),
    THREADS("Threads", 0xFF000000.toInt()),
    UNKNOWN("Unknown", 0xFF757575.toInt());

    companion object {
        fun detect(url: String): Platform {
            val lower = url.lowercase()
            return when {
                lower.contains("youtube.com") || lower.contains("youtu.be") -> YOUTUBE
                lower.contains("tiktok.com") || lower.contains("vm.tiktok.com") -> TIKTOK
                lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> FACEBOOK
                lower.contains("instagram.com") -> INSTAGRAM
                lower.contains("threads.net") || lower.contains("threads.com") -> THREADS
                else -> UNKNOWN
            }
        }

        fun extractVideoId(url: String): String? {
            val lower = url.lowercase()
            return when {
                // YouTube
                lower.contains("youtu.be") -> {
                    url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").ifEmpty { null }
                }
                lower.contains("youtube.com") -> {
                    val patterns = listOf("v=", "vi=", "embed/", "shorts/")
                    for (pattern in patterns) {
                        if (url.contains(pattern)) {
                            return url.substringAfter(pattern).substringBefore("?").substringBefore("&").ifEmpty { null }
                        }
                    }
                    null
                }
                // TikTok
                lower.contains("tiktok.com") -> {
                    val match = Regex("/video/(\\d+)").find(url)
                    match?.groupValues?.get(1)
                }
                else -> null
            }
        }
    }
}
