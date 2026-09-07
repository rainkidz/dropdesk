package com.tubenime.app

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * yt-dlp runner via Chaquopy Python bridge.
 *
 * State (thread, result, error) is tracked **per job id**, so several downloads
 * (e.g. a premium batch queue running in parallel) don't overwrite each other.
 * All methods default to the ["main"] job, preserving the old single-download
 * behaviour used by the normal download flow.
 */
object YtDlpRunner {

    const val TAG = "YtDlpRunner"

    /** Job id used by the classic single (non-queue) download path. */
    const val DEFAULT_JOB = "main"

    private class JobState {
        @Volatile var thread: Thread? = null
        @Volatile var lastResult: String? = null
        @Volatile var error: String? = null
    }

    private val jobs = ConcurrentHashMap<String, JobState>()
    private var pythonInitialized = false

    private fun jobState(jobId: String): JobState =
        jobs.computeIfAbsent(jobId) { JobState() }

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
     * Call getProgress(jobId) to poll status.
     */
    fun startDownload(
        context: Context,
        url: String,
        outputPath: String,
        format: String,
        cookiesFile: String? = null,
        jobId: String = DEFAULT_JOB
    ) {
        val state = jobState(jobId)
        state.error = null
        state.lastResult = null
        state.thread?.interrupt()
        val thread = Thread {
            try {
                initPython(context)
                val py = Python.getInstance()
                val ytUtils = py.getModule("yt_utils")

                Log.d(TAG, "[$jobId] Downloading: $url with format $format cookies=$cookiesFile")

                val resultJson = ytUtils.callAttr(
                    "download_video",
                    url,
                    outputPath,
                    format,
                    cookiesFile ?: "",
                    jobId
                ).toString()

                val json = JSONObject(resultJson)
                if (json.has("error")) {
                    state.error = json.getString("error")
                } else {
                    state.lastResult = outputPath
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$jobId] download failed", e)
                state.error = e.message ?: "Download failed"
            }
        }
        state.thread = thread
        thread.start()
    }

    /**
     * Poll download progress for one job from Python.
     */
    fun getProgress(context: Context, jobId: String = DEFAULT_JOB): DownloadProgress {
        return try {
            initPython(context)
            val py = Python.getInstance()
            val ytUtils = py.getModule("yt_utils")
            val jsonStr = ytUtils.callAttr("get_progress", jobId).toString()
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
     * Check if the job's download thread is still running.
     */
    fun isDownloading(jobId: String = DEFAULT_JOB): Boolean =
        jobState(jobId).thread?.isAlive == true

    /**
     * Get download result for a job after completion.
     */
    fun getResult(jobId: String = DEFAULT_JOB): Result<String> {
        val state = jobState(jobId)
        val error = state.error
        if (error != null) {
            return Result.failure(Exception(error))
        }
        val result = state.lastResult
        if (result != null) {
            return Result.success(result)
        }
        return Result.failure(Exception("Download not completed"))
    }

    /**
     * Stop a job's download thread.
     */
    fun stopDownload(jobId: String = DEFAULT_JOB) {
        val state = jobState(jobId)
        state.thread?.interrupt()
        state.thread = null
    }

    /**
     * Start video+audio merge download (Premium feature).
     * Downloads video and audio separately, then merges with ffmpeg.
     */
    fun startMergeDownload(
        context: Context,
        url: String,
        outputPath: String,
        videoFormat: String,
        audioFormat: String = "bestaudio",
        cookiesFile: String? = null,
        jobId: String = DEFAULT_JOB
    ) {
        val state = jobState(jobId)
        state.error = null
        state.lastResult = null
        state.thread?.interrupt()
        val thread = Thread {
            try {
                initPython(context)
                val py = Python.getInstance()
                val ytUtils = py.getModule("yt_utils")
                val ffmpegDir = getFfmpegLocation(context)

                Log.d(TAG, "[$jobId] Merge download: $url video=$videoFormat audio=$audioFormat ffmpeg=$ffmpegDir")

                val resultJson = ytUtils.callAttr(
                    "download_video_audio",
                    url,
                    outputPath,
                    videoFormat,
                    audioFormat,
                    cookiesFile ?: "",
                    ffmpegDir ?: "",
                    jobId
                ).toString()

                val json = JSONObject(resultJson)
                if (json.has("error")) {
                    state.error = json.getString("error")
                } else {
                    state.lastResult = outputPath
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$jobId] merge download failed", e)
                state.error = e.message ?: "Merge download failed"
            }
        }
        state.thread = thread
        thread.start()
    }

    /**
     * Start playlist download (Premium feature).
     */
    fun startPlaylistDownload(
        context: Context,
        url: String,
        outputPath: String,
        format: String,
        maxVideos: Int = 50,
        cookiesFile: String? = null,
        jobId: String = DEFAULT_JOB
    ) {
        val state = jobState(jobId)
        state.error = null
        state.lastResult = null
        state.thread?.interrupt()
        val thread = Thread {
            try {
                initPython(context)
                val py = Python.getInstance()
                val ytUtils = py.getModule("yt_utils")
                val ffmpegDir = getFfmpegLocation(context)

                Log.d(TAG, "[$jobId] Playlist download: $url format=$format max=$maxVideos")

                val resultJson = ytUtils.callAttr(
                    "download_playlist",
                    url,
                    outputPath,
                    format,
                    maxVideos,
                    cookiesFile ?: "",
                    ffmpegDir ?: "",
                    jobId
                ).toString()

                val json = JSONObject(resultJson)
                if (json.has("error")) {
                    state.error = json.getString("error")
                } else {
                    state.lastResult = outputPath
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$jobId] playlist download failed", e)
                state.error = e.message ?: "Playlist download failed"
            }
        }
        state.thread = thread
        thread.start()
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
