package com.snapsave.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Runs yt-dlp binary extracted from APK assets.
 * Handles binary extraction, execution, and output parsing.
 */
object YtDlpRunner {

    private const val TAG = "YtDlpRunner"
    private const val BINARY_NAME = "ytdlp-arm64"
    private const val BINARY_DIR = "bin"

    /**
     * Extract yt-dlp binary from assets to app's private directory.
     * Only extracts once (skips if already exists and correct size).
     */
    fun extractBinary(context: Context): File? {
        return try {
            val binDir = File(context.filesDir, BINARY_DIR)
            binDir.mkdirs()
            val binaryFile = File(binDir, BINARY_NAME)

            // Check if already extracted (compare size)
            if (binaryFile.exists() && binaryFile.length() > 10_000_000) {
                Log.d(TAG, "Binary already extracted: ${binaryFile.absolutePath} (${binaryFile.length()} bytes)")
                return binaryFile
            }

            Log.d(TAG, "Extracting yt-dlp binary from assets...")
            context.assets.open(BINARY_NAME).use { input ->
                FileOutputStream(binaryFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Make executable
            binaryFile.setExecutable(true, false)
            binaryFile.setReadable(true, false)

            Log.d(TAG, "Binary extracted: ${binaryFile.absolutePath} (${binaryFile.length()} bytes)")
            binaryFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract binary", e)
            null
        }
    }

    /**
     * Get video info using yt-dlp --dump-json
     * Returns JSON with video details and format list.
     */
    suspend fun getVideoInfo(
        context: Context,
        url: String
    ): Result<YtDlpVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val binary = extractBinary(context)
                ?: return@withContext Result.failure(Exception("Failed to extract yt-dlp binary"))

            val command = listOf(
                binary.absolutePath,
                "--dump-json",
                "--no-warnings",
                "--no-playlist",
                "--no-check-certificates",
                "--geo-bypass",
                url
            )

            Log.d(TAG, "Running: ${command.joinToString(" ")}")
            val result = executeCommand(command)

            if (result.exitCode != 0) {
                val error = result.stderr.ifEmpty { result.stdout }
                Log.e(TAG, "yt-dlp failed (exit ${result.exitCode}): $error")
                return@withContext Result.failure(Exception("yt-dlp error: ${error.take(200)}"))
            }

            val json = JSONObject(result.stdout)
            val info = parseVideoInfo(json)
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "getVideoInfo failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download video/audio using yt-dlp.
     * Returns the output file path.
     */
    suspend fun download(
        context: Context,
        url: String,
        outputPath: String,
        format: String,  // "bestvideo+bestaudio" or "bestaudio" etc
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val binary = extractBinary(context)
                ?: return@withContext Result.failure(Exception("Failed to extract yt-dlp binary"))

            val command = mutableListOf(
                binary.absolutePath,
                "-f", format,
                "--no-warnings",
                "--no-playlist",
                "--no-check-certificates",
                "--geo-bypass",
                "--newline",  // Print progress on new lines
                "-o", outputPath,
                url
            )

            Log.d(TAG, "Downloading: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            // Read stdout for progress
            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        Log.d(TAG, "yt-dlp: $line")
                        onProgress(line)
                    }
                } catch (_: Exception) {}
            }
            stdoutThread.start()

            // Read stderr
            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        Log.w(TAG, "yt-dlp stderr: $line")
                    }
                } catch (_: Exception) {}
            }
            stderrThread.start()

            val exitCode = process.waitFor()
            stdoutThread.join(5000)
            stderrThread.join(5000)

            if (exitCode != 0) {
                return@withContext Result.failure(Exception("yt-dlp download failed (exit $exitCode)"))
            }

            // Find the output file
            val outputFile = File(outputPath)
            if (outputFile.exists()) {
                Result.success(outputFile.absolutePath)
            } else {
                // yt-dlp might add extension, search for the file
                val dir = outputFile.parentFile ?: File(context.filesDir, "downloads")
                val baseName = outputFile.nameWithoutExtension
                val found = dir.listFiles()?.firstOrNull { it.name.startsWith(baseName) }
                if (found != null) {
                    Result.success(found.absolutePath)
                } else {
                    Result.failure(Exception("Output file not found after download"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific format URL for direct download.
     * Uses yt-dlp -g to get the URL.
     */
    suspend fun getFormatUrl(
        context: Context,
        url: String,
        formatId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val binary = extractBinary(context)
                ?: return@withContext Result.failure(Exception("Failed to extract yt-dlp binary"))

            val command = listOf(
                binary.absolutePath,
                "-f", formatId,
                "-g",  // Get URL only
                "--no-warnings",
                "--no-playlist",
                "--no-check-certificates",
                "--geo-bypass",
                url
            )

            val result = executeCommand(command)

            if (result.exitCode != 0) {
                return@withContext Result.failure(Exception("yt-dlp error: ${result.stderr.take(200)}"))
            }

            val downloadUrl = result.stdout.trim()
            if (downloadUrl.isNotEmpty()) {
                Result.success(downloadUrl)
            } else {
                Result.failure(Exception("No URL returned"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeCommand(command: List<String>): ProcessResult {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return ProcessResult(exitCode, stdout, stderr)
    }

    private fun parseVideoInfo(json: JSONObject): YtDlpVideoInfo {
        val title = json.optString("title", "Unknown")
        val duration = json.optDouble("duration", 0.0)
        val thumbnail = json.optString("thumbnail", "")
        val uploader = json.optString("uploader", "")

        val formats = mutableListOf<YtDlpFormat>()

        // Parse formats array
        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val fmt = formatsArray.optJSONObject(i) ?: continue
                val formatId = fmt.optString("format_id", "")
                val ext = fmt.optString("ext", "")
                val resolution = fmt.optString("resolution", "")
                val height = fmt.optInt("height", 0)
                val width = fmt.optInt("width", 0)
                val fps = fmt.optInt("fps", 0)
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                val filesize = fmt.optLong("filesize", 0).takeIf { it > 0 }
                    ?: fmt.optLong("filesize_approx", 0).takeIf { it > 0 }
                val tbr = fmt.optDouble("tbr", 0.0)
                val abr = fmt.optDouble("abr", 0.0)
                val formatNote = fmt.optString("format_note", "")
                val url = fmt.optString("url", "")

                // Skip formats without URL
                if (url.isEmpty()) continue

                val hasVideo = vcodec != "none"
                val hasAudio = acodec != "none"

                val label = buildString {
                    if (hasVideo && hasAudio) {
                        append("${height}p (video+audio)")
                    } else if (hasVideo) {
                        append("${height}p video")
                    } else if (hasAudio) {
                        append("Audio")
                        if (abr > 0) append(" ${abr.toInt()}kbps")
                    }
                    append(" • $ext")
                    if (formatNote.isNotEmpty()) append(" • $formatNote")
                }

                formats.add(YtDlpFormat(
                    formatId = formatId,
                    label = label,
                    ext = ext,
                    height = height,
                    width = width,
                    fps = fps,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                    filesize = filesize,
                    bitrate = (tbr * 1000).toInt(),
                    url = url
                ))
            }
        }

        return YtDlpVideoInfo(
            title = title,
            duration = duration,
            thumbnail = thumbnail,
            uploader = uploader,
            formats = formats
        )
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )
}

data class YtDlpVideoInfo(
    val title: String,
    val duration: Double,
    val thumbnail: String,
    val uploader: String,
    val formats: List<YtDlpFormat>
)

data class YtDlpFormat(
    val formatId: String,
    val label: String,
    val ext: String,
    val height: Int,
    val width: Int,
    val fps: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val filesize: Long?,
    val bitrate: Int,
    val url: String
)
