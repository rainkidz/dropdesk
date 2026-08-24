package com.snapsave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TikTok video extractor using tikwm.com API.
 * Works directly on device — no server required.
 */
object TikTokExtractor {

    private const val TIKWM_API = "https://www.tikwm.com/api/"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class TikTokInfo(
        val title: String,
        val duration: Double,
        val videoUrl: String,       // Video with watermark
        val videoNoWmUrl: String,   // Video without watermark
        val audioUrl: String?,      // Audio/music only
        val coverUrl: String?,
        val author: String?
    )

    /**
     * Extract TikTok video info.
     * Accepts both regular URLs and short URLs (vm.tiktok.com).
     */
    suspend fun extract(url: String): Result<TikTokInfo> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("url", url)
                .add("count", "12")
                .add("cursor", "0")
                .add("web", "1")
                .add("hd", "1")
                .build()

            val request = Request.Builder()
                .url("${TIKWM_API}tiktok")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.tikwm.com/")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)

            val code = json.optInt("code", -1)
            if (code != 0) {
                val msg = json.optString("msg", "Unknown error")
                throw Exception("TikTok API error: $msg")
            }

            val data = json.optJSONObject("data") ?: throw Exception("No data in response")

            val title = data.optString("title", "").let {
                if (it.isEmpty() || it == "TikTok") "TikTok Video" else it
            }
            val duration = data.optDouble("duration", 0.0)
            val videoUrl = data.optString("play", "")
            val videoNoWmUrl = data.optString("play_addr", "").let {
                if (it.isEmpty()) data.optString("hdplay", "") else it
            }
            val audioUrl = data.optString("music", "")
            val coverUrl = data.optString("cover", "")
            val author = data.optString("author", "").let {
                if (it.isEmpty()) null else it
            }

            if (videoUrl.isEmpty() && videoNoWmUrl.isEmpty()) {
                throw Exception("No video URL found")
            }

            Result.success(TikTokInfo(
                title = title,
                duration = duration,
                videoUrl = videoUrl,
                videoNoWmUrl = videoNoWmUrl.ifEmpty { videoUrl },
                audioUrl = audioUrl.ifEmpty { null },
                coverUrl = coverUrl.ifEmpty { null },
                author = author
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
                .header("Referer", "https://www.tikwm.com/")
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
