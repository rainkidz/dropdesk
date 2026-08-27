package com.snapsave.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Unified download manager that handles downloading from any platform.
 */
class DownloadManager(private val context: Context) {

    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int)
        fun onComplete(filePath: String, filename: String)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var currentJob: Job? = null

    /**
     * Download a file from URL to device storage.
     */
    fun download(
        url: String,
        filename: String,
        callback: DownloadCallback
    ) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clean filename
                val safeFilename = sanitizeFilename(filename)

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    postError(callback, "HTTP ${response.code}: ${response.message}")
                    return@launch
                }

                val body = response.body ?: throw Exception("Empty body")
                val totalBytes = body.contentLength()
                var bytesDownloaded = 0L

                body.byteStream().use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ — use MediaStore
                        val ext = safeFilename.substringAfterLast('.', "mp4")
                        val mimeType = getMimeType(ext)
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, safeFilename)
                            put(MediaStore.Downloads.MIME_TYPE, mimeType)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SnapSave")
                        }

                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            ?: throw Exception("Cannot create file")

                        resolver.openOutputStream(uri)?.use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) throw CancellationException()
                                output.write(buffer, 0, bytesRead)
                                bytesDownloaded += bytesRead
                                val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1
                                postProgress(callback, bytesDownloaded, totalBytes, percent)
                            }
                        }
                    } else {
                        // Android 9 and below — direct file write
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapSave")
                        dir.mkdirs()
                        val file = File(dir, safeFilename)

                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) throw CancellationException()
                                output.write(buffer, 0, bytesRead)
                                bytesDownloaded += bytesRead
                                val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1
                                postProgress(callback, bytesDownloaded, totalBytes, percent)
                            }
                        }
                    }
                }

                postComplete(callback, safeFilename)
            } catch (e: CancellationException) {
                // Download was cancelled
            } catch (e: Exception) {
                postError(callback, e.message ?: "Download failed")
            }
        }
    }

    /**
     * Download using the platform-specific extractor.
     */
    fun downloadFromUrl(
        url: String,
        type: String, // "video" or "audio"
        callback: DownloadCallback
    ) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val platform = PlatformDetector.detect(url)

                when (platform) {
                    Platform.YOUTUBE -> downloadYouTube(url, type, callback)
                    Platform.TIKTOK -> downloadTikTok(url, type, callback)
                    Platform.FACEBOOK -> downloadFacebook(url, type, callback)
                    else -> postError(callback, "Platform not supported yet: ${platform.displayName}")
                }
            } catch (e: CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                postError(callback, e.message ?: "Download failed")
            }
        }
    }

    private suspend fun downloadYouTube(url: String, type: String, callback: DownloadCallback) {
        val format = if (type == "audio") {
            "bestaudio/best"
        } else {
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
        }

        val dir = java.io.File(context.filesDir, "downloads")
        dir.mkdirs()
        val outputPath = java.io.File(dir, "%(title)s.%(ext)s").absolutePath

        postProgress(callback, 0, 0, 0)

        YtDlpRunner.download(context, url, outputPath, format) { line ->
            // Parse yt-dlp progress output
            val percentMatch = Regex("([\\d.]+)%").find(line)
            if (percentMatch != null) {
                val percent = percentMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: -1
                postProgress(callback, 0, 0, percent)
            }
            if (line.contains("100%")) {
                postProgress(callback, 0, 0, 100)
            }
        }.getOrThrow()

        // Find the downloaded file
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() }
        val downloadedFile = files?.firstOrNull()
        if (downloadedFile != null && downloadedFile.exists()) {
            postComplete(callback, downloadedFile.name)
        } else {
            throw Exception("Download completed but file not found")
        }
    }

    private suspend fun downloadTikTok(url: String, type: String, callback: DownloadCallback) {
        val info = TikTokExtractor.extract(url).getOrThrow()

        if (type == "audio") {
            val audioUrl = info.audioUrl ?: throw Exception("No audio available for this video")
            val filename = "${sanitizeFilename(info.title)}.mp3"

            TikTokExtractor.downloadFile(audioUrl, filename) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                postProgress(callback, downloaded, total, percent)
            }.getOrThrow()

            postComplete(callback, filename)
        } else {
            val videoUrl = info.videoNoWmUrl.ifEmpty { info.videoUrl }
            val filename = "${sanitizeFilename(info.title)}.mp4"

            TikTokExtractor.downloadFile(videoUrl, filename) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                postProgress(callback, downloaded, total, percent)
            }.getOrThrow()

            postComplete(callback, filename)
        }
    }

    private suspend fun downloadFacebook(url: String, type: String, callback: DownloadCallback) {
        val info = FacebookExtractor.extract(url).getOrThrow()

        if (info.videoUrl.isEmpty()) {
            throw Exception("Video URL not found. The video may require login.")
        }

        val filename = "${sanitizeFilename(info.title)}.mp4"

        FacebookExtractor.downloadFile(info.videoUrl, filename) { downloaded, total ->
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
            postProgress(callback, downloaded, total, percent)
        }.getOrThrow()

        postComplete(callback, filename)
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }

    private fun postProgress(callback: DownloadCallback, downloaded: Long, total: Long, percent: Int) {
        handler.post { callback.onProgress(downloaded, total, percent) }
    }

    private fun postComplete(callback: DownloadCallback, filename: String) {
        handler.post { callback.onComplete(filename, filename) }
    }

    private fun postError(callback: DownloadCallback, error: String) {
        handler.post { callback.onError(error) }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\-\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(100)
            .trim('_')
    }

    private fun getMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    private fun getExtFromMime(mimeType: String): String {
        return when {
            mimeType.contains("mp4") -> "mp4"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("mp3") -> "mp3"
            mimeType.contains("m4a") -> "m4a"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("opus") -> "opus"
            mimeType.contains("wav") -> "wav"
            else -> "mp4"
        }
    }
}
