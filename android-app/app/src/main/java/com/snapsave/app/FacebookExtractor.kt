package com.snapsave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Facebook video extractor using page scraping.
 * Works directly on device — no server required.
 */
object FacebookExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class FacebookInfo(
        val title: String,
        val videoUrl: String,
        val thumbnail: String?,
        val duration: Double?
    )

    /**
     * Extract Facebook video info by scraping the page.
     */
    suspend fun extract(url: String): Result<FacebookInfo> = withContext(Dispatchers.IO) {
        try {
            // Normalize URL
            val normalizedUrl = normalizeUrl(url)

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("Empty response")

            // Try to extract from meta tags first
            val title = extractMetaContent(html, "og:title")
                ?: extractMetaContent(html, "title")
                ?: "Facebook Video"

            val thumbnail = extractMetaContent(html, "og:image")

            // Try to find video URL from page source
            val videoUrl = findVideoUrl(html)

            if (videoUrl.isEmpty()) {
                // Try oEmbed API
                val oembedInfo = tryOembed(normalizedUrl)
                if (oembedInfo != null) {
                    return@withContext Result.success(FacebookInfo(
                        title = oembedInfo.first,
                        videoUrl = "", // oEmbed doesn't provide direct video URL
                        thumbnail = thumbnail,
                        duration = null
                    ))
                }
                throw Exception("Could not find video URL. The video may be private or require login.")
            }

            Result.success(FacebookInfo(
                title = title,
                videoUrl = videoUrl,
                thumbnail = thumbnail,
                duration = null
            ))
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

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        // Convert mobile URLs to desktop
        normalized = normalized.replace("m.facebook.com", "www.facebook.com")
        normalized = normalized.replace("mobile.facebook.com", "www.facebook.com")
        // Ensure https
        if (!normalized.startsWith("http")) {
            normalized = "https://$normalized"
        }
        return normalized
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Pattern.compile(
            "<meta[^>]*property=\"$property\"[^>]*content=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = pattern.matcher(html)
        if (matcher.find()) return matcher.group(1)

        // Try name attribute
        val pattern2 = Pattern.compile(
            "<meta[^>]*name=\"$property\"[^>]*content=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        )
        matcher = pattern2.matcher(html)
        if (matcher.find()) return matcher.group(1)

        return null
    }

    private fun findVideoUrl(html: String): String {
        // Try multiple patterns to find video URL
        val patterns = listOf(
            Pattern.compile("\"playable_url\":\"(https?[^\"]+)\""),
            Pattern.compile("\"playable_url_quality_hd\":\"(https?[^\"]+)\""),
            Pattern.compile("\"video_url\":\"(https?[^\"]+)\""),
            Pattern.compile("\"src\":\"(https?[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("video_src[^>]*src=\"(https?[^\"]+)\""),
            Pattern.compile("\"progressive_url\":\"(https?[^\"]+)\""),
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val url = matcher.group(1)
                    ?.replace("\\u0025", "%")
                    ?.replace("\\/", "/")
                    ?.replace("&amp;", "&")
                if (!url.isNullOrEmpty()) return url
            }
        }

        return ""
    }

    private suspend fun tryOembed(url: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val oembedUrl = "https://www.facebook.com/plugins/video/oembed.json/?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val title = json.optString("title", "Facebook Video")
            val html = json.optString("html", "")

            Pair(title, html)
        } catch (e: Exception) {
            null
        }
    }
}
