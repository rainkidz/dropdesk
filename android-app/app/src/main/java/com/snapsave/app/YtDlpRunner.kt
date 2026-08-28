package com.snapsave.app

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * YouTube extraction using yt-dlp via Chaquopy Python bridge.
 * Runs yt-dlp as a Python package inside Android's runtime.
 */
object YtDlpRunner {

    private const val TAG = "YtDlpRunner"
    private var pythonInitialized = false
    private var downloadThread: Thread? = null
    private var lastResult: String? = null
    private var downloadError: String? = null

    private fun initPython(context: Context) {
        if (!pythonInitialized) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            pythonInitialized = true
        }
    }

    /**
     * Get the path to the ffmpeg binary provided by ffmpeg-kit.
     * ffmpeg-kit installs its .so files into the app's nativeLibraryDir.
     * yt-dlp can find ffmpeg if the binary is in the same directory.
     * Returns the directory path, or null if not available.
     */
    private fun getFfmpegLocation(context: Context): String? {
        return try {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            Log.d(TAG, "Native lib dir: $nativeDir")
            if (nativeDir != null && java.io.File(nativeDir).exists()) {
                nativeDir
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "ffmpeg-kit not available: ${e.message}")
            null
        }
    }

    /**
     * Get video info using yt-dlp via Python.
     */
    suspend fun getVideoInfo(
        context: Context,
        url: String
    ): Result<YtDlpVideoInfo> = withContext(Dispatchers.IO) {
        try {
            initPython(context)
            val py = Python.getInstance()
            val ytUtils = py.getModule("yt_utils")

            Log.d(TAG, "Fetching video info for: $url")
            val resultJson = ytUtils.callAttr("get_video_info", url).toString()

            val json = JSONObject(resultJson)
            if (json.has("error")) {
                return@withContext Result.failure(Exception(json.getString("error")))
            }

            val info = parseVideoInfo(json)
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "getVideoInfo failed", e)
            Result.failure(e)
        }
    }

    /**
     * Start download in background thread. Non-blocking.
     * Call getProgress() to poll status.
     */
    fun startDownload(
        context: Context,
        url: String,
        outputPath: String,
        format: String
    ) {
        lastResult = null
        downloadError = null
        downloadThread?.interrupt()
        downloadThread = Thread {
            try {
                initPython(context)
                val py = Python.getInstance()
                val ytUtils = py.getModule("yt_utils")

                Log.d(TAG, "Downloading: $url with format $format")

                val ffmpegDir = getFfmpegLocation(context)

                val resultJson = if (ffmpegDir != null) {
                    ytUtils.callAttr(
                        "download_video",
                        url,
                        outputPath,
                        format,
                        ffmpegDir
                    ).toString()
                } else {
                    ytUtils.callAttr(
                        "download_video",
                        url,
                        outputPath,
                        format
                    ).toString()
                }

                val json = JSONObject(resultJson)
                if (json.has("error")) {
                    downloadError = json.getString("error")
                } else {
                    lastResult = outputPath
                }
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                downloadError = e.message ?: "Download failed"
            }
        }
        downloadThread?.start()
    }

    /**
     * Poll download progress from Python. Returns structured progress data.
     */
    fun getProgress(context: Context): DownloadProgress {
        return try {
            initPython(context)
            val py = Python.getInstance()
            val ytUtils = py.getModule("yt_utils")
            val jsonStr = ytUtils.callAttr("get_progress").toString()
            val json = JSONObject(jsonStr)
            DownloadProgress(
                phase = json.optString("phase", "idle"),
                percent = json.optDouble("percent", 0.0),
                speed = json.optString("speed", ""),
                eta = json.optString("eta", ""),
                downloaded = json.optLong("downloaded", 0),
                total = json.optLong("total", 0),
                filename = json.optString("filename", ""),
                error = json.optString("error", "")
            )
        } catch (e: Exception) {
            DownloadProgress(phase = "error", error = e.message ?: "Progress unavailable")
        }
    }

    /**
     * Check if download is still running.
     */
    fun isDownloading(): Boolean = downloadThread?.isAlive == true

    /**
     * Get download result after completion.
     */
    fun getResult(): Result<String> {
        val error = downloadError
        if (error != null) {
            return Result.failure(Exception(error))
        }
        val result = lastResult
        if (result != null) {
            return Result.success(result)
        }
        return Result.failure(Exception("Download not completed"))
    }

    /**
     * Stop current download.
     */
    fun stopDownload() {
        downloadThread?.interrupt()
        downloadThread = null
    }

    /**
     * Legacy synchronous download — blocks until done.
     */
    suspend fun download(
        context: Context,
        url: String,
        outputPath: String,
        format: String,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            initPython(context)
            val py = Python.getInstance()
            val ytUtils = py.getModule("yt_utils")

            Log.d(TAG, "Downloading: $url with format $format")

            val ffmpegDir = getFfmpegLocation(context)

            val resultJson = if (ffmpegDir != null) {
                ytUtils.callAttr(
                    "download_video",
                    url,
                    outputPath,
                    format,
                    ffmpegDir
                ).toString()
            } else {
                ytUtils.callAttr(
                    "download_video",
                    url,
                    outputPath,
                    format
                ).toString()
            }

            val json = JSONObject(resultJson)
            if (json.has("error")) {
                return@withContext Result.failure(Exception(json.getString("error")))
            }

            Result.success(outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            Result.failure(e)
        }
    }

    private fun parseVideoInfo(json: JSONObject): YtDlpVideoInfo {
        val title = json.optString("title", "Unknown")
        val duration = json.optDouble("duration", 0.0)
        val thumbnail = json.optString("thumbnail", "")
        val uploader = json.optString("uploader", "")

        val formats = mutableListOf<YtDlpFormat>()

        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val fmt = formatsArray.optJSONObject(i) ?: continue
                val formatId = fmt.optString("format_id", "")
                val ext = fmt.optString("ext", "")
                val height = fmt.optInt("height", 0)
                val width = fmt.optInt("width", 0)
                val fps = fmt.optInt("fps", 0)
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                val filesize = fmt.optLong("filesize", 0).takeIf { it > 0 }
                val tbr = fmt.optDouble("tbr", 0.0)
                val abr = fmt.optDouble("abr", 0.0)
                val formatNote = fmt.optString("format_note", "")
                val url = fmt.optString("url", "")

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

data class DownloadProgress(
    val phase: String = "idle",
    val percent: Double = 0.0,
    val speed: String = "",
    val eta: String = "",
    val downloaded: Long = 0,
    val total: Long = 0,
    val filename: String = "",
    val error: String = ""
)
