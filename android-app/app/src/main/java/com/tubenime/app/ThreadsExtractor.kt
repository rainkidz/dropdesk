package com.tubenime.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Threads video extractor using web scraping.
 * Works for public posts without login.
 */
object ThreadsExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ThreadsInfo(
        val title: String,
        val videoUrl: String,
        val thumbnail: String?,
        val isVideo: Boolean
    )

    /**
     * Extract Threads video info from URL.
     * Supports: /@user/post/ID, /post/ID
     *
     * When [cookiesFile] is provided (a Netscape cookies.txt captured by
     * CookieLoginActivity for a logged-in Threads account), the post page is
     * fetched authenticated — Meta gates anonymous post pages behind a login
     * wall, but a logged-in page embeds the `video_versions` JSON with a direct
     * Meta CDN URL.
     */
    suspend fun extract(url: String, cookiesFile: File? = null): Result<ThreadsInfo> = withContext(Dispatchers.IO) {
        try {
            // Normalize URL
            val normalizedUrl = normalizeUrl(url)

            // Try to extract from the post page (authenticated if cookies provided)
            val result = tryPostPage(normalizedUrl, cookiesFile)

            if (result == null || result.videoUrl.isEmpty()) {
                throw Exception("Could not extract video URL. Threads requires login — add Threads cookies (Premium) to download this post.")
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build a Cookie request header from a Netscape cookies.txt file.
     * Returns null when there are no usable cookies.
     */
    private fun readCookiesHeader(cookiesFile: File?): String? {
        if (cookiesFile == null || !cookiesFile.exists()) return null
        val pairs = mutableListOf<String>()
        cookiesFile.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            // Netscape format: domain \t includeSubdomains \t path \t secure \t expiry \t name \t value
            val parts = line.split("\t")
            if (parts.size >= 7) {
                val name = parts[5].trim()
                val value = parts[6].trim()
                if (name.isNotEmpty()) pairs.add("$name=$value")
            }
        }
        return if (pairs.isEmpty()) null else pairs.joinToString("; ")
    }

    /**
     * Download a file with progress callback.
     */
    suspend fun downloadFile(
        url: String,
        outputPath: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.threads.net/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty body")
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                java.io.FileOutputStream(outputPath).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Try to extract from the post page.
     */
    private suspend fun tryPostPage(url: String, cookiesFile: File?): ThreadsInfo? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.threads.net/")

            readCookiesHeader(cookiesFile)?.let { builder.header("Cookie", it) }

            val response = client.newCall(builder.build()).execute()
            val html = response.body?.string() ?: return@withContext null

            // Try to find video URL in page source
            val videoUrl = findVideoUrlInHtml(html)
            if (videoUrl.isNullOrEmpty()) return@withContext null

            val title = extractTitle(html) ?: "Threads Video"
            val thumbnail = extractThumbnail(html)

            ThreadsInfo(
                title = title,
                videoUrl = videoUrl,
                thumbnail = thumbnail,
                isVideo = true
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalize Threads URL.
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()

        // Convert threads.com to threads.net if needed
        normalized = normalized.replace("threads.com", "threads.net")

        // Ensure https
        if (!normalized.startsWith("http")) {
            normalized = "https://$normalized"
        }

        return normalized
    }

    /**
     * Find video URL in HTML using various patterns.
     */
    private fun findVideoUrlInHtml(html: String): String? {
        val patterns = listOf(
            // Threads/Meta specific patterns
            // video_versions array (logged-in comet page) — matched by the generic
            // "src":"...mp4..." pattern below; keep these named fields too.
            Pattern.compile("\"video_playback_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"video_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"playback_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"url\":\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("\"src\":\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("video_src[^>]*src=\"(https?://[^\"]+)\""),
            // CDN patterns (Threads uses Meta CDN)
            Pattern.compile("(https?://scontent[^\"]+\\.mp4[^\"]*)"),
            Pattern.compile("(https?://[^\"]*cdninstagram[^\"]+\\.mp4[^\"]*)"),
            Pattern.compile("(https?://[^\"]*fbcdn[^\"]+\\.mp4[^\"]*)"),
            // Generic video patterns
            Pattern.compile("\"contentUrl\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"embedUrl\":\"(https?://[^\"]+)\"")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                var url = matcher.group(1)
                    ?.replace("\\u0025", "%")
                    ?.replace("\\/", "/")
                    ?.replace("&amp;", "&")
                    ?.replace("\\\"", "\"")

                if (!url.isNullOrEmpty() && url.startsWith("http")) {
                    return url
                }
            }
        }

        return null
    }

    /**
     * Extract title from HTML.
     */
    private fun extractTitle(html: String): String? {
        val patterns = listOf(
            Pattern.compile("<meta[^>]*property=\"og:title\"[^>]*content=\"([^\"]+)\""),
            Pattern.compile("<title>([^<]+)</title>"),
            Pattern.compile("\"text\":\"([^\"]{1,200})\"")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                return matcher.group(1)
                    ?.replace("\\u0025", "%")
                    ?.replace("\\/", "/")
                    ?.trim()
            }
        }

        return null
    }

    /**
     * Extract thumbnail URL from HTML.
     */
    private fun extractThumbnail(html: String): String? {
        val patterns = listOf(
            Pattern.compile("<meta[^>]*property=\"og:image\"[^>]*content=\"([^\"]+)\""),
            Pattern.compile("\"thumbnail_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"image\":\"(https?://[^\"]+)\"")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val url = matcher.group(1)
                    ?.replace("\\u0025", "%")
                    ?.replace("\\/", "/")

                if (!url.isNullOrEmpty() && url.startsWith("http")) {
                    return url
                }
            }
        }

        return null
    }
}
