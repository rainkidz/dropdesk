package com.snapsave.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * YouTube video extractor using bundled yt-dlp binary.
 * Handles all YouTube encryption/cipher automatically via yt-dlp.
 */
object YouTubeExtractor {

    private const val TAG = "YouTubeExtractor"

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
        val isAudioOnly: Boolean,
        val formatIdForDl: String? = null  // yt-dlp format_id for direct download
    )

    /**
     * Extract YouTube video info using yt-dlp.
     * Requires context to access app's private directory for binary extraction.
     */
    suspend fun extract(context: Context, videoId: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            Log.d(TAG, "Extracting video info for: $url")

            val ytDlpInfo = YtDlpRunner.getVideoInfo(context, url).getOrThrow()

            val formats = mutableListOf<StreamFormat>()

            // Process all formats from yt-dlp
            for (fmt in ytDlpInfo.formats) {
                formats.add(StreamFormat(
                    itag = fmt.formatId.hashCode(), // Use hash as pseudo-itag
                    url = fmt.url,
                    mimeType = "${if (fmt.hasVideo) "video" else "audio"}/${fmt.ext}",
                    qualityLabel = fmt.label,
                    width = fmt.width.takeIf { it > 0 },
                    height = fmt.height.takeIf { it > 0 },
                    bitrate = fmt.bitrate.takeIf { it > 0 },
                    contentLength = fmt.filesize,
                    isAudioOnly = !fmt.hasVideo,
                    formatIdForDl = fmt.formatId
                ))
            }

            Log.d(TAG, "Extracted ${formats.size} formats for: ${ytDlpInfo.title}")

            if (formats.isEmpty()) {
                throw Exception("No downloadable formats found")
            }

            Result.success(VideoInfo(
                title = ytDlpInfo.title,
                duration = ytDlpInfo.duration,
                thumbnail = ytDlpInfo.thumbnail,
                formats = formats
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download a stream using yt-dlp with the specific format.
     */
    suspend fun downloadStream(
        context: Context,
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            YtDlpRunner.download(context, url, outputPath, formatId, onProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
