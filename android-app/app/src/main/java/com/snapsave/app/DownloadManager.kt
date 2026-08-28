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
                    // All platforms use yt-dlp — it supports YouTube, TikTok, Facebook,
                    // Instagram, Threads, and 1000+ other sites
                    Platform.YOUTUBE, Platform.TIKTOK, Platform.FACEBOOK,
                    Platform.INSTAGRAM, Platform.THREADS ->
                        downloadWithYtDlp(url, type, formatId, callback)
                    else -> postError(callback, "Platform not supported: ${platform.displayName}")
                }
            } catch (e: CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                postError(callback, e.message ?: "Download failed")
            }
        }
    }

    /**
     * Unified download for ALL platforms via yt-dlp.
     * yt-dlp supports YouTube, TikTok, Facebook, Instagram, Threads, and 1000+ sites.
     */
    private suspend fun downloadWithYtDlp(url: String, type: String, formatId: String?, callback: DownloadCallback) {
        // Video only or audio only — single stream, no merge, no ffmpeg needed
        val format = if (!formatId.isNullOrEmpty()) {
            formatId
        } else if (type == "audio") {
            "bestaudio/best"
        } else {
            "bestvideo/best"
        }

        val dir = java.io.File(context.filesDir, "downloads")
        dir.mkdirs()
        val outputPath = java.io.File(dir, "%(title)s.%(ext)s").absolutePath

        postProgress(callback, 0, 0, 0)

        // Find cookies file for this platform (if user logged in)
        val platform = PlatformDetector.detect(url)
        val cookiesFile = when (platform) {
            Platform.FACEBOOK -> CookieLoginActivity.getCookiesFile(context, "facebook").let { if (it.exists()) it.absolutePath else null }
            Platform.INSTAGRAM -> CookieLoginActivity.getCookiesFile(context, "instagram").let { if (it.exists()) it.absolutePath else null }
            Platform.THREADS -> CookieLoginActivity.getCookiesFile(context, "threads").let { if (it.exists()) it.absolutePath else null }
            else -> null
        }

        YtDlpRunner.startDownload(context, url, outputPath, format, cookiesFile)

        var lastPercent = -1
        var lastProgressTime = System.currentTimeMillis()
        val timeoutMs = 120_000L

        var downloadDone = false
        while (!downloadDone) {
            kotlinx.coroutines.delay(500)

            val progress = YtDlpRunner.getProgress(context)
            val currentPercent = progress.percent.toInt()

            if (progress.phase == "done" || progress.phase == "error") {
                downloadDone = true
                if (progress.phase == "done") {
                    postStatusText(callback, "Download complete!")
                    postProgress(callback, 0, 0, 100)
                }
                break
            }
            // Also break if thread finished but phase is still finalizing
            if (!YtDlpRunner.isDownloading() && progress.phase == "finalizing") {
                downloadDone = true
                postStatusText(callback, "Download complete!")
                postProgress(callback, 0, 0, 100)
                break
            }

            val speedStr = progress.speed.ifEmpty { null }
            val etaStr = progress.eta.ifEmpty { null }
            val phaseStr = when (progress.phase) {
                "extracting" -> "Extracting info..."
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
            lastProgressTime = System.currentTimeMillis()

            if (System.currentTimeMillis() - lastProgressTime > timeoutMs && currentPercent < 100) {
                YtDlpRunner.stopDownload()
                throw Exception("Download timed out — no progress for 2 minutes.")
            }
        }

        val progress = YtDlpRunner.getProgress(context)
        if (progress.phase == "error") {
            throw Exception(progress.error.ifEmpty { "Download failed" })
        }

        val result = YtDlpRunner.getResult()
        if (result.isFailure) {
            throw Exception(result.exceptionOrNull()?.message ?: "Download failed")
        }

        val files = dir.listFiles()?.sortedByDescending { it.lastModified() }
        val downloadedFile = files?.firstOrNull()
        if (downloadedFile != null && downloadedFile.exists()) {
            postComplete(callback, downloadedFile.name)
        } else {
            throw Exception("Download completed but file not found")
        }
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
