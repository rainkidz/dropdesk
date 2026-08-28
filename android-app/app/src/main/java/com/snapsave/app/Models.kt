package com.snapsave.app

/**
 * Standalone data models — no server dependency.
 */

// ── Platform Info ──────────────────────────────────────────
data class PlatformInfo(
    val platform: Platform,
    val title: String?,
    val duration: Double?,
    val thumbnail: String?,
    val formats: List<FormatChoice>
)

// ── Format Choice ──────────────────────────────────────────
data class FormatChoice(
    val id: String,
    val label: String,
    val type: String,       // "video" or "audio"
    val ext: String,
    val quality: String?,   // e.g. "720p", "1080p", "128kbps"
    val sizeBytes: Long?,
    val ytDlpFormatId: String? = null,  // actual yt-dlp format ID for direct download
    val height: Int = 0,     // video height in px for building format selector
    val bitrate: Int = 0     // audio bitrate for building format selector
)

// ── Download State ─────────────────────────────────────────
sealed class DownloadState {
    object Idle : DownloadState()
    object Inspecting : DownloadState()
    data class Ready(val info: PlatformInfo) : DownloadState()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val filename: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
