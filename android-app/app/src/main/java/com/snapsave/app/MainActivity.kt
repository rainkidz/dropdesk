package com.snapsave.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import android.widget.FrameLayout
import android.view.animation.AnimationUtils
import android.view.animation.Animation
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var urlCard: MaterialCardView
    private lateinit var urlInput: TextInputEditText
    private lateinit var inspectButton: MaterialButton
    private lateinit var loadingCard: MaterialCardView
    private lateinit var loadingText: TextView
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var videoInfoCard: MaterialCardView
    private lateinit var platformChip: Chip
    private lateinit var videoTitle: TextView
    private lateinit var videoDuration: TextView
    private lateinit var videoOnlyButton: MaterialButton
    private lateinit var audioOnlyButton: MaterialButton
    private lateinit var formatCard: MaterialCardView
    private lateinit var formatTypeLabel: TextView
    private lateinit var formatRadioGroup: RadioGroup
    private lateinit var downloadButton: MaterialButton
    private lateinit var progressCard: MaterialCardView
    private lateinit var downloadProgress: LinearProgressIndicator
    private lateinit var downloadStatus: TextView
    private lateinit var completeCard: MaterialCardView
    private lateinit var completeFileName: TextView
    private lateinit var viewDownloadsButton: MaterialButton
    private lateinit var newDownloadButton: MaterialButton
    private lateinit var videoAudioButton: MaterialButton
    private lateinit var playlistButton: MaterialButton
    private lateinit var premiumSection: LinearLayout
    private lateinit var loginFacebookBtn: MaterialButton
    private lateinit var loginInstagramBtn: MaterialButton
    private lateinit var loginThreadsBtn: MaterialButton
    private lateinit var adContainer: android.widget.FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var homeScrollView: androidx.core.widget.NestedScrollView
    private lateinit var sitesScrollView: androidx.core.widget.NestedScrollView
    private lateinit var settingsScrollView: androidx.core.widget.NestedScrollView
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var prefs: SharedPreferences

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val loginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val platform = result.data?.getStringExtra("platform") ?: return@registerForActivityResult
            Toast.makeText(this, "✅ ${platform.replaceFirstChar { it.uppercase() }} login saved!", Toast.LENGTH_SHORT).show()
            updateLoginStatus()
        }
    }
    private var currentInfo: PlatformInfo? = null
    private var selectedType: String = "video"
    private var selectedFormatId: String? = null
    private var downloadManager: DownloadManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = DownloadManager(this)
        bindViews()
        setupListeners()
        setupButtonAnimations()
        checkPermissions()
        handleShareIntent(intent)

        // Initialize AdMob
        AdManager.initialize(this)
        AdManager.loadBannerAd(this, adContainer)
        AdManager.loadInterstitialAd(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null && isUrl(sharedText)) {
                urlInput.setText(sharedText.trim())
                doInspect(sharedText.trim())
            }
        }
    }

    private fun isUrl(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be") ||
                lower.contains("tiktok.com") || lower.contains("vm.tiktok.com") ||
                lower.contains("facebook.com") || lower.contains("fb.watch") ||
                lower.contains("instagram.com") || lower.contains("threads.net")
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            }
        }
    }

    private fun bindViews() {
        urlCard = findViewById(R.id.urlCard)
        urlInput = findViewById(R.id.urlInput)
        inspectButton = findViewById(R.id.inspectButton)
        loadingCard = findViewById(R.id.loadingCard)
        loadingText = findViewById(R.id.loadingText)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorText)
        videoInfoCard = findViewById(R.id.videoInfoCard)
        platformChip = findViewById(R.id.platformChip)
        videoTitle = findViewById(R.id.videoTitle)
        videoDuration = findViewById(R.id.videoDuration)
        videoOnlyButton = findViewById(R.id.videoOnlyButton)
        audioOnlyButton = findViewById(R.id.audioOnlyButton)
        formatCard = findViewById(R.id.formatCard)
        formatTypeLabel = findViewById(R.id.formatTypeLabel)
        formatRadioGroup = findViewById(R.id.formatRadioGroup)
        downloadButton = findViewById(R.id.downloadButton)
        progressCard = findViewById(R.id.progressCard)
        downloadProgress = findViewById(R.id.downloadProgress)
        downloadStatus = findViewById(R.id.downloadStatus)
        completeCard = findViewById(R.id.completeCard)
        completeFileName = findViewById(R.id.completeFileName)
        viewDownloadsButton = findViewById(R.id.viewDownloadsButton)
        newDownloadButton = findViewById(R.id.newDownloadButton)
        videoAudioButton = findViewById(R.id.videoAudioButton)
        playlistButton = findViewById(R.id.playlistButton)
        premiumSection = findViewById(R.id.premiumSection)
        loginFacebookBtn = findViewById(R.id.loginFacebookBtn)
        adContainer = findViewById(R.id.adContainer)
        loginInstagramBtn = findViewById(R.id.loginInstagramBtn)
        loginThreadsBtn = findViewById(R.id.loginThreadsBtn)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        homeScrollView = findViewById(R.id.homeScrollView)
        sitesScrollView = findViewById(R.id.sitesScrollView)
        settingsScrollView = findViewById(R.id.settingsScrollView)
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        prefs = getSharedPreferences("vidgrab_prefs", MODE_PRIVATE)
    }

    private fun setupListeners() {
        // Inspect button
        inspectButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                urlInput.error = "Enter a URL"
                return@setOnClickListener
            }
            doInspect(url)
        }

        // Keyboard action
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                inspectButton.performClick()
                true
            } else false
        }

        // Download type buttons
        videoOnlyButton.setOnClickListener { selectType("video") }
        audioOnlyButton.setOnClickListener { selectType("audio") }

        // Premium buttons
        videoAudioButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            doPremiumDownload(url, "video_audio")
        }

        playlistButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            doPremiumDownload(url, "playlist")
        }

        // Download button
        downloadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            doDownload(url, selectedType)
        }

        // View downloads
        viewDownloadsButton.setOnClickListener {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }

        // New download
        newDownloadButton.setOnClickListener {
            resetUI()
            showHome()
            bottomNavigation.selectedItemId = R.id.nav_home
        }

        // Login buttons
        loginFacebookBtn.setOnClickListener { openLogin(CookieLoginActivity.PLATFORM_FACEBOOK) }
        loginInstagramBtn.setOnClickListener { openLogin(CookieLoginActivity.PLATFORM_INSTAGRAM) }
        loginThreadsBtn.setOnClickListener { openLogin(CookieLoginActivity.PLATFORM_THREADS) }

        // Bottom Navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHome()
                    true
                }
                R.id.nav_sites -> {
                    showSites()
                    true
                }
                R.id.nav_downloads -> {
                    startActivity(Intent(this, DownloadsActivity::class.java))
                    false // Don't select, go to activity
                }
                R.id.nav_settings -> {
                    showSettings()
                    true
                }
                else -> false
            }
        }

        // Dark Mode Toggle
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        darkModeSwitch.isChecked = isDarkMode
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // Apply saved dark mode
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        updateLoginStatus()
    }

    private fun showHome() {
        homeScrollView.visibility = View.VISIBLE
        sitesScrollView.visibility = View.GONE
        settingsScrollView.visibility = View.GONE
    }

    private fun showSites() {
        homeScrollView.visibility = View.GONE
        sitesScrollView.visibility = View.VISIBLE
        settingsScrollView.visibility = View.GONE
    }

    private fun showSettings() {
        homeScrollView.visibility = View.GONE
        sitesScrollView.visibility = View.GONE
        settingsScrollView.visibility = View.VISIBLE
    }

    private fun openLogin(platform: String) {
        val url = when (platform) {
            CookieLoginActivity.PLATFORM_FACEBOOK -> CookieLoginActivity.PLATFORM_FACEBOOK
            CookieLoginActivity.PLATFORM_INSTAGRAM -> CookieLoginActivity.PLATFORM_INSTAGRAM
            CookieLoginActivity.PLATFORM_THREADS -> CookieLoginActivity.PLATFORM_THREADS
            else -> platform
        }
        val intent = Intent(this, CookieLoginActivity::class.java).apply {
            putExtra(CookieLoginActivity.EXTRA_PLATFORM, platform)
        }
        loginLauncher.launch(intent)
    }

    private fun updateLoginStatus() {
        val fbHasLogin = CookieLoginActivity.hasCookies(this, "facebook")
        val igHasLogin = CookieLoginActivity.hasCookies(this, "instagram")
        val threadsHasLogin = CookieLoginActivity.hasCookies(this, "threads")

        loginFacebookBtn.text = if (fbHasLogin) "✅ Facebook" else "Facebook"
        loginInstagramBtn.text = if (igHasLogin) "✅ Instagram" else "Instagram"
        loginThreadsBtn.text = if (threadsHasLogin) "✅ Threads" else "Threads"
    }

    private fun doInspect(url: String) {
        hideAll()
        loadingCard.visibility = View.VISIBLE
        loadingText.text = "Inspecting link..."
        inspectButton.isEnabled = false
        downloadButton.isEnabled = true

        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)

        val platform = PlatformDetector.detect(url)

        mainScope.launch {
            try {
                when (platform) {
                    Platform.YOUTUBE -> inspectYouTube(url)
                    Platform.TIKTOK -> inspectTikTok(url)
                    Platform.FACEBOOK -> inspectFacebook(url)
                    else -> showError("Platform not supported: ${platform.displayName}")
                }
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            } finally {
                loadingCard.visibility = View.GONE
                inspectButton.isEnabled = true
            }
        }
    }

    private suspend fun inspectYouTube(url: String) {
        loadingText.text = "Fetching YouTube video info..."
        val videoId = PlatformDetector.extractVideoId(url)
            ?: throw Exception("Cannot extract video ID from URL")

        val info = YouTubeExtractor.extract(this@MainActivity, videoId).getOrThrow()

        val formats = mutableListOf<FormatChoice>()
        val seenVideoHeights = mutableSetOf<Int>()
        val seenAudioBitrates = mutableSetOf<Int>()

        // Add video formats — only unique heights, clean labels
        info.formats.filter { !it.isAudioOnly }.sortedByDescending { it.height ?: 0 }.forEach { fmt ->
            val ext = getExtFromMime(fmt.mimeType)
            val h = fmt.height ?: 0
            // Skip if no height or duplicate height
            if (h <= 0 || h in seenVideoHeights) return@forEach
            seenVideoHeights.add(h)
            val fmtLabel = "${h}p — $ext"
            // Build a yt-dlp format selector: bestvideo[height<=H][ext=EXT] — video only, no audio
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

        // Add audio formats — only unique bitrates, clean labels
        info.formats.filter { it.isAudioOnly }.sortedByDescending { it.bitrate ?: 0 }.forEach { fmt ->
            val ext = getExtFromMime(fmt.mimeType)
            val abr = ((fmt.bitrate ?: 0) / 1000)
            // Skip if no bitrate or duplicate bitrate (round to nearest 32kbps)
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

        currentInfo = PlatformInfo(
            platform = Platform.YOUTUBE,
            title = info.title,
            duration = info.duration,
            thumbnail = info.thumbnail,
            formats = formats
        )

        showResult(currentInfo!!)
    }

    private suspend fun inspectTikTok(url: String) {
        loadingText.text = "Fetching TikTok video info..."
        val info = TikTokExtractor.extract(url).getOrThrow()

        val formats = mutableListOf<FormatChoice>()

        // Video format
        formats.add(FormatChoice(
            id = "tiktok_video",
            label = "Video (No Watermark)",
            type = "video",
            ext = "mp4",
            quality = null,
            sizeBytes = null
        ))

        // Audio format
        if (info.audioUrl != null) {
            formats.add(FormatChoice(
                id = "tiktok_audio",
                label = "Audio / Music",
                type = "audio",
                ext = "mp3",
                quality = null,
                sizeBytes = null
            ))
        }

        currentInfo = PlatformInfo(
            platform = Platform.TIKTOK,
            title = info.title,
            duration = info.duration,
            thumbnail = info.coverUrl,
            formats = formats
        )

        showResult(currentInfo!!)
    }

    private suspend fun inspectFacebook(url: String) {
        loadingText.text = "Fetching Facebook video info..."
        val info = FacebookExtractor.extract(url).getOrThrow()

        val formats = mutableListOf<FormatChoice>()
        formats.add(FormatChoice(
            id = "facebook_video",
            label = "Video (MP4)",
            type = "video",
            ext = "mp4",
            quality = null,
            sizeBytes = null
        ))

        currentInfo = PlatformInfo(
            platform = Platform.FACEBOOK,
            title = info.title,
            duration = info.duration,
            thumbnail = info.thumbnail,
            formats = formats
        )

        showResult(currentInfo!!)
    }

    private fun showResult(info: PlatformInfo) {
        hideAll()

        // Platform chip
        platformChip.text = info.platform.displayName
        platformChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(info.platform.color)
        platformChip.setTextColor(Color.WHITE)

        // Title & duration
        videoTitle.text = info.title ?: "Unknown title"
        videoDuration.text = formatDuration(info.duration)
        animateCardShow(videoInfoCard)

        // Show premium section (for YouTube only)
        if (info.platform == Platform.YOUTUBE) {
            premiumSection.visibility = View.VISIBLE
        } else {
            premiumSection.visibility = View.GONE
        }

        // Check formats
        if (info.formats.isEmpty()) {
            showError("No downloadable formats found.")
            return
        }

        // Show type selection
        val hasVideo = info.formats.any { it.type == "video" }
        val hasAudio = info.formats.any { it.type == "audio" }

        if (hasVideo && hasAudio) {
            selectType("video")
        } else if (hasVideo) {
            selectType("video")
        } else if (hasAudio) {
            selectType("audio")
        }
    }

    private fun selectType(type: String) {
        selectedType = type
        val info = currentInfo ?: return

        videoOnlyButton.strokeWidth = if (type == "video") 3 else 1
        audioOnlyButton.strokeWidth = if (type == "audio") 3 else 1

        val filtered = info.formats.filter { it.type == type }
        showFormats(filtered)
    }

    private fun showFormats(formats: List<FormatChoice>) {
        formatRadioGroup.removeAllViews()

        val typeLabel = if (selectedType == "audio") "Audio formats" else "Video formats"
        formatTypeLabel.text = typeLabel

        formats.forEachIndexed { index, format ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                text = buildString {
                    append(format.label)
                    format.sizeBytes?.let {
                        append(" • ")
                        append(formatFileSize(it))
                    }
                }
                textSize = 14f
                setPadding(0, 12, 0, 12)
                tag = format.ytDlpFormatId
            }
            formatRadioGroup.addView(radioButton)

            if (index == 0) {
                radioButton.isChecked = true
                selectedFormatId = format.ytDlpFormatId
            }
        }

        // Track radio button selection changes
        formatRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadio = formatRadioGroup.findViewById<RadioButton>(checkedId)
            selectedFormatId = checkedRadio?.tag as? String
        }

        animateCardShow(formatCard)
        downloadButton.isEnabled = true
    }

    private fun doPremiumDownload(url: String, premiumType: String) {
        hideAll()
        animateCardShow(progressCard)
        downloadProgress.progress = 0
        downloadProgress.isIndeterminate = true
        downloadStatus.text = "Preparing premium download..."
        downloadButton.isEnabled = false

        when (premiumType) {
            "video_audio" -> {
                // Video + Audio merge (1080p+)
                val videoFormat = selectedFormatId ?: "bestvideo[height<=1080][ext=mp4]/bestvideo[height<=1080]"
                downloadManager?.downloadMerged(url, videoFormat, "bestaudio[ext=m4a]/bestaudio", object : DownloadManager.DownloadCallback {
                    override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int) {
                        downloadProgress.isIndeterminate = false
                        downloadProgress.progress = percent
                    }
                    override fun onStatusUpdate(statusText: String) {
                        downloadStatus.text = statusText
                    }
                    override fun onComplete(filePath: String, filename: String) {
                        hideAll()
                        animateCardShow(completeCard)
                        completeFileName.text = "📁 $filename\nSaved to: Downloads/SnapSave/"
                        Toast.makeText(this@MainActivity, "✅ Premium download complete!", Toast.LENGTH_LONG).show()
                    }
                    override fun onError(error: String) {
                        showError("Premium download failed: $error")
                    }
                })
            }
            "playlist" -> {
                // Playlist download
                downloadManager?.downloadPlaylist(url, "bestvideo[height<=720]+bestaudio/bestvideo+bestaudio/best", 50, object : DownloadManager.DownloadCallback {
                    override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int) {
                        downloadProgress.isIndeterminate = false
                        downloadProgress.progress = percent
                    }
                    override fun onStatusUpdate(statusText: String) {
                        downloadStatus.text = statusText
                    }
                    override fun onComplete(filePath: String, filename: String) {
                        hideAll()
                        animateCardShow(completeCard)
                        completeFileName.text = "📁 Playlist downloaded\nSaved to: Downloads/SnapSave/"
                        Toast.makeText(this@MainActivity, "✅ Playlist download complete!", Toast.LENGTH_LONG).show()
                    }
                    override fun onError(error: String) {
                        showError("Playlist download failed: $error")
                    }
                })
            }
        }
    }

    private fun doDownload(url: String, type: String) {
        val info = currentInfo
        val platform = info?.platform ?: PlatformDetector.detect(url)

        hideAll()
        animateCardShow(progressCard)
        downloadProgress.progress = 0
        downloadProgress.isIndeterminate = true
        downloadStatus.text = "Preparing download..."
        downloadButton.isEnabled = false

        downloadManager?.downloadFromUrl(url, type, selectedFormatId, object : DownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int) {
                downloadProgress.isIndeterminate = false
                downloadProgress.progress = percent
            }

            override fun onStatusUpdate(statusText: String) {
                downloadStatus.text = statusText
            }

            override fun onComplete(filePath: String, filename: String) {
                // Show interstitial ad after download completes
                AdManager.showInterstitialAd(this@MainActivity,
                    onAdDismissed = {
                        hideAll()
                        animateCardShow(completeCard)
                        completeFileName.text = "📁 $filename\nSaved to: Downloads/SnapSave/"
                        Toast.makeText(this@MainActivity, "✅ Saved to Downloads/SnapSave/", Toast.LENGTH_LONG).show()
                    },
                    onAdFailed = {
                        hideAll()
                        animateCardShow(completeCard)
                        completeFileName.text = "📁 $filename\nSaved to: Downloads/SnapSave/"
                        Toast.makeText(this@MainActivity, "✅ Saved to Downloads/SnapSave/", Toast.LENGTH_LONG).show()
                    }
                )
            }

            override fun onError(error: String) {
                showError("Download failed: $error")
            }
        })
    }

    // ── UI Helpers ───────────────────────────────────────────

    private fun showError(message: String) {
        hideAll()
        errorCard.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun hideAll() {
        loadingCard.visibility = View.GONE
        errorCard.visibility = View.GONE
        videoInfoCard.visibility = View.GONE
        formatCard.visibility = View.GONE
        progressCard.visibility = View.GONE
        completeCard.visibility = View.GONE
    }

    private fun resetUI() {
        hideAll()
        urlInput.text?.clear()
        currentInfo = null
        selectedType = "video"
        selectedFormatId = null
        downloadButton.isEnabled = true
        urlInput.requestFocus()
    }

    // ── Animations ───────────────────────────────────────────

    private fun animateCardShow(card: MaterialCardView) {
        val showAnim = AnimationUtils.loadAnimation(this, R.anim.card_show)
        card.startAnimation(showAnim)
        card.visibility = View.VISIBLE
    }

    private fun animateCardHide(card: MaterialCardView) {
        card.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(200)
            .withEndAction {
                card.visibility = View.GONE
                card.alpha = 1f
                card.scaleX = 1f
                card.scaleY = 1f
            }
            .start()
    }

    private fun setupButtonAnimations() {
        // Apply subtle press animation to all MaterialButtons
        listOf(
            inspectButton, videoOnlyButton, audioOnlyButton,
            downloadButton, videoAudioButton, playlistButton,
            viewDownloadsButton, newDownloadButton
        ).forEach { button ->
            button.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(80)
                            .start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(80)
                            .start()
                    }
                }
                false // Let click pass through
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────

    private fun formatDuration(seconds: Double?): String {
        if (seconds == null || seconds <= 0) return ""
        val totalSeconds = seconds.toInt()
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        return "%d:%02d".format(minutes, secs)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
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

    override fun onDestroy() {
        super.onDestroy()
        downloadManager?.cancel()
        mainScope.cancel()
        AdManager.destroyBannerAd(adContainer)
    }
}
