package com.tubenime.app.ui.screens.home.components

import com.tubenime.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * Satu-satunya sumber kebenaran untuk konten anime di Home.
 *
 * Konsep (sesuai arahan pemilik):
 * - PLATFORM MATRIX = pintu KONTEN TRENDING/POPULER anime per platform
 *   (hashtag/tag/ranking/explore anime — BUKAN channel tertentu).
 * - QUICK CHANNELS = saran ACAK lintas platform (YouTube, TikTok, Instagram,
 *   ...) yang berganti tiap hari + spotlight "Today:" yang dipertahankan.
 *
 * Semua URL diverifikasi HTTP 200 pada 22 Sep 2026, kecuali Facebook
 * (bot-wall via curl, tapi pola URL valid di WebView sungguhan).
 */
object AnimeChannels {

    // ── Trending anime per platform (tile matrix) ───────────────

    /** Feed trending/populer anime per nama tile ("YouTube", "TikTok", ...). */
    fun platformTrendingUrl(name: String): String? = when (name) {
        "YouTube" -> "https://m.youtube.com/hashtag/anime"
        "TikTok" -> "https://www.tiktok.com/tag/anime"
        "Instagram" -> "https://www.instagram.com/explore/tags/anime/"
        "Bilibili" -> "https://www.bilibili.com/v/popular/rank/bangumi"
        "Facebook" -> "https://m.facebook.com/watch/search/?q=anime"
        "Threads" -> "https://www.threads.net/search?q=anime"
        else -> null
    }

    fun youtubeSearchUrl(query: String): String {
        val encoded = query.trim().split(Regex("\\s+")).joinToString("+")
        return "https://m.youtube.com/results?search_query=$encoded"
    }

    // ── Quick channels acak ─────────────────────────────────────
    //
    // Kolam saran dari berbagai platform; tiap hari diambil 2 secara acak
    // dengan seed tanggal (stabil sepanjang hari, berganti besoknya).
    // Acak per-komposisi sengaja DIHINDARI agar daftar tidak melompat-lompat
    // saat user scroll (seed = yyyyMMdd).

    data class RandomPick(
        val title: String,
        val platform: String,
        val url: String,
        val iconRes: Int = R.drawable.figma_stripbox_trending,
    )

    val randomPool: List<RandomPick> = listOf(
        RandomPick("AMV Terpopuler", "YOUTUBE", youtubeSearchUrl("amv anime terpopuler")),
        RandomPick("Anime Movie", "YOUTUBE", youtubeSearchUrl("anime movie sub indo")),
        RandomPick("#animetiktok", "TIKTOK", "https://www.tiktok.com/tag/animetiktok"),
        RandomPick("#cosplay", "TIKTOK", "https://www.tiktok.com/tag/cosplay"),
        RandomPick("@anime", "INSTAGRAM", "https://www.instagram.com/anime/", R.drawable.figma_stripbox_anime),
        RandomPick("Anime Memes", "INSTAGRAM", "https://www.instagram.com/explore/tags/animememes/", R.drawable.figma_stripbox_anime),
        RandomPick("Donghua Populer", "BILIBILI", "https://www.bilibili.com/v/popular/rank/guochuang", R.drawable.figma_stripbox_animezone),
        RandomPick("Anime OP/ED", "BILIBILI", "https://search.bilibili.com/all?keyword=anime+opening", R.drawable.figma_stripbox_anime),
        RandomPick("Watch Anime", "FACEBOOK", "https://m.facebook.com/watch/search/?q=anime"),
        RandomPick("Manga Talk", "THREADS", "https://www.threads.net/search?q=manga", R.drawable.figma_stripbox_anime),
    )

    /** Seed harian yyyyMMdd (mis. 20260922). */
    fun todaySeed(): Long =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()).toLong()

    /** 2 saran acak hari ini (seed tanggal — stabil sehari penuh). */
    fun dailyPicks(count: Int = 2, seed: Long = todaySeed()): List<RandomPick> {
        if (randomPool.isEmpty()) return emptyList()
        val shuffled = randomPool.shuffled(Random(seed))
        return shuffled.take(count.coerceAtMost(randomPool.size))
    }

    // ── Spotlight harian "Today:" (dipertahankan) ───────────────

    data class AnimeSpotlight(val title: String, val query: String)

    /** Satu spotlight per hari (Senin..Minggu, index Calendar - 1). */
    val weeklySpotlights: List<AnimeSpotlight> = listOf(
        AnimeSpotlight("One Piece", "one piece anime episode terbaru"),
        AnimeSpotlight("Jujutsu Kaisen", "jujutsu kaisen anime"),
        AnimeSpotlight("Frieren", "sousou no frieren anime"),
        AnimeSpotlight("Solo Leveling", "solo leveling anime"),
        AnimeSpotlight("Demon Slayer", "demon slayer anime"),
        AnimeSpotlight("Chainsaw Man", "chainsaw man anime"),
        AnimeSpotlight("Spy x Family", "spy x family anime"),
    )

    fun todaySpotlight(): AnimeSpotlight {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=Senin..7=Sabtu
        return weeklySpotlights[(day - 1) % weeklySpotlights.size]
    }

    fun spotlightUrl(spotlight: AnimeSpotlight): String =
        youtubeSearchUrl(spotlight.query)

    /** Pencarian anime trending (tujuan "VIEW ALL"). */
    const val ANIME_TRENDING_URL = "https://m.youtube.com/results?search_query=trending+anime+2026"

    /**
     * Tiga strip quick channels: 2 acak lintas platform + 1 spotlight harian.
     * Dipakai [defaultShortcuts] agar preview & Home selalu konsisten.
     */
    fun quickChannels(
        trendingIconRes: Int = R.drawable.figma_stripbox_trending,
    ): List<ShortcutItem> {
        val today = todaySpotlight()
        val picks = dailyPicks(2)
        return listOf(
            picks.getOrElse(0) {
                RandomPick("Anime Viral", "YOUTUBE", ANIME_TRENDING_URL, trendingIconRes)
            }.toShortcut(),
            picks.getOrElse(1) {
                RandomPick("Anime Populer", "TIKTOK", "https://www.tiktok.com/tag/anime", trendingIconRes)
            }.toShortcut(),
            ShortcutItem(
                "Today: ${today.title}",
                "DAILY SPOTLIGHT • TAP TO EXPLORE",
                trendingIconRes,
            ),
        )
    }

    private fun RandomPick.toShortcut(): ShortcutItem =
        ShortcutItem(title, "RANDOM PICK • $platform", iconRes)

    /**
     * Selesaikan judul strip menjadi URL browser. Kembalikan null bila judul
     * tidak dikenal (pemanggil memakai fallback-nya sendiri).
     */
    fun resolveShortcutUrl(title: String): String? {
        if (title.startsWith("Today: ")) {
            val name = title.removePrefix("Today: ").trim()
            return weeklySpotlights.firstOrNull { it.title.equals(name, ignoreCase = true) }
                ?.let { spotlightUrl(it) }
                ?: spotlightUrl(todaySpotlight())
        }
        randomPool.firstOrNull { it.title.equals(title, ignoreCase = true) }?.let {
            return it.url
        }
        // "VIEW ALL" & judul lama ("Trending Now", "Anime OP/ED",
        // "Bilibili Anime Zone") → fallback pemanggil (trending).
        return null
    }
}
