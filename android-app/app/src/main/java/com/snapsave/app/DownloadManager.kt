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
        fun onStatusUpdate(statusText: String)
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
        formatId: String? = null, // specific yt-dlp format ID from UI selection
        callback: DownloadCallback
    ) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val platform = PlatformDetector.detect(url)

                when (platform) {
                    Platform.YOUTUBE -> downloadYouTube(url, type, formatId, callback)
                    Platform.TIKTOK -> downloadTikTok(url, type, callback)
                    Platform.FACEBOOK -> downloadFacebook(url, type, callback)
                    Platform.INSTAGRAM -> downloadInstagram(url, type, callback)
                    Platform.THREADS -> downloadThreads(url, type, callback)
                    else -> postError(callback, "Platform not supported yet: ${platform.displayName}")
                }
            } catch (e: CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                postError(callback, e.message ?: "Download failed")
            }
        }
    }

    private suspend fun downloadYouTube(url: String, type: String, formatId: String?, callback: DownloadCallback) {
        val format = if (!formatId.isNullOrEmpty()) {
            // Use the specific format ID selected by user in UI
            // This downloads a single stream — no merge/ffmpeg needed
            formatId
        } else if (type == "audio") {
            // Audio-only: single stream, no merge needed
            "bestaudio[ext=m4a]/bestaudio/best"
        } else {
            // Video-only: single stream without audio, no merge needed
            "bestvideo[ext=mp4]/best[ext=mp4]/best"
        }

        val dir = java.io.File(context.filesDir, "downloads")
        dir.mkdirs()
        val outputPath = java.io.File(dir, "%(title)s.%(ext)s").absolutePath

        // Show initial status
        postProgress(callback, 0, 0, 0)

        // Start download in background thread (non-blocking)
        YtDlpRunner.startDownload(context, url, outputPath, format)

        // Poll progress every 500ms
        var lastPercent = -1
        var lastProgressTime = System.currentTimeMillis()
        val timeoutMs = 60_000L  // 60 seconds no progress = timeout

        while (YtDlpRunner.isDownloading()) {
            kotlinx.coroutines.delay(500)

            val progress = YtDlpRunner.getProgress(context)
            val currentPercent = progress.percent.toInt()

            // Update progress if changed
            if (currentPercent != lastPercent && currentPercent >= 0) {
                lastPercent = currentPercent
                lastProgressTime = System.currentTimeMillis()

                // Format speed and ETA for display
                val speedStr = progress.speed.ifEmpty { null }
                val etaStr = progress.eta.ifEmpty { null }
                val phaseStr = when (progress.phase) {
                    "extracting" -> "Extracting video info..."
                    "downloading" -> {
                        val parts = mutableListOf("Downloading")
                        if (progress.total > 0) {
                            parts.add("${formatFileSize(progress.downloaded)} / ${formatFileSize(progress.total)}")
                        }
                        parts.add("$currentPercent%")
                        if (!speedStr.isNullOrEmpty()) parts.add(speedStr)
                        if (!etaStr.isNullOrEmpty()) parts.add("ETA $etaStr")
                        parts.joinToString(" ")
                    }
                    "finalizing" -> "Finalizing..."
                    else -> "Downloading $currentPercent%"
                }

                postStatusText(callback, phaseStr)
                postProgress(callback, progress.downloaded, progress.total, currentPercent)
            }

            // Check for timeout — no progress for 60s
            if (System.currentTimeMillis() - lastProgressTime > timeoutMs && currentPercent < 100) {
                YtDlpRunner.stopDownload()
                throw Exception("Download timed out — no progress for 60 seconds. Check your internet connection.")
            }
        }

        // Get final result
        val result = YtDlpRunner.getResult()
        if (result.isFailure) {
            throw Exception(result.exceptionOrNull()?.message ?: "Download failed")
        }

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
        postStatusText(callback, "Fetching TikTok video info...")
        val info = TikTokExtractor.extract(url).getOrThrow()

        if (type == "audio") {
            val audioUrl = info.audioUrl ?: throw Exception("No audio available for this video")
            val filename = "${sanitizeFilename(info.title)}.mp3"

            postStatusText(callback, "Downloading audio...")
            TikTokExtractor.downloadFile(audioUrl, filename) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                postProgress(callback, downloaded, total, percent)
            }.getOrThrow()

            postComplete(callback, filename)
        } else {
            val videoUrl = info.videoNoWmUrl.ifEmpty { info.videoUrl }
            val filename = "${sanitizeFilename(info.title)}.mp4"

            postStatusText(callback, "Downloading video...")
            TikTokExtractor.downloadFile(videoUrl, filename) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                postProgress(callback, downloaded, total, percent)
            }.getOrThrow()

            postComplete(callback, filename)
        }
    }

    private suspend fun downloadFacebook(url: String, type: String, callback: DownloadCallback) {
        postStatusText(callback, "Fetching Facebook video info...")
        val info = FacebookExtractor.extract(url).getOrThrow()

        if (info.videoUrl.isEmpty()) {
            throw Exception("Video URL not found. The video may require login.")
        }

        val filename = "${sanitizeFilename(info.title)}.mp4"

        postStatusText(callback, "Downloading video...")
        FacebookExtractor.downloadFile(info.videoUrl, filename) { downloaded, total ->
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
            postProgress(callback, downloaded, total, percent)
        }.getOrThrow()

        postComplete(callback, filename)
    }

    private suspend fun downloadInstagram(url: String, type: String, callback: DownloadCallback) {
        postStatusText(callback, "Fetching Instagram media info...")
        val info = InstagramExtractor.extract(url).getOrThrow()

        if (info.videoUrl.isEmpty()) {
            throw Exception("Video URL not found. The post may be private or require login.")
        }

        val filename = "${sanitizeFilename(info.title)}.mp4"

        postStatusText(callback, "Downloading video...")
        InstagramExtractor.downloadFile(info.videoUrl, filename) { downloaded, total ->
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
            postProgress(callback, downloaded, total, percent)
        }.getOrThrow()

        postComplete(callback, filename)
    }

    private suspend fun downloadThreads(url: String, type: String, callback: DownloadCallback) {
        postStatusText(callback, "Fetching Threads media info...")
        val info = ThreadsExtractor.extract(url).getOrThrow()

        if (info.videoUrl.isEmpty()) {
            throw Exception("Video URL not found. The post may be private or not contain a video.")
        }

        val filename = "${sanitizeFilename(info.title)}.mp4"

        postStatusText(callback, "Downloading video...")
        ThreadsExtractor.downloadFile(info.videoUrl, filename) { downloaded, total ->
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

    private fun postStatusText(callback: DownloadCallback, text: String) {
        handler.post { callback.onStatusUpdate(text) }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
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
