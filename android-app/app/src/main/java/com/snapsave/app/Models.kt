package com.snapsave.app

import com.google.gson.annotations.SerializedName

// ── Inspect ──────────────────────────────────────────────────

data class InspectRequest(
    val url: String
)

data class InspectResponse(
    val platform: String,
    val title: String?,
    val duration: Double?,
    val thumbnail: String?,
    val formats: List<FormatOption>
)

data class FormatOption(
    val formatId: String,
    val label: String,
    val ext: String,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
    @SerializedName("filesize_bytes")
    val filesizeBytes: Long? = null,
    @SerializedName("requires_premium")
    val requiresPremium: Boolean = false
)

// ── Download ─────────────────────────────────────────────────

data class DownloadRequest(
    val url: String,
    @SerializedName("format_id")
    val formatId: String,
    val platform: String
)

data class DownloadResponse(
    @SerializedName("job_id")
    val jobId: String,
    val status: String
)

data class DownloadStatus(
    val id: String,
    val status: String,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
    @SerializedName("save_url")
    val saveUrl: String? = null,
    val filename: String? = null,
    @SerializedName("content_type")
    val contentType: String? = null,
    val error: String? = null,
    val progress: Double? = null
)

// ── Premium Download ─────────────────────────────────────────

data class PremiumFormat(
    @SerializedName("format_id")
    val formatId: String,
    val label: String,
    val ext: String,
    @SerializedName("filesize_bytes")
    val filesizeBytes: Long? = null
)

data class PremiumDownloadRequest(
    val url: String,
    val platform: String,
    @SerializedName("resolution")
    val resolution: String,
    val type: String
)
