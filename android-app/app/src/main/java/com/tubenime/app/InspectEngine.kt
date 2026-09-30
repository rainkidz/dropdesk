package com.tubenime.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mesin inspeksi & unduhan — dipindah utuh dari MainActivity (UI XML lama)
 * supaya alurnya bisa dipakai ComposeHomeActivity tanpa duplikasi.
 * Logika extractor identik dengan versi 4.3; hanya lapisan UI-nya yang dibuang:
 * status dikirim via callback [onStatus], progres via [DownloadManager.DownloadCallback].
 */
class InspectEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val downloadManager = DownloadManager(context)

    fun cancel() = downloadManager.cancel()

    /** Inspect URL via extractor per-platform; hasil dikirim lewat [onDone]. */
    fun inspect(
        url: String,
        onStatus: (String) -> Unit = {},
        onDone: (PlatformInfo) -> Unit,
        onError: (String) -> Unit,
    ) {
        val platform = PlatformDetector.detect(url)
        scope.launch {
            try {
                val info = when (platform) {
                    Platform.YOUTUBE -> inspectYouTube(url, onStatus)
                    Platform.TIKTOK -> inspectTikTok(url, onStatus)
                    Platform.FACEBOOK -> inspectFacebook(url, onStatus)
                    Platform.INSTAGRAM -> inspectInstagram(url, onStatus)
                    Platform.THREADS -> inspectThreads(url, onStatus)
                    Platform.BILIBILI -> inspectBilibili(url, onStatus)
                    else -> throw Exception("Platform not supported: ${platform.displayName}")
                }
                onDone(info)
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error occurred")
            }
        }
    }

    private suspend fun inspectYouTube(url: String, onStatus: (String) -> Unit): PlatformInfo {
        onStatus("Fetching YouTube video info...")
        val videoId = PlatformDetector.extractVideoId(url)
            ?: throw Exception("Cannot extract video ID from URL")

        val info = YouTubeExtractor.extract(context, videoId).getOrThrow()

        val formats = mutableListOf<FormatChoice>()
        val seenVideoHeights = mutableSetOf<Int>()
        val seenAudioBitrates = mutableSetOf<Int>()

        // Video formats — hanya tinggi unik, label bersih
        info.formats.filter { !it.isAudioOnly }.sortedByDescending { it.height ?: 0 }.forEach { fmt ->
            val ext = getExtFromMime(fmt.mimeType)
            val h = fmt.height ?: 0
            if (h <= 0 || h in seenVideoHeights) return@forEach
            seenVideoHeights.add(h)
            val fmtLabel = "${h}p — $ext"
            val selector = "bestvideo[height<=$h][ext=$ext]/bestvideo[height<=$h]"
            formats.add(FormatChoice(
                id = "video_${fmt.itag}",
                label = fmtLabel,
                type = "video",
                ext = ext,
                quality = "${h}p",
                sizeBytes = fmt.contentLength,
                ytDlpFormatId = selector,
                height = h
            ))
        }

        // Audio formats — bitrate dibulatkan ke 32kbps terdekat agar unik
        info.formats.filter { it.isAudioOnly }.sortedByDescending { it.bitrate ?: 0 }.forEach { fmt ->
            val ext = getExtFromMime(fmt.mimeType)
            val abr = ((fmt.bitrate ?: 0) / 1000)
            val roundedBitrate = (abr / 32) * 32
            if (roundedBitrate <= 0 || roundedBitrate in seenAudioBitrates) return@forEach
            seenAudioBitrates.add(roundedBitrate)
            val bitrateLabel = "${roundedBitrate}kbps"
            val selector = "bestaudio[ext=$ext][abr>${roundedBitrate - 50}]/bestaudio[ext=$ext]/bestaudio"
            formats.add(FormatChoice(
                id = "audio_${fmt.itag}",
                label = "Audio — $bitrateLabel — $ext",
                type = "audio",
                ext = ext,
                quality = bitrateLabel,
                sizeBytes = fmt.contentLength,
                ytDlpFormatId = selector,
                bitrate = fmt.bitrate ?: 0
            ))
        }

        return PlatformInfo(
            platform = Platform.YOUTUBE,
            title = info.title,
            duration = info.duration,
            thumbnail = info.thumbnail,
            formats = formats
        )
    }

    private suspend fun inspectTikTok(url: String, onStatus: (String) -> Unit): PlatformInfo {
        onStatus("Fetching TikTok video info...")
        // tikwm dulu (metadata cepat); kalau diblokir/rate-limited → fallback yt-dlp.
        val info = TikTokExtractor.extract(url).getOrElse { tikwmError ->
            onStatus("TikTok API blocked — retrying with yt-dlp...")
            val dlp = YtDlpRunner.getVideoInfo(context, url).getOrElse { dlpError ->
                val detail = tikwmError.message ?: "public API failed"
                val dlpDetail = dlpError.message ?: "failed"
                throw Exception(
                    "TikTok is temporarily unavailable — $detail (yt-dlp: $dlpDetail). Try again in a few minutes."
                )
            }
            val audio = dlp.formats.filter { !it.hasVideo && it.hasAudio }.maxByOrNull { it.bitrate }
            TikTokExtractor.TikTokInfo(
                title = dlp.title.ifBlank { "TikTok Video" },
                duration = dlp.duration,
                videoUrl = "",
                videoNoWmUrl = "",
                audioUrl = audio?.url,
                coverUrl = dlp.thumbnail.ifBlank { null },
                author = dlp.uploader.ifBlank { null }
            )
        }

        val formats = mutableListOf<FormatChoice>()

        // directUrl membuat unduhan melewati yt-dlp (CDN tikwm langsung)
        formats.add(FormatChoice(
            id = "tiktok_video",
            label = "Video (No Watermark)",
            type = "video",
            ext = "mp4",
            quality = null,
            sizeBytes = null,
            directUrl = info.videoNoWmUrl.ifEmpty { null }
        ))

        if (info.audioUrl != null) {
            formats.add(FormatChoice(
                id = "tiktok_audio",
                label = "Audio / Music",
                type = "audio",
                ext = "mp3",
                quality = null,
                sizeBytes = null,
                directUrl = info.audioUrl
            ))
        }

        return PlatformInfo(
            platform = Platform.TIKTOK,
            title = info.title,
            duration = info.duration,
            thumbnail = info.coverUrl,
            formats = formats
        )
    }

    private suspend fun inspectFacebook(url: String, onStatus: (String) -> Unit): PlatformInfo {
        onStatus("Fetching Facebook video info...")
        // Facebook makin sering menyajikan login wall ke scraper — coba page scraper
        // dulu, lalu fallback yt-dlp.
        val info = try {
            FacebookExtractor.extract(url).getOrElse { facebookFromYtDlp(url, onStatus) }
        } catch (e: Exception) {
            facebookFromYtDlp(url, onStatus)
        }

        val formats = mutableListOf<FormatChoice>()
        formats.add(FormatChoice(
            id = "facebook_video",
            label = "Video (MP4)",
            type = "video",
            ext = "mp4",
            quality = null,
            sizeBytes = null,
            directUrl = info.videoUrl.ifEmpty { null }
        ))

        return PlatformInfo(
            platform = Platform.FACEBOOK,
            title = info.title,
            duration = info.duration,
            thumbnail = info.thumbnail,
            formats = formats
        )
    }

    /** Fallback yt-dlp untuk Facebook saat halaman diblokir login wall. */
    private suspend fun facebookFromYtDlp(url: String, onStatus: (String) -> Unit): FacebookExtractor.FacebookInfo {
        onStatus("Facebook page blocked — retrying with yt-dlp...")
        val dlp = YtDlpRunner.getVideoInfo(context, url).getOrElse {
            throw Exception("Could not find video URL. The video may be private or require login. (${it.message})")
        }
        val video = dlp.formats.filter { it.hasVideo && it.ext == "mp4" }.maxByOrNull { it.height }
            ?: dlp.formats.firstOrNull { it.hasVideo }
        if (video?.url.isNullOrEmpty()) throw Exception("No video URL found")
        return FacebookExtractor.FacebookInfo(
            title = dlp.title.ifBlank { "Facebook Video" },
            videoUrl = video!!.url,
            thumbnail = dlp.thumbnail.ifBlank { null },
            duration = dlp.duration
        )
    }

    private suspend fun inspectInstagram(url: String, onStatus: (String) -> Unit): PlatformInfo {
        onStatus("Fetching Instagram video info...")
        val formats = mutableListOf<FormatChoice>()
        formats.add(FormatChoice(
            id = "instagram_video",
            label = "Video / Reel",
            type = "video",
            ext = "mp4",
            quality = null,
            sizeBytes = null
        ))
        formats.add(FormatChoice(
            id = "instagram_audio",
            label = "Audio",
            type = "audio",
            ext = "mp3",
            quality = null,
            sizeBytes = null
        ))

        return PlatformInfo(
            platform = Platform.INSTAGRAM,
            title = "Instagram Post",
            duration = null,
            thumbnail = null,
            formats = formats
        )
    }

    private suspend fun inspectThreads(url: String, onStatus: (String) -> Unit): PlatformInfo {
        onStatus("Fetching Threads video info...")

        val formats = mutableListOf<FormatChoice>()

        // Meta memaksa login untuk halaman Threads anonim dan yt-dlp tidak punya
        // extractor Threads — hanya Premium dengan cookies yang bisa di-resolve.
        val premium = PremiumManager.isPremium()
        val hasCookies = CookieLoginStore.hasCookies(context, "threads")
        val info = if (premium && hasCookies) {
            ThreadsExtractor.extract(url, CookieLoginStore.getCookiesFile(context, "threads"))
                .getOrElse { throw Exception(it.message ?: "Could not fetch Threads post.") }
        } else null

        if (info != null) {
            formats.add(FormatChoice(
                id = "threads_video",
                label = "Video (MP4)",
                type = "video",
                ext = "mp4",
                quality = null,
                sizeBytes = null,
                directUrl = info.videoUrl.ifEmpty { null }
            ))
            return PlatformInfo(
                platform = Platform.THREADS,
                title = info.title.ifBlank { "Threads Post" },
                duration = null,
                thumbnail = info.thumbnail,
                formats = formats
            )
        } else {
            // Pengguna gratis (atau Premium tanpa cookies) tetap dapat opsi placeholder
            // supaya alurnya merespons; unduhan akan menampilkan pesan butuh login.
            formats.add(FormatChoice(
                id = "threads_video",
                label = "Video",
                type = "video",
                ext = "mp4",
                quality = null,
                sizeBytes = null
            ))
            return PlatformInfo(
                platform = Platform.THREADS,
                title = if (premium) "Threads Post — add Threads cookies to download" else "Threads Post",
                duration = null,
                thumbnail = null,
                formats = formats
            )
        }
    }

    private suspend fun inspectBilibili(url: String, onStatus: (String) -> Unit): PlatformInfo {
        onStatus("Fetching Bilibili video info...")
        // Metadata langsung dari yt-dlp (judul, thumbnail, durasi, format)
        val info = YtDlpRunner.getVideoInfo(context, url).getOrThrow()

        val formats = mutableListOf<FormatChoice>()

        // Format video+audio gabungan, satu per tinggi — ID format langsung
        // sehingga unduhan tidak butuh merge ffmpeg.
        val seenVideoHeights = mutableSetOf<Int>()
        info.formats
            .filter { it.hasVideo && it.hasAudio }
            .sortedByDescending { it.height }
            .forEach { fmt ->
                val h = fmt.height
                if (h <= 0 || h in seenVideoHeights) return@forEach
                seenVideoHeights.add(h)
                formats.add(FormatChoice(
                    id = "bilibili_video_${fmt.formatId}",
                    label = "${h}p — ${fmt.ext}",
                    type = "video",
                    ext = fmt.ext,
                    quality = "${h}p",
                    sizeBytes = fmt.filesize,
                    ytDlpFormatId = fmt.formatId,
                    height = h
                ))
            }

        // Beberapa video Bilibili murni DASH (video/audio terpisah) tanpa format
        // gabungan — tawarkan satu pilihan video best-effort daripada kosong.
        if (formats.none { it.type == "video" }) {
            formats.add(FormatChoice(
                id = "bilibili_video_best",
                label = "Video (MP4)",
                type = "video",
                ext = "mp4",
                quality = null,
                sizeBytes = null,
                ytDlpFormatId = "best"
            ))
        }

        // Audio-only (dibulatkan ke 32kbps terdekat agar unik)
        val seenAudioBitrates = mutableSetOf<Int>()
        info.formats.filter { !it.hasVideo && it.hasAudio }.forEach { fmt ->
            val rounded = ((fmt.bitrate / 1000) / 32) * 32
            if (rounded <= 0 || rounded in seenAudioBitrates) return@forEach
            seenAudioBitrates.add(rounded)
            formats.add(FormatChoice(
                id = "bilibili_audio_${fmt.formatId}",
                label = "Audio — ${rounded}kbps — ${fmt.ext}",
                type = "audio",
                ext = fmt.ext,
                quality = "${rounded}kbps",
                sizeBytes = fmt.filesize,
                ytDlpFormatId = fmt.formatId,
                bitrate = fmt.bitrate
            ))
        }

        if (formats.isEmpty()) {
            throw Exception("No downloadable formats found.")
        }

        return PlatformInfo(
            platform = Platform.BILIBILI,
            title = info.title.ifBlank { "Bilibili Video" },
            duration = info.duration.takeIf { it > 0 },
            thumbnail = info.thumbnail.ifBlank { null },
            formats = formats
        )
    }

    /**
     * Mulai unduhan untuk [choice] (format terpilih). Jalur direct CDN bila ada
     * (TikTok tikwm / Facebook fbcdn — tanpa yt-dlp), selain itu yt-dlp.
     */
    fun download(
        url: String,
        type: String,
        choice: FormatChoice,
        title: String = "Video",
        onProgress: (percent: Int, indeterminate: Boolean, status: String) -> Unit,
        onComplete: (filePath: String, filename: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        onProgress(0, true, "Preparing download...")

        val callback = object : DownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int) {
                onProgress(percent, false, "Downloading... $percent%")
            }
            override fun onStatusUpdate(statusText: String) {
                onProgress(0, true, statusText)
            }
            override fun onComplete(filePath: String, filename: String) {
                onComplete(filePath, filename)
            }
            override fun onError(error: String) {
                onError("Download failed: $error")
            }
        }

        // Jalur direct CDN — lewati yt-dlp sepenuhnya
        if (choice.directUrl != null) {
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Video" }
            val filename = "$safeTitle.${choice.ext}"
            downloadManager.download(choice.directUrl, filename, callback)
            return
        }

        // Audio: bangun selector dari bitrate pilihan (paritas dengan UI lama)
        val formatId = if (type == "audio" && choice.bitrate > 0) {
            buildAudioSelector(choice.bitrate, choice.ext)
        } else {
            choice.ytDlpFormatId
        }
        downloadManager.downloadFromUrl(url, type, formatId, callback)
    }

    /** Menerjemahkan error private-content menjadi prompt upgrade untuk user gratis. */
    fun friendlyError(message: String): String {
        val lower = message.lowercase()
        val privateHints = listOf(
            "login required", "sign in", "log in", "private", "age-restricted",
            "restricted", "authentication", "logged in", "authorization"
        )
        if (!PremiumManager.isPremium() && privateHints.any { lower.contains(it) }) {
            return "🔒 That looks like private or restricted content — Premium is required to download it.\n\n($message)"
        }
        return message
    }

    private fun buildAudioSelector(bitrate: Int, ext: String): String {
        val rounded = (bitrate / 1000 / 32) * 32
        return "bestaudio[ext=$ext][abr>${rounded - 50}]/bestaudio[ext=$ext]/bestaudio"
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

    fun destroy() {
        downloadManager.cancel()
        scope.cancel()
    }
}
