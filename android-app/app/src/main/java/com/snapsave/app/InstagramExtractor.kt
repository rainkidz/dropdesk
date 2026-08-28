package com.snapsave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Instagram video extractor using oEmbed API and embed page scraping.
 * Works for public posts without login.
 */
object InstagramExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class InstagramInfo(
        val title: String,
        val videoUrl: String,
        val thumbnail: String?,
        val isVideo: Boolean
    )

    /**
     * Extract Instagram video info from URL.
     * Supports: /p/XXX/, /reel/XXX/, /tv/XXX/
     */
    suspend fun extract(url: String): Result<InstagramInfo> = withContext(Dispatchers.IO) {
        try {
            val shortcode = extractShortcode(url)
                ?: throw Exception("Invalid Instagram URL")

            // Try multiple extraction methods
            val result = tryEmbedPage(shortcode)
                ?: tryOembedPage(shortcode)
                ?: tryDirectPage(url)

            if (result == null || result.videoUrl.isEmpty()) {
                throw Exception("Could not extract video URL. The post may be private or require login.")
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
                .header("Referer", "https://www.instagram.com/")
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
     * Extract shortcode from Instagram URL.
     */
    private fun extractShortcode(url: String): String? {
        val patterns = listOf(
            Regex("/p/([A-Za-z0-9_-]+)"),
            Regex("/reel/([A-Za-z0-9_-]+)"),
            Regex("/tv/([A-Za-z0-9_-]+)"),
            Regex("/reels/([A-Za-z0-9_-]+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Try to extract video from embed page.
     * The embed page often contains the video URL without requiring login.
     */
    private suspend fun tryEmbedPage(shortcode: String): InstagramInfo? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            // Extract video URL from embed HTML
            val videoUrl = findVideoUrlInHtml(html)
            if (videoUrl.isNullOrEmpty()) return@withContext null

            // Extract title/caption
            val title = extractCaption(html) ?: "Instagram Reel"

            // Extract thumbnail
            val thumbnail = extractThumbnail(html)

            InstagramInfo(
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
     * Try to extract from oEmbed endpoint.
     * oEmbed provides basic metadata but may not include direct video URL.
     */
    private suspend fun tryOembedPage(shortcode: String): InstagramInfo? = withContext(Dispatchers.IO) {
        try {
            val postUrl = "https://www.instagram.com/p/$shortcode/"
            val oembedUrl = "https://api.instagram.com/oembed/?url=$postUrl"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val title = json.optString("title", "Instagram Reel")
            val thumbnailUrl = json.optString("thumbnail_url", "")

            // oEmbed doesn't provide direct video URL, but we got metadata
            // Try to get video from the thumbnail URL pattern
            // Instagram CDN pattern: https://scontent-*.cdninstagram.com/...
            if (thumbnailUrl.isNotEmpty()) {
                // The thumbnail might be a frame from the video
                // Try to construct video URL from thumbnail pattern
                val videoUrl = thumbnailUrl
                    .replace(Regex("\\?[^?]*$"), "") // Remove query params
                    .replace(".jpg", ".mp4")
                    .replace(".jpeg", ".mp4")

                if (videoUrl != thumbnailUrl) {
                    return@withContext InstagramInfo(
                        title = title,
                        videoUrl = videoUrl,
                        thumbnail = thumbnailUrl,
                        isVideo = true
                    )
                }
            }

            // If we can't get video URL, return null to try next method
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try to extract from the direct post page.
     */
    private suspend fun tryDirectPage(url: String): InstagramInfo? = withContext(Dispatchers.IO) {
        try {
            // Normalize URL
            val normalizedUrl = if (!url.startsWith("http")) "https://$url" else url

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            // Try to find video URL in page source
            val videoUrl = findVideoUrlInHtml(html)
            if (videoUrl.isNullOrEmpty()) return@withContext null

            val title = extractCaption(html) ?: "Instagram Reel"
            val thumbnail = extractThumbnail(html)

            InstagramInfo(
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
     * Find video URL in HTML using various patterns.
     */
    private fun findVideoUrlInHtml(html: String): String? {
        val patterns = listOf(
            // Video URL patterns
            Pattern.compile("\"video_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"url\":\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("\"src\":\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("video_src[^>]*src=\"(https?://[^\"]+)\""),
            Pattern.compile("\"playback_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("\"video_versions\":\\[\\{\"url\":\"(https?://[^\"]+)\""),
            // CDN patterns
            Pattern.compile("(https?://scontent[^\"]+\\.mp4[^\"]*)"),
            Pattern.compile("(https?://[^\"]*cdninstagram[^\"]+\\.mp4[^\"]*)")
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
     * Extract caption/title from HTML.
     */
    private fun extractCaption(html: String): String? {
        val patterns = listOf(
            Pattern.compile("\"caption\":\"([^\"]+)\""),
            Pattern.compile("<meta[^>]*property=\"og:title\"[^>]*content=\"([^\"]+)\""),
            Pattern.compile("<title>([^<]+)</title>")
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
            Pattern.compile("\"thumbnail_url\":\"(https?://[^\"]+)\""),
            Pattern.compile("<meta[^>]*property=\"og:image\"[^>]*content=\"([^\"]+)\""),
            Pattern.compile("\"display_url\":\"(https?://[^\"]+)\"")
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
