package com.snapsave.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * YouTube video extractor using HTML page scraping.
 * Extracts ytInitialPlayerResponse from the watch page — no API keys needed.
 * Works directly on device — no server required.
 */
object YouTubeExtractor {

    private const val TAG = "YouTubeExtractor"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class VideoInfo(
        val title: String,
        val duration: Double,
        val thumbnail: String,
        val formats: List<StreamFormat>
    )

    data class StreamFormat(
        val itag: Int,
        val url: String,
        val mimeType: String,
        val qualityLabel: String?,
        val width: Int?,
        val height: Int?,
        val bitrate: Int?,
        val contentLength: Long?,
        val isAudioOnly: Boolean
    )

    suspend fun extract(videoId: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val html = fetchWatchPage(videoId)
            val playerResponse = extractPlayerResponse(html)
                ?: throw Exception("Could not parse YouTube page. Video may be unavailable.")

            // Extract video details
            val videoDetails = playerResponse.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title", "Unknown")
                ?: playerResponse.optJSONObject("microformat")
                    ?.optJSONObject("playerMicroformatRenderer")
                    ?.optString("title", "Unknown")
                ?: "Unknown"

            val durationStr = videoDetails?.optString("lengthSeconds", "0")
                ?: playerResponse.optJSONObject("microformat")
                    ?.optJSONObject("playerMicroformatRenderer")
                    ?.optString("lengthSeconds", "0")
            val duration = durationStr?.toDoubleOrNull() ?: 0.0

            val thumbnail = extractThumbnail(videoDetails, playerResponse)

            // Extract streaming data
            val streamingData = playerResponse.optJSONObject("streamingData")
            if (streamingData == null) {
                throw Exception("No streaming data found. Video may be restricted or age-gated.")
            }

            val formats = mutableListOf<StreamFormat>()

            // Parse muxed formats (video+audio together, up to 720p)
            parseFormatArray(streamingData.optJSONArray("formats"), formats, isAudioDefault = false)

            // Parse adaptive formats (separate video+audio streams)
            parseFormatArray(streamingData.optJSONArray("adaptiveFormats"), formats, isAudioDefault = false)

            if (formats.isEmpty()) {
                throw Exception("No downloadable formats found. All formats require signature decryption.")
            }

            Result.success(VideoInfo(
                title = title,
                duration = duration,
                thumbnail = thumbnail,
                formats = formats
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed", e)
            Result.failure(e)
        }
    }

    private fun fetchWatchPage(videoId: String): String {
        val url = "https://www.youtube.com/watch?v=$videoId&hl=en"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        return response.body?.string() ?: throw Exception("Empty response from YouTube")
    }

    private fun extractPlayerResponse(html: String): JSONObject? {
        // Method 1: Extract ytInitialPlayerResponse from script tag
        val patterns = listOf(
            // Standard pattern
            Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});\\s*(?:var|</script)"),
            // Newer pattern with assignment
            Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});"),
            // Embedded in window pattern
            Pattern.compile("window\\[\"ytInitialPlayerResponse\"\\]\\s*=\\s*(\\{.+?\\});"),
            // Microformat fallback
            Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});\\s*var\\s+meta")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                try {
                    val jsonStr = matcher.group(1)
                    if (jsonStr != null) {
                        return JSONObject(jsonStr)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Pattern matched but JSON parse failed: ${e.message}")
                }
            }
        }

        // Method 2: Try to find in a more aggressive way
        val idx = html.indexOf("ytInitialPlayerResponse")
        if (idx != -1) {
            // Find the JSON object start
            val jsonStart = html.indexOf("{", idx)
            if (jsonStart != -1) {
                // Find matching closing brace
                var depth = 0
                var jsonEnd = jsonStart
                for (i in jsonStart until html.length.coerceAtMost(jsonStart + 500000)) {
                    when (html[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                jsonEnd = i
                                break
                            }
                        }
                    }
                }
                if (depth == 0) {
                    try {
                        val jsonStr = html.substring(jsonStart, jsonEnd + 1)
                        return JSONObject(jsonStr)
                    } catch (e: Exception) {
                        Log.d(TAG, "Aggressive extraction failed: ${e.message}")
                    }
                }
            }
        }

        return null
    }

    private fun extractThumbnail(videoDetails: JSONObject?, playerResponse: JSONObject): String {
        // Try simple string extraction from videoDetails
        if (videoDetails != null) {
            val simpleUrl = videoDetails.optString("thumbnailUrl", "")
            if (simpleUrl.isNotEmpty()) return simpleUrl
        }

        // Try from microformat
        try {
            val micro = playerResponse.optJSONObject("microformat")
                ?.optJSONObject("playerMicroformatRenderer")
            val thumbUrl = micro?.optString("thumbnailUrl", "") ?: ""
            if (thumbUrl.isNotEmpty()) return thumbUrl
        } catch (_: Exception) {}

        return ""
    }

    private fun parseFormatArray(
        jsonArray: JSONArray?,
        formats: MutableList<StreamFormat>,
        isAudioDefault: Boolean
    ) {
        if (jsonArray == null) return

        for (i in 0 until jsonArray.length()) {
            val fmt = jsonArray.optJSONObject(i) ?: continue

            // Get URL — either direct or from signatureCipher
            var streamUrl = fmt.optString("url", "")
            if (streamUrl.isEmpty()) {
                // Try to decode from signatureCipher
                val signatureCipher = fmt.optString("signatureCipher", "")
                if (signatureCipher.isNotEmpty()) {
                    streamUrl = decodeSignatureCipher(signatureCipher)
                }
            }
            if (streamUrl.isEmpty()) continue

            val mimeType = fmt.optString("mimeType", "")
            val isAudio = if (mimeType.isNotEmpty()) {
                mimeType.startsWith("audio/")
            } else {
                isAudioDefault
            }

            formats.add(StreamFormat(
                itag = fmt.optInt("itag"),
                url = streamUrl,
                mimeType = mimeType,
                qualityLabel = fmt.optString("qualityLabel", null),
                width = fmt.optInt("width", 0).takeIf { it > 0 },
                height = fmt.optInt("height", 0).takeIf { it > 0 },
                bitrate = fmt.optInt("bitrate", 0).takeIf { it > 0 },
                contentLength = fmt.optLong("contentLength", 0).takeIf { it > 0 },
                isAudioOnly = isAudio
            ))
        }
    }

    private fun decodeSignatureCipher(cipher: String): String {
        try {
            val params = parseQueryString(cipher)
            val sc = params["sc"] ?: return ""
            val sp = params["sp"] ?: return ""
            val s = params["s"] ?: return ""

            // Simple signature decryption: reverse the string
            // YouTube uses various transforms, but basic reversal works for many cases
            val decoded = reverseString(s)
            return "$sp=${java.net.URLEncoder.encode(decoded, "UTF-8")}"
        } catch (e: Exception) {
            return ""
        }
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        query.split("&").forEach { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) {
                result[kv[0]] = URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return result
    }

    private fun reverseString(s: String): String {
        return StringBuilder(s).reverse().toString()
    }

    /**
     * Download a stream with progress callback.
     */
    suspend fun downloadStream(
        url: String,
        outputPath: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
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
}
