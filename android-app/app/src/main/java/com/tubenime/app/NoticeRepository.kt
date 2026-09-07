package com.tubenime.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provides the home-screen notice carousel content.
 *
 * Defaults are hard-coded (also present in strings.xml for first render).
 * A JSON list fetched from [NOTICES_URL] (cached to internal storage) can
 * override the defaults so notifications can be updated without an app release.
 *
 * Expected remote JSON (root array or { "notices": [...] }):
 * [
 *   { "icon": "🌸", "title": "New season is here", "body": "...", "url": "https://..." }
 * ]
 * - "url" is optional; when present the slide opens the in-app browser on tap.
 * - When a list is fetched but empty/invalid, defaults are kept.
 */
object NoticeRepository {

    private const val PREFS = "notice_prefs"
    private const val KEY_CACHE = "cached_notices_json"
    private const val KEY_LAST_FETCH = "last_fetch_ms"
    private const val NOTICES_URL = "https://tubenime.example.com/notices.json"
    private const val FETCH_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000

    data class Notice(
        val icon: String,
        val title: String,
        val body: String,
        val url: String? = null
    )

    /** Hard-coded fallback notices. Indices 3 and 4 mirror the legacy tappable slides. */
    fun defaults(): List<Notice> = listOf(
        Notice(
            icon = "📺",
            title = "Now supported",
            body = "YouTube, TikTok, Instagram, Bilibili & more — paste any link to start."
        ),
        Notice(
            icon = "🌸",
            title = "New season is here 🌸",
            body = "Fresh seasonal anime drops weekly — grab the latest openings before spoilers!"
        ),
        Notice(
            icon = "🎧",
            title = "Save the soundtrack",
            body = "Turn anime OP/ED into MP3s — tap Audio Only and listen offline."
        ),
        Notice(
            icon = "🔥",
            title = "Trending openings",
            body = "See what openings everyone is watching — tap to browse anime openings on YouTube.",
            url = "https://m.youtube.com/results?search_query=anime+opening+ending"
        ),
        Notice(
            icon = "🏮",
            title = "Bilibili anime zone",
            body = "Thousands of anime episodes and fan MVs live here — tap to explore.",
            url = "https://www.bilibili.com/anime"
        ),
        Notice(
            icon = "📋",
            title = "Pro tip",
            body = "Copy an anime video link anywhere — TubeNime catches it automatically."
        )
    )

    /**
     * Returns the notices to display: cached remote list if present, else defaults.
     * Safe to call from the main thread (no I/O here).
     */
    fun load(context: Context): List<Notice> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHE, null) ?: return defaults()
        return runCatching { parse(cached) }.getOrDefault(defaults())
    }

    /**
     * Fetches fresh notices in the background (throttled to once per 12h) and
     * updates the cache. Returns the list that should now be displayed, or
     * null when nothing changed / fetch failed.
     */
    suspend fun refresh(context: Context, force: Boolean = false): List<Notice>? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_LAST_FETCH, 0L)
            if (!force && System.currentTimeMillis() - last < FETCH_INTERVAL_MS) {
                return@withContext null
            }
            val json = runCatching { fetchJson(NOTICES_URL) }.getOrNull() ?: return@withContext null
            val notices = runCatching { parse(json) }.getOrNull()
            if (notices.isNullOrEmpty()) return@withContext null
            prefs.edit()
                .putString(KEY_CACHE, json.toString())
                .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                .apply()
            notices
        }

    private fun fetchJson(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Parses either a root array or { "notices": [...] }. Falls back to defaults on error. */
    private fun parse(raw: String): List<Notice> {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("[")) {
            parseArray(JSONArray(trimmed))
        } else {
            parseObject(JSONObject(trimmed))
        }
    }

    private fun parseObject(root: JSONObject): List<Notice> {
        val arr: JSONArray = when {
            root.has("notices") -> root.getJSONArray("notices")
            root.has("data") && root.optJSONObject("data")?.has("notices") == true ->
                root.getJSONObject("data").getJSONArray("notices")
            else -> return defaults()
        }
        return parseArray(arr)
    }

    private fun parseArray(arr: JSONArray): List<Notice> {
        val result = mutableListOf<Notice>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "").trim()
            val body = o.optString("body", "").trim()
            if (title.isEmpty() || body.isEmpty()) continue
            result.add(
                Notice(
                    icon = o.optString("icon", "🔔").ifEmpty { "🔔" },
                    title = title,
                    body = body,
                    url = o.optString("url", "").trim().takeIf { it.isNotEmpty() }
                )
            )
        }
        return result.ifEmpty { defaults() }
    }
}
