package com.snapsave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
     */
    suspend fun extract(url: String): Result<ThreadsInfo> = withContext(Dispatchers.IO) {
        try {
            // Normalize URL
            val normalizedUrl = normalizeUrl(url)

            // Try to extract from the post page
            val result = tryPostPage(normalizedUrl)

            if (result == null || result.videoUrl.isEmpty()) {
                throw Exception("Could not extract video URL. The post may be private or not contain a video.")
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    private suspend fun tryPostPage(url: String): ThreadsInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
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
            Pattern.compile("\"video_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"url\":\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("\"src\":\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("video_src[^>]*src=\"(https?://[^\"]+)\""),
            Pattern.compile("\"playback_url\":\"(https?://[^\"]+)\""),
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
