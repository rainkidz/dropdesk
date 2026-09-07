package com.tubenime.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
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
import android.widget.ImageButton
import android.widget.ViewFlipper
import android.view.animation.AnimationUtils
import android.view.animation.Animation
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore
import android.os.Environment
import java.io.File
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.MenuItem
import com.google.android.material.snackbar.Snackbar
import coil.load
import coil.transform.RoundedCornersTransformation

class MainActivity : AppCompatActivity() {

    private lateinit var urlCard: MaterialCardView
    private lateinit var urlInput: TextInputEditText
    private lateinit var pasteShortcut: TextView
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
    private lateinit var welcomeCard: MaterialCardView
    private lateinit var noticeFlipper: ViewFlipper
    private lateinit var noticeDots: LinearLayout
    private lateinit var prefs: SharedPreferences
    private lateinit var queueButton: MaterialButton
    private lateinit var premiumHint: TextView

    // Ad-load guards so onResume()/listener refreshes don't stack duplicate ad requests.
    private var bannerLoadedForSession = false
    private var interstitialLoadedForSession = false

    private val premiumListener = object : PremiumManager.Listener {
        override fun onPremiumChanged() {
            runOnUiThread { refreshPremiumUI() }
        }
    }

    // ── Welcome notice carousel state ─────────────────────────
    private var noticeIndex = 0
    private var noticeTicker: Job? = null
    private var noticeList: List<NoticeRepository.Notice> = emptyList()
    private val noticeUrlMap = mutableMapOf<Int, String>()

