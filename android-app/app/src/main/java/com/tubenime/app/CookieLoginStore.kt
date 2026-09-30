package com.tubenime.app

import android.content.Context
import java.io.File

/**
 * Penyimpanan cookie login per-platform (dipindah dari companion
 * CookieLoginActivity): file cookies.txt format Netscape per platform,
 * dipakai yt-dlp via [InspectEngine] dan [DownloadManager].
 */
object CookieLoginStore {

    const val PLATFORM_FACEBOOK = "facebook"
    const val PLATFORM_INSTAGRAM = "instagram"
    const val PLATFORM_THREADS = "threads"

    /** Desain Settings 17:1981/17:2001: YouTube + TikTok juga bisa login cookie. */
    const val PLATFORM_YOUTUBE = "youtube"
    const val PLATFORM_TIKTOK = "tiktok"

    const val EXTRA_PLATFORM = "platform"
    const val EXTRA_LOGIN_URL = "login_url"

    fun getCookiesFile(context: Context, platform: String): File {
        return File(context.filesDir, "cookies_${platform}.txt")
    }

    fun hasCookies(context: Context, platform: String): Boolean {
        val file = getCookiesFile(context, platform)
        return file.exists() && file.length() > 10
    }

    /** URL login default per platform (untuk host Compose Cookie Login). */
    fun defaultLoginUrl(platform: String): String = when (platform) {
        PLATFORM_FACEBOOK -> "https://www.facebook.com/"
        PLATFORM_INSTAGRAM -> "https://www.instagram.com/accounts/login/"
        PLATFORM_THREADS -> "https://www.threads.net/login"
        PLATFORM_YOUTUBE -> "https://www.youtube.com/"
        PLATFORM_TIKTOK -> "https://www.tiktok.com/login/"
        else -> "https://www.google.com"
    }

    /** Domain untuk capture cookie via CookieManager. */
    fun cookieDomain(platform: String): String = when (platform) {
        PLATFORM_FACEBOOK -> "facebook.com"
        PLATFORM_INSTAGRAM -> "instagram.com"
        PLATFORM_THREADS -> "threads.net"
        PLATFORM_YOUTUBE -> "youtube.com"
        PLATFORM_TIKTOK -> "tiktok.com"
        else -> ""
    }
}
