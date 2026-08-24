package com.snapsave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * YouTube video extractor using Innertube (ANDROID) API.
 * Works directly on device — no server required.
 */
object YouTubeExtractor {

    private const val INNERTUBE_API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
    private const val INNERTUBE_URL = "https://www.youtube.com/youtubei/v1/player"
    private const val CLIENT_VERSION = "19.09.37"
    private const val ANDROID_SDK = 30

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    /**
     * Extract video info and stream URLs using Innertube ANDROID client.
     */
    suspend fun extract(videoId: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildInnertubeRequest(videoId)

            val request = Request.Builder()
                .url("$INNERTUBE_URL?key=$INNERTUBE_API_KEY")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android $ANDROID_SDK) gzip")
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)

            // Check for errors
            val playability = json.optJSONObject("playabilityStatus")
            if (playability?.optString("status") == "ERROR") {
                val reason = playability.optString("reason", "Video unavailable")
                return@withContext Result.failure(Exception(reason))
            }

            // Extract video details
            val videoDetails = json.optJSONObject("videoDetails")
                ?: throw Exception("No video details found")

            val title = videoDetails.optString("title", "Unknown")
            val duration = videoDetails.optDouble("lengthSeconds", 0.0)
            val thumbnail = videoDetails.optString("thumbnailUrl", "").let {
                val arr = videoDetails.optJSONArray("thumbnail")?.optJSONObject(0)
                arr?.optString("url", it) ?: it
            }

            // Extract streaming data
            val streamingData = json.optJSONObject("streamingData")
                ?: throw Exception("No streaming data found (video may be restricted)")

            val formats = mutableListOf<StreamFormat>()

            // Parse adaptive formats (separate video+audio streams)
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                    val streamUrl = fmt.optString("url", "")
                    if (streamUrl.isEmpty()) continue // Skip formats that need signature decryption

                    val mimeType = fmt.optString("mimeType", "")
                    val isAudio = mimeType.startsWith("audio/")

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

            // Parse muxed formats (video+audio together)
            val muxedFormats = streamingData.optJSONArray("formats")
            if (muxedFormats != null) {
                for (i in 0 until muxedFormats.length()) {
                    val fmt = muxedFormats.optJSONObject(i) ?: continue
                    val streamUrl = fmt.optString("url", "")
                    if (streamUrl.isEmpty()) continue

                    val mimeType = fmt.optString("mimeType", "")

                    formats.add(StreamFormat(
                        itag = fmt.optInt("itag"),
                        url = streamUrl,
                        mimeType = mimeType,
                        qualityLabel = fmt.optString("qualityLabel", null),
                        width = fmt.optInt("width", 0).takeIf { it > 0 },
                        height = fmt.optInt("height", 0).takeIf { it > 0 },
                        bitrate = fmt.optInt("bitrate", 0).takeIf { it > 0 },
                        contentLength = fmt.optLong("contentLength", 0).takeIf { it > 0 },
                        isAudioOnly = false
                    ))
                }
            }

            if (formats.isEmpty()) {
                throw Exception("No downloadable formats found. Video may require signature decryption.")
            }

            Result.success(VideoInfo(
                title = title,
                duration = duration,
                thumbnail = thumbnail,
                formats = formats
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a stream to the given file path with progress callback.
     */
    suspend fun downloadStream(
        url: String,
        outputPath: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android $ANDROID_SDK) gzip")
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

    private fun buildInnertubeRequest(videoId: String): String {
        val json = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", CLIENT_VERSION)
                    put("androidSdkVersion", ANDROID_SDK)
                    put("hl", "en")
                    put("gl", "US")
                    put("userAgent", "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android $ANDROID_SDK) gzip")
                })
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return json.toString()
    }
}