    private val noticeGestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                noticeUrlMap[noticeIndex]?.let { openNoticeLink(it) }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val startX = e1?.x ?: return false
                val dx = e2.x - startX
                if (kotlin.math.abs(velocityX) > 400 && kotlin.math.abs(dx) > 80) {
                    if (dx < 0) showNotice(noticeIndex + 1, announce = true)
                    else showNotice(noticeIndex - 1, announce = true)
                    return true
                }
                return false
            }
        })
    }

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
    private lateinit var downloadQueue: DownloadQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = DownloadManager(this)
        downloadQueue = DownloadQueue.getInstance(this)
        
        // Initialize notification helper
        NotificationHelper.init(this)
        
        bindViews()
        setupListeners()
        trackUsageAndGreeting()

        // Keep the banner hidden if the user already dismissed it for this app version,
        // or turned home notifications off in Settings
        val showNotices = prefs.getBoolean(SettingsActivity.KEY_SHOW_HOME_NOTICES, true)
        if (!showNotices || prefs.getBoolean(WELCOME_DISMISS_KEY, false)) {
            welcomeCard.visibility = View.GONE
        }

        // Refresh notices in the background (12h throttle, falls back to cache/defaults)
        mainScope.launch {
            NoticeRepository.refresh(this@MainActivity)?.let { fresh ->
                noticeList = fresh
                if (welcomeCard.isVisible) rebuildNoticeCarousel()
            }
        }

        setupButtonAnimations()
        checkPermissions()
        handleShareIntent(intent)

        // Premium entitlement + AdMob. refreshPremiumUI loads/hides ads per tier.
        PremiumManager.init(this)
        PremiumManager.addListener(premiumListener)
        AdManager.initialize(this)
        refreshPremiumUI()
        // Play Integrity attestation (only active when an enforcement server is configured).
        PremiumManager.runInstallAttestation(this)
    }

    override fun onResume() {
        super.onResume()
        PremiumManager.refreshPurchases()
        refreshPremiumUI()
        if (::noticeFlipper.isInitialized) {
            // Re-show the banner if Settings re-enabled home notifications
            val showNotices = prefs.getBoolean(SettingsActivity.KEY_SHOW_HOME_NOTICES, true)
            val dismissed = prefs.getBoolean(WELCOME_DISMISS_KEY, false)
            if (showNotices && !dismissed && welcomeCard.visibility != View.VISIBLE) {
                welcomeCard.visibility = View.VISIBLE
            }
            if (welcomeCard.visibility == View.VISIBLE) {
                startNoticeTicker()
            }
        }
        checkClipboardForUrl()
    }

    override fun onPause() {
        stopNoticeTicker()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null && isUrl(sharedText)) {
                // Make sure we're on the Home tab
                showHome()
                bottomNavigation.selectedItemId = R.id.nav_home
                // Set URL and inspect
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
                lower.contains("instagram.com") || lower.contains("threads.net") ||
                lower.contains("x.com") || lower.contains("twitter.com") ||
                lower.contains("bilibili.com")
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            }
        }
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun bindViews() {
        urlCard = findViewById(R.id.urlCard)
        urlInput = findViewById(R.id.urlInput)
        pasteShortcut = findViewById(R.id.pasteShortcut)
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
        welcomeCard = findViewById(R.id.welcomeCard)
        noticeFlipper = findViewById(R.id.noticeFlipper)
        noticeDots = findViewById(R.id.noticeDots)
        prefs = getSharedPreferences("tubenime_prefs", MODE_PRIVATE)
        queueButton = findViewById(R.id.queueButton)
        premiumHint = findViewById(R.id.premiumHint)

        noticeFlipper.setInAnimation(this, R.anim.notice_slide_in_right)
        noticeFlipper.setOutAnimation(this, R.anim.notice_slide_out_left)
    }

    private fun setupListeners() {
        // Welcome banner: close button, swipe gestures, tap actions, page dots
        findViewById<ImageButton>(R.id.welcomeCloseButton).setOnClickListener {
            prefs.edit().putBoolean(WELCOME_DISMISS_KEY, true).apply()
            stopNoticeTicker()
            animateCardHide(welcomeCard)
        }
        setupNoticeCarousel()

        // Inspect button
        pasteShortcut.setOnClickListener { pasteFromClipboard() }

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

        // Premium buttons — locked (with upsell) unless the user is premium.
        videoAudioButton.setOnClickListener {
            if (!guardPremiumAction()) return@setOnClickListener
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            doPremiumDownload(url, "video_audio")
        }

        playlistButton.setOnClickListener {
            if (!guardPremiumAction()) return@setOnClickListener
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            doPremiumDownload(url, "playlist")
        }

        // Batch queue (Premium)
        queueButton.setOnClickListener { addCurrentToQueue() }

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
                R.id.nav_browser -> {
                    startActivity(Intent(this, BrowserActivity::class.java))
                    false // Don't select, go to activity
                }
                R.id.nav_downloads -> {
                    startActivity(Intent(this, DownloadsActivity::class.java))
                    false // Don't select, go to activity
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }

        // Apply saved dark mode
        val nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // Toolbar actions
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_downloads -> {
                    startActivity(Intent(this, DownloadsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Move login section to bottom of home, visible only when needed
        val loginCard = findViewById<MaterialCardView>(R.id.loginCard)
        loginCard.visibility = View.GONE

        updateLoginStatus()

        // Quick Download platform click handlers
        val platformUrls = mapOf(
            R.id.quickYouTube to "https://m.youtube.com",
            R.id.quickTikTok to "https://www.tiktok.com",
            R.id.quickInstagram to "https://www.instagram.com",
            R.id.quickBilibili to "https://www.bilibili.com",
            R.id.quickFacebook to "https://m.facebook.com",
            R.id.quickThreads to "https://www.threads.net",
            R.id.animeYouTubeBtn to "https://m.youtube.com/results?search_query=anime+opening+ending",
            R.id.animeBilibiliBtn to "https://www.bilibili.com/anime"
        )
        for ((id, url) in platformUrls) {
            findViewById<View>(id).setOnClickListener {
                val intent = Intent(this, BrowserActivity::class.java)
                intent.putExtra(BrowserActivity.EXTRA_URL, url)
                startActivity(intent)
            }
        }
    }

    private fun showHome() {
        homeScrollView.visibility = View.VISIBLE
    }

    // ── Welcome Notice Carousel ────────────────────────────

    private fun setupNoticeCarousel() {
        noticeList = NoticeRepository.load(this)
        rebuildNoticeCarousel()

        // Swipe + tap handling on the flipper
        noticeFlipper.setOnTouchListener { _, event ->
            noticeGestureDetector.onTouchEvent(event)
            true
        }
    }

    /** (Re)builds the flipper slides + page dots from [noticeList]. */
    private fun rebuildNoticeCarousel() {
        // Detach animations so the rebuild itself doesn't animate
        noticeFlipper.setInAnimation(null)
        noticeFlipper.setOutAnimation(null)
        noticeFlipper.removeAllViews()
        noticeUrlMap.clear()

        val inflater = android.view.LayoutInflater.from(this)
        noticeList.forEachIndexed { index, notice ->
            val slide = inflater.inflate(R.layout.item_notice_slide, noticeFlipper, false)
            slide.findViewById<TextView>(R.id.noticeIcon).text = notice.icon
            slide.findViewById<TextView>(R.id.noticeTitle).text = notice.title
            slide.findViewById<TextView>(R.id.noticeBody).text = notice.body
            if (notice.url != null) {
                noticeUrlMap[index] = notice.url
                slide.setOnClickListener { openNoticeLink(notice.url) }
            }
            noticeFlipper.addView(slide)
        }

        // Page dots to match the slide count
        val dp = resources.displayMetrics.density
        noticeDots.removeAllViews()
        repeat(noticeList.size) { index ->
            val dot = View(this)
            val lp = LinearLayout.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
            lp.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            dot.layoutParams = lp
            dot.setBackgroundResource(if (index == 0) R.drawable.notice_dot_active else R.drawable.notice_dot_inactive)
            dot.setOnClickListener { showNotice(index, announce = true) }
            noticeDots.addView(dot)
        }

        noticeIndex = 0
        noticeFlipper.displayedChild = 0
        noticeFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.notice_slide_in_right))
        noticeFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.notice_slide_out_left))
    }

    private fun showNotice(index: Int, announce: Boolean = false) {
        val slides = noticeFlipper.childCount
        if (slides == 0) return
        noticeIndex = ((index % slides) + slides) % slides
        noticeFlipper.displayedChild = noticeIndex
        for (i in 0 until noticeDots.childCount) {
            noticeDots.getChildAt(i).setBackgroundResource(
                if (i == noticeIndex) R.drawable.notice_dot_active else R.drawable.notice_dot_inactive
            )
        }
        if (announce && noticeUrlMap.containsKey(noticeIndex)) {
            Toast.makeText(this, "💡 Tip — tap for actions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNoticeTicker() {
        if (noticeTicker?.isActive == true) return
        noticeTicker = mainScope.launch {
            while (isActive && welcomeCard.visibility == View.VISIBLE) {
                delay(5000)
                showNotice(noticeIndex + 1)
            }
        }
    }

    private fun stopNoticeTicker() {
        noticeTicker?.cancel()
        noticeTicker = null
    }

    private fun openNoticeLink(url: String) {
        val intent = Intent(this, BrowserActivity::class.java)
        intent.putExtra(BrowserActivity.EXTRA_URL, url)
        startActivity(intent)
    }

    // ── Contextual Greeting & Weekly Count ─────────────────

    /** Counts TubeNime downloads from MediaStore within the last 7 days. */
    private fun countWeeklyDownloads(): Int {
        return try {
            val weekAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 60 * 60
            var count = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DATE_MODIFIED} >= ?",
                    arrayOf("%TubeNime%", weekAgo.toString()),
                    null
                )?.use { cursor -> count = cursor.count }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "TubeNime"
                )
                count = dir.listFiles()?.count {
                    it.isFile && it.lastModified() >= weekAgo * 1000
                } ?: 0
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Personalizes the banner: returning users get "Welcome back" + a weekly
     * download-count slide prepended to the carousel; first launch keeps the
     * plain welcome title and default notices.
     */
    private fun trackUsageAndGreeting() {
        val isFirstLaunch = !prefs.contains("first_launch_ms")
        if (isFirstLaunch) {
            prefs.edit().putLong("first_launch_ms", System.currentTimeMillis()).apply()
            findViewById<TextView>(R.id.welcomeTitle).text = getString(R.string.welcome_title)
            return
        }

        findViewById<TextView>(R.id.welcomeTitle).text = getString(R.string.welcome_back_title)

        val weekly = countWeeklyDownloads()
        val notices = noticeList.ifEmpty { NoticeRepository.load(this) }.toMutableList()
        val body = when {
            weekly > 0 -> "You grabbed $weekly download${if (weekly == 1) "" else "s"} this week — keep the binge going!"
            else -> "No downloads this week yet — copy an anime link and we'll grab it for you."
        }
        notices.add(0, NoticeRepository.Notice(icon = "📊", title = "This week", body = body))
        noticeList = notices
        rebuildNoticeCarousel()
    }

    // ── Clipboard Helpers ──────────────────────────────────────

    private fun readClipboardUrl(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text?.toString() ?: return null
        return if (isUrl(text)) text.trim() else null
    }

    private fun pasteFromClipboard() {
        val url = readClipboardUrl()
        if (url != null) {
            urlInput.setText(url)
            // Scroll to top so user sees the URL card
            homeScrollView.smoothScrollTo(0, 0)
            doInspect(url)
            Toast.makeText(this, "📋 Pasted from clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No video link in clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkClipboardForUrl() {
        val url = readClipboardUrl() ?: return
        // Don't show if the URL is already in the input
        if (urlInput.text.toString().trim() == url) return
        // Don't show if we're already inspecting/downloading
        if (loadingCard.visibility == View.VISIBLE) return
        if (videoInfoCard.visibility == View.VISIBLE) return
        if (progressCard.visibility == View.VISIBLE) return

        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, "📋 Link detected — paste & inspect?", Snackbar.LENGTH_LONG)
            .setAction("Paste") {
                urlInput.setText(url)
                homeScrollView.smoothScrollTo(0, 0)
                doInspect(url)
            }
            .show()
    }

    private fun openLogin(platform: String) {
        // Private content (cookie login) is a Premium feature.
        if (!guardPremiumAction()) return
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
                    Platform.INSTAGRAM -> inspectInstagram(url)
                    Platform.THREADS -> inspectThreads(url)
                    Platform.BILIBILI -> inspectBilibili(url)
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
        // tikwm first (fast metadata); when it is blocked/rate-limited it returns
        // HTML and fails — fall back to yt-dlp extraction for the metadata.
        val info = TikTokExtractor.extract(url).getOrElse { tikwmError ->
            loadingText.text = "TikTok API blocked — retrying with yt-dlp..."
            val dlp = YtDlpRunner.getVideoInfo(this, url).getOrElse { dlpError ->
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

    private suspend fun inspectInstagram(url: String) {
        loadingText.text = "Fetching Instagram video info..."
        // Use yt-dlp to extract Instagram video info
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

        currentInfo = PlatformInfo(
            platform = Platform.INSTAGRAM,
            title = "Instagram Post",
            duration = null,
            thumbnail = null,
            formats = formats
        )

        showResult(currentInfo!!)
    }

    private suspend fun inspectThreads(url: String) {
        loadingText.text = "Fetching Threads video info..."
        val formats = mutableListOf<FormatChoice>()
        formats.add(FormatChoice(
            id = "threads_video",
            label = "Video",
            type = "video",
            ext = "mp4",
            quality = null,
            sizeBytes = null
        ))
        formats.add(FormatChoice(
            id = "threads_audio",
            label = "Audio",
            type = "audio",
            ext = "mp3",
            quality = null,
            sizeBytes = null
        ))

        currentInfo = PlatformInfo(
            platform = Platform.THREADS,
            title = "Threads Post",
            duration = null,
            thumbnail = null,
            formats = formats
        )

        showResult(currentInfo!!)
    }

    private suspend fun inspectBilibili(url: String) {
        loadingText.text = "Fetching Bilibili video info..."
        // Real metadata straight from yt-dlp (title, thumbnail, duration, formats)
        val info = YtDlpRunner.getVideoInfo(this@MainActivity, url).getOrThrow()

        val formats = mutableListOf<FormatChoice>()

        // Combined video+audio formats, one per height — direct format IDs so the
        // download needs no ffmpeg merge step.
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

        // Some Bilibili videos are pure DASH (separate video/audio streams) with no
        // combined format — offer a single best-effort video choice instead of none.
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

        // Audio-only formats (rounded to the nearest 32kbps to avoid duplicates)
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
            showError("No downloadable formats found.")
            return
        }

        currentInfo = PlatformInfo(
            platform = Platform.BILIBILI,
            title = info.title.ifBlank { "Bilibili Video" },
            duration = info.duration.takeIf { it > 0 },
            thumbnail = info.thumbnail.ifBlank { null },
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

        // Load video thumbnail
        val thumbnailView = findViewById<ImageView>(R.id.videoThumbnail)
        if (info.thumbnail != null) {
            thumbnailView.load(info.thumbnail) {
                crossfade(true)
                transformations(RoundedCornersTransformation(16f))
                placeholder(R.drawable.ic_launcher_foreground)
                error(R.drawable.ic_launcher_foreground)
            }
            thumbnailView.visibility = View.VISIBLE
        } else {
            thumbnailView.visibility = View.GONE
        }
        animateCardShow(videoInfoCard)

        // Premium actions are available for every platform. Playlists only make
        // sense where yt-dlp can enumerate them (YouTube for now).
        premiumSection.visibility = View.VISIBLE
        playlistButton.visibility = if (info.platform == Platform.YOUTUBE) View.VISIBLE else View.GONE
        updatePremiumSectionState()

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

        // Free tier only sees formats within the free limits (720p / 128kbps).
        val visibleFormats = TierRules.filterFormats(PremiumManager.isPremium(), formats)
        visibleFormats.forEachIndexed { index, format ->
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
                // Video + Audio merge (1080p+). Honour the user's chosen resolution,
                // so premium users who picked a 4K radio get a 2160p merge.
                val videoFormat = mergeVideoFormatFromSelection()
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
                        completeFileName.text = "📁 $filename\nSaved to: Downloads/TubeNime/"
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
                        completeFileName.text = "📁 Playlist downloaded\nSaved to: Downloads/TubeNime/"
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
                        completeFileName.text = "📁 $filename\nSaved to: Downloads/TubeNime/"
                        Toast.makeText(this@MainActivity, "✅ Saved to Downloads/TubeNime/", Toast.LENGTH_LONG).show()
                    },
                    onAdFailed = {
                        hideAll()
                        animateCardShow(completeCard)
                        completeFileName.text = "📁 $filename\nSaved to: Downloads/TubeNime/"
                        Toast.makeText(this@MainActivity, "✅ Saved to Downloads/TubeNime/", Toast.LENGTH_LONG).show()
                    }
                )
            }

            override fun onError(error: String) {
                showError("Download failed: $error")
            }
        })
    }

    // ── Premium helpers ──────────────────────────────────────

    /** Refreshes everything that depends on the premium tier (ads, buttons, lists). */
    private fun refreshPremiumUI() {
        val premium = PremiumManager.isPremium()

        // Ads: only free users see them.
        AdManager.setAdsEnabled(!premium)
        if (premium) {
            adContainer.removeAllViews()
            adContainer.visibility = View.GONE
            // Reset so ads reload once if the user ever switches back to free.
            bannerLoadedForSession = false
            interstitialLoadedForSession = false
        } else {
            adContainer.visibility = View.VISIBLE
            if (!bannerLoadedForSession) {
                AdManager.loadBannerAd(this, adContainer)
                bannerLoadedForSession = true
            }
            if (!interstitialLoadedForSession) {
                AdManager.loadInterstitialAd(this)
                interstitialLoadedForSession = true
            }
        }

        if (::queueButton.isInitialized) {
            queueButton.visibility = if (premium) View.VISIBLE else View.GONE
        }
        if (::premiumHint.isInitialized) {
            premiumHint.text = if (premium) {
                "✅ Premium active — everything unlocked."
            } else {
                "Free plan: up to 720p & 128kbps with ads. Go Premium for 1080p+/4K with audio, playlists, private posts, batch downloads & no ads."
            }
        }

        if (::videoAudioButton.isInitialized && premiumSection.visibility == View.VISIBLE) {
            updatePremiumSectionState()
            // Re-render the current format list so locked resolutions appear/disappear.
            val info = currentInfo ?: return
            if (formatCard.visibility == View.VISIBLE) {
                val tierFormats = TierRules.filterFormats(premium, info.formats).filter { it.type == selectedType }
                if (tierFormats.isNotEmpty()) showFormats(tierFormats)
            }
        }
    }

    /** Lock icons + plan hint on the premium buttons. */
    private fun updatePremiumSectionState() {
        val premium = PremiumManager.isPremium()
        videoAudioButton.text = (if (!premium) "🔒 " else "") + "🎬+🎵 Video + Audio (1080p+)"
        playlistButton.text = (if (!premium) "🔒 " else "") + "📋 Playlist Download"
        if (::premiumHint.isInitialized) {
            premiumHint.text = if (premium) {
                "✅ Premium active — everything unlocked."
            } else {
                "Free plan caps videos at 720p (128kbps audio). Premium: 1080p+/4K with audio, playlists, private posts, batch queue, no ads."
            }
        }
    }

    /** Premium gate: returns true when the action may proceed. */
    private fun guardPremiumAction(): Boolean {
        if (PremiumManager.isPremium()) return true
        Toast.makeText(this, "That's a Premium feature — upgrade to unlock it.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, PremiumActivity::class.java))
        return false
    }

    /** Adds the current selection to the shared batch queue (Premium). */
    private fun addCurrentToQueue() {
        if (!guardPremiumAction()) return
        val info = currentInfo ?: return
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) return
        val item = downloadQueue.addToQueue(url, info.title ?: "Video", selectedType, selectedFormatId)
        Snackbar.make(findViewById(android.R.id.content), "➕ #${item.id} added to batch queue", Snackbar.LENGTH_LONG)
            .setAction("Open") {
                startActivity(Intent(this, DownloadsQueueActivity::class.java))
            }
            .show()
    }

    /** Builds the video half of a merged download from the user's chosen radio. */
    private fun mergeVideoFormatFromSelection(): String {
        val chosen = selectedFormatId
        if (!chosen.isNullOrEmpty() && chosen.contains("bestvideo")) return chosen
        return "bestvideo[height<=1080][ext=mp4]/bestvideo[height<=1080]"
    }

    /** Translates private-content errors into an upgrade prompt for free users. */
    private fun friendlyError(message: String): String {
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

    // ── UI Helpers ───────────────────────────────────────────

    private fun showError(message: String) {
        hideAll()
        errorCard.visibility = View.VISIBLE
        errorText.text = friendlyError(message)
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
        // Bring the welcome/notice carousel back to its first tip
        if (welcomeCard.visibility == View.VISIBLE) showNotice(0)
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
        PremiumManager.removeListener(premiumListener)
        AdManager.destroyBannerAd(adContainer)
    }

    companion object {
        /** Bumped per app version so new notifications can re-appear after dismissal. */
        const val WELCOME_DISMISS_KEY = "welcome_notices_dismissed_v4.3"
    }
}
