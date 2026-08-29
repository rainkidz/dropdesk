package com.snapsave.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var urlInput: TextInputEditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var fabDownload: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var homepageGrid: View

    private var currentUrl = ""
    private var detectedVideoUrls = mutableListOf<String>()
    private var isHomepage = true
    private val adBlockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val adBlockRunnable = object : Runnable {
        override fun run() {
            if (!isHomepage && !isFinishing) {
                injectAdBlockJs()
            }
            adBlockHandler.postDelayed(this, 3000) // Run every 3 seconds
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PLATFORM = "extra_platform"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        urlInput = findViewById(R.id.urlInput)
        goButton = findViewById(R.id.goButton)
        backButton = findViewById(R.id.backButton)
        fabDownload = findViewById(R.id.fabDownload)
        homepageGrid = findViewById(R.id.homepageGrid)

        setupWebView()
        setupListeners()
        setupPlatformCards()
        adBlockHandler.postDelayed(adBlockRunnable, 3000) // Start periodic ad removal

        // Check if opened with a URL from intent
        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (!initialUrl.isNullOrEmpty()) {
            loadUrl(initialUrl)
        }
    }

    // ── Ad domains to block ──────────────────────────────────────
    private val adDomains = setOf(
        // Google Ads
        "pagead2.googlesyndication.com",
        "googleadservices.com",
        "www.googleadservices.com",
        "adservice.google.com",
        "doubleclick.net",
        "*.doubleclick.net",
        "tpc.googlesyndication.com",
        "www-doubleclick-net.cdn.ampproject.org",
        "googletagmanager.com",
        "googletagservices.com",
        "googlesyndication.com",
        // YouTube ads
        "youtube.com/api/stats/ads",
        "youtube.com/get_video_info&ei=",
        "s.ytimg.com/yts/jsbin/",
        "static.doubleclick.net",
        "ad.doubleclick.net",
        // Facebook / Instagram ads
        "an.facebook.com",
        "pixel.facebook.com",
        "www.facebook.com/tr",
        // Generic ad networks
        "ads-twitter.com",
        "analytics.twitter.com",
        "ad.doubleclick.net",
        "ad.turn.com",
        "advertising.com",
        "adnxs.com",
        "adsrvr.org",
        "casalemedia.com",
        "contextweb.com",
        "demdex.net",
        "everesttech.net",
        "indeed.com/ads",
        "taboola.com",
        "outbrain.com",
        "moatads.com",
        "serving-sys.com",
        "smaato.net",
        "unity3d.com/ads",
        "applovin.com",
        "inmobi.com",
        "vungle.com",
        "chartboost.com"
    )

    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        return adDomains.any { domain ->
            if (domain.startsWith("*.")) {
                lower.contains(domain.removePrefix("*."))
            } else {
                lower.contains(domain)
            }
        }
    }

    // ── Detect current platform from URL ───────────────────────
    private fun detectPlatform(url: String): Platform {
        return PlatformDetector.detect(url)
    }

    // ── CSS to hide ad elements — platform-specific ───────────
    private fun getAdBlockCss(): String {
        val platform = detectPlatform(currentUrl)
        return when (platform) {
            Platform.YOUTUBE -> getYouTubeCss()
            Platform.TIKTOK -> getTikTokCss()
            Platform.INSTAGRAM -> getInstagramCss()
            Platform.FACEBOOK -> getFacebookCss()
            else -> getGenericCss()
        }
    }

    private fun getYouTubeCss(): String {
        return """
            /* YouTube ad containers */
            ytd-ad-slot-renderer,
            ytd-promoted-sparkles-web-renderer,
            ytd-display-ad-renderer,
            ytd-video-masthead-ad-ad-slot-renderer,
            ytd-in-feed-ad-renderer,
            ytd-ad-banner-renderer,
            ytd-statement-banner-renderer,
            #player-ads,
            #ad-container,
            .ytp-ad-overlay-container,
            .ytp-ad-text-overlay,
            .ytp-ad-image-overlay,
            .ytp-ad-overlay-slot,
            .video-ads,
            .ytp-ad-module,
            ytd-rich-section-renderer:has(ytd-statement-banner-renderer),
            /* Sponsored content */
            ytd-rich-section-renderer:has([href*='promoted']),
            /* YouTube premium upsell */
            ytd-mealbar-promo-renderer,
            /* Bottom banner ads */
            .ytp-ad-bottomlink-section,
            /* YouTube notifications popup */
            ytd-popup-container,
            /* Channel premium upsell */
            ytd-membership-pill-renderer
        """.trimIndent()
    }

    private fun getTikTokCss(): String {
        return """
            /* TikTok download prompt */
            [data-e2e='top-tab-create'],
            /* TikTok cookie banner */
            [class*='DivCookieBanner'],
            /* TikTok login wall */
            [class*='DivLoginBanner'],
            /* TikTok bottom nav */
            [class*='DivFooter'],
            /* Generic ad elements */
            [id*='google_ads'],
            [class*='ad-container'],
            iframe[src*='doubleclick']
        """.trimIndent()
    }

    private fun getInstagramCss(): String {
        return """
            /* Instagram login wall */
            [class*='x1n2onr6'][class*='x78zumc'],
            /* Instagram bottom bar */
            [role='navigation'],
            /* Instagram story tray on web */
            [class*='x9f619'] > div:first-child,
            /* Generic ad elements */
            [data-testid='ad-banner'],
            [id*='google_ads'],
            iframe[src*='doubleclick']
        """.trimIndent()
    }

    private fun getFacebookCss(): String {
        return """
            /* Facebook sponsored posts */
            [aria-label='Sponsored'],
            [data-testid='fbfeed_story'],
            /* Facebook story ads */
            [class*='_9Ag-'],
            /* Facebook reels ads */
            [class*='x1dr75xp'],
            /* Generic ad elements */
            [id*='google_ads'],
            [class*='ad-container'],
            iframe[src*='doubleclick'],
            iframe[src*='googlesyndication']
        """.trimIndent()
    }

    private fun getGenericCss(): String {
        return """
            /* Generic ad elements */
            [id*='google_ads'],
            [id*='ad-slot'],
            [class*='ad-container'],
            [class*='advertisement'],
            [data-ad],
            iframe[src*='doubleclick'],
            iframe[src*='googlesyndication'],
            iframe[src*='googleads']
        """.trimIndent()
    }

    private fun injectAdBlockCss() {
        val css = getAdBlockCss()
        val js = """
            (function() {
                var style = document.createElement('style');
                style.id = 'vidgrab-adblock';
                style.textContent = '$css';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectAdBlockJs() {
        val platform = detectPlatform(currentUrl)
        val platformJs = when (platform) {
            Platform.YOUTUBE -> getYoutubeAdBlockJs()
            Platform.TIKTOK -> getTikTokAdBlockJs()
            Platform.INSTAGRAM -> getInstagramAdBlockJs()
            Platform.FACEBOOK -> getFacebookAdBlockJs()
            else -> getGenericAdBlockJs()
        }
        webView.evaluateJavascript(platformJs, null)
    }

    private fun getYoutubeAdBlockJs(): String {
        return """
            (function() {
                // Remove ad iframes
                document.querySelectorAll('iframe').forEach(function(f) {
                    var src = (f.src || '').toLowerCase();
                    if (src.includes('doubleclick') || src.includes('googlesyndication') ||
                        src.includes('googleads')) {
                        f.remove();
                    }
                });

                // Remove ad containers
                document.querySelectorAll('[id*="google_ads"], [id*="ad-slot"], [class*="ad-container"]').forEach(function(el) {
                    el.remove();
                });

                // YouTube: remove ad overlays
                document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay, .ytp-ad-overlay-slot, .ytp-ad-bottomlink-section, .ytp-ad-module').forEach(function(el) {
                    el.remove();
                });

                // YouTube: try to skip ad by seeking to end
                var player = document.querySelector('video');
                if (player && player.duration > 0) {
                    var adLayer = document.querySelector('.ytp-ad-player-overlay');
                    if (adLayer) {
                        try { player.currentTime = player.duration; } catch(e) {}
                    }
                }

                // YouTube: remove promoted content sections
                document.querySelectorAll('ytd-rich-section-renderer').forEach(function(el) {
                    if (el.innerHTML.includes('promoted') || el.innerHTML.includes('sponsor')) {
                        el.remove();
                    }
                });
            })();
        """.trimIndent()
    }

    private fun getTikTokAdBlockJs(): String {
        return """
            (function() {
                // Remove ad iframes
                document.querySelectorAll('iframe').forEach(function(f) {
                    var src = (f.src || '').toLowerCase();
                    if (src.includes('doubleclick') || src.includes('googlesyndication')) {
                        f.remove();
                    }
                });

                // Remove sponsored content
                document.querySelectorAll('[class*="sponsor"], [class*="Promoted"], [data-e2e="sponsored"]').forEach(function(el) {
                    el.remove();
                });

                // Remove download app prompts
                document.querySelectorAll('[class*="DivAppPromotion"], [class*="DivDownloadBanner"]').forEach(function(el) {
                    el.remove();
                });
            })();
        """.trimIndent()
    }

    private fun getInstagramAdBlockJs(): String {
        return """
            (function() {
                // Remove ad iframes
                document.querySelectorAll('iframe').forEach(function(f) {
                    var src = (f.src || '').toLowerCase();
                    if (src.includes('doubleclick') || src.includes('googlesyndication')) {
                        f.remove();
                    }
                });

                // Remove sponsored posts
                document.querySelectorAll('[class*="x1lliihq"]').forEach(function(el) {
                    if (el.textContent.includes('Sponsored') || el.innerHTML.includes('Sponsored')) {
                        el.closest('article') ? el.closest('article').remove() : el.remove();
                    }
                });

                // Remove login prompts
                document.querySelectorAll('[role="dialog"]').forEach(function(el) {
                    if (el.innerHTML.includes('Log in') || el.innerHTML.includes('Sign up')) {
                        el.remove();
                    }
                });
            })();
        """.trimIndent()
    }

    private fun getFacebookAdBlockJs(): String {
        return """
            (function() {
                // Remove ad iframes
                document.querySelectorAll('iframe').forEach(function(f) {
                    var src = (f.src || '').toLowerCase();
                    if (src.includes('doubleclick') || src.includes('googlesyndication')) {
                        f.remove();
                    }
                });

                // Remove sponsored posts
                document.querySelectorAll('[aria-label="Sponsored"]').forEach(function(el) {
                    var story = el.closest('[data-testid="fbfeed_story"]');
                    if (story) story.remove();
                });

                // Remove ad containers
                document.querySelectorAll('[class*="_9Ag-"], [class*="x1dr75xp"]').forEach(function(el) {
                    el.remove();
                });
            })();
        """.trimIndent()
    }

    private fun getGenericAdBlockJs(): String {
        return """
            (function() {
                // Remove ad iframes
                document.querySelectorAll('iframe').forEach(function(f) {
                    var src = (f.src || '').toLowerCase();
                    if (src.includes('doubleclick') || src.includes('googlesyndication') ||
                        src.includes('googleads')) {
                        f.remove();
                    }
                });

                // Remove ad containers
                document.querySelectorAll('[id*="google_ads"], [id*="ad-slot"], [class*="ad-container"], [class*="advertisement"]').forEach(function(el) {
                    el.remove();
                });
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowContentAccess = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            // Remove WebView identifier for better site compatibility
            userAgentString = userAgentString.replace("wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Handle custom URL scheme from JavaScript bridge
                if (url.startsWith("vidgrab://")) {
                    handleCustomScheme(url)
                    return true // Don't navigate to this URL
                }

                // Check if URL is a video page
                checkUrlForVideo(url)

                currentUrl = url
                urlInput.setText(url)
                return false
            }

            // Block ad requests
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                if (isAdUrl(url)) {
                    // Return empty response to block the ad
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                currentUrl = url ?: ""
                urlInput.setText(url)
                hideHomepage()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                // Inject ad blocking (platform-specific)
                injectAdBlockCss()
                injectAdBlockJs()
                // Inject JavaScript to detect video elements
                injectVideoDetection()
                // Check URL for known video patterns
                checkUrlForVideo(url)
                // Check for login walls (Instagram, Facebook, TikTok)
                checkLoginWall()
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupListeners() {
        // Back button
        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                if (!isHomepage) {
                    showHomepage()
                } else {
                    finish()
                }
            }
        }

        // Go button (also refresh)
        goButton.setOnClickListener {
            if (isHomepage || webView.url == null) {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    loadUrl(url)
                }
            } else {
                webView.reload()
            }
        }

        // Keyboard action
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    loadUrl(url)
                }
                true
            } else false
        }

        // Paste button from clipboard
        val pasteButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.pasteButton)
        pasteButton?.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text.toString()
                if (text.contains("http") || text.contains("youtu") || text.contains("tiktok") ||
                    text.contains("instagram") || text.contains("facebook") || text.contains("threads")) {
                    urlInput.setText(text)
                    loadUrl(text)
                } else {
                    Toast.makeText(this, "No video URL in clipboard", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        // Floating download button
        fabDownload.setOnClickListener {
            showDownloadDialog()
        }
    }

    private fun setupPlatformCards() {
        findViewById<MaterialCardView>(R.id.siteYoutube).setOnClickListener {
            loadUrl("https://m.youtube.com")
        }
        findViewById<MaterialCardView>(R.id.siteTiktok).setOnClickListener {
            loadUrl("https://www.tiktok.com")
        }
        findViewById<MaterialCardView>(R.id.siteInstagram).setOnClickListener {
            loadUrl("https://www.instagram.com")
        }
        findViewById<MaterialCardView>(R.id.siteFacebook).setOnClickListener {
            loadUrl("https://m.facebook.com")
        }
        findViewById<MaterialCardView>(R.id.siteThreads).setOnClickListener {
            loadUrl("https://www.threads.net")
        }
    }

    private fun loadUrl(url: String) {
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url
        urlInput.setText(fullUrl)
        webView.loadUrl(fullUrl)
        hideHomepage()
        isHomepage = false

        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    private fun showHomepage() {
        homepageGrid.visibility = View.VISIBLE
        webView.visibility = View.GONE
        fabDownload.hide()
        isHomepage = true
        urlInput.text?.clear()
        urlInput.requestFocus()
    }

    private fun hideHomepage() {
        homepageGrid.visibility = View.GONE
        webView.visibility = View.VISIBLE
        isHomepage = false
    }

    /**
     * Inject JavaScript to detect video elements with IntersectionObserver
     * for scroll-based platforms like TikTok, Instagram Reels, Facebook Watch
     */
    private fun injectVideoDetection() {
        val js = """
            (function() {
                var videoUrls = [];
                var activeVideoSrc = null;

                // 1. Find <video> elements and extract src
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    if (v.src) videoUrls.push(v.src);
                    v.querySelectorAll('source').forEach(function(s) {
                        if (s.src) videoUrls.push(s.src);
                    });
                });

                // 2. Find <video> inside shadow DOMs (TikTok uses this)
                document.querySelectorAll('*').forEach(function(el) {
                    if (el.shadowRoot) {
                        el.shadowRoot.querySelectorAll('video').forEach(function(v) {
                            if (v.src) videoUrls.push(v.src);
                            v.querySelectorAll('source').forEach(function(s) {
                                if (s.src) videoUrls.push(s.src);
                            });
                        });
                    }
                });

                // 3. Find og:video meta tags (Facebook, Instagram, Threads)
                document.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"], meta[property="og:video:secure_url"]').forEach(function(m) {
                    var content = m.getAttribute('content');
                    if (content && content.startsWith('http')) videoUrls.push(content);
                });

                // 4. Find twitter:player:stream (Twitter/X, Threads)
                document.querySelectorAll('meta[name="twitter:player:stream"], meta[name="twitter:video.src"]').forEach(function(m) {
                    var content = m.getAttribute('content');
                    if (content && content.startsWith('http')) videoUrls.push(content);
                });

                // 5. YouTube: extract from ytInitialPlayerResponse
                try {
                    if (window.ytInitialPlayerResponse) {
                        var sr = window.ytInitialPlayerResponse.streamingData;
                        if (sr && sr.formats) {
                            sr.formats.forEach(function(f) {
                                if (f.url) videoUrls.push(f.url);
                            });
                        }
                        if (sr && sr.adaptiveFormats) {
                            sr.adaptiveFormats.forEach(function(f) {
                                if (f.url) videoUrls.push(f.url);
                            });
                        }
                    }
                } catch(e) {}

                // 6. YouTube: extract from page source JSON
                try {
                    var scripts = document.querySelectorAll('script');
                    scripts.forEach(function(s) {
                        var text = s.textContent || '';
                        var match = text.match(/"playbackUrl":"(https:.*?\.googlevideo\.com.*?)"/);
                        if (match) videoUrls.push(match[1].replace(/\\u0026/g, '&'));
                    });
                } catch(e) {}

                // 7. IntersectionObserver for TikTok/Reels scroll detection
                if (!window._vidgrabObserver) {
                    window._vidgrabObserver = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            if (entry.isIntersecting && entry.intersectionRatio > 0.5) {
                                var video = entry.target;
                                if (video.src) {
                                    window._vidgrabActiveVideo = video.src;
                                }
                            }
                        });
                    }, { threshold: [0.5] });

                    // Observe all video elements
                    document.querySelectorAll('video').forEach(function(v) {
                        window._vidgrabObserver.observe(v);
                    });
                }

                // 8. Get active video from IntersectionObserver
                if (window._vidgrabActiveVideo) {
                    videoUrls.unshift(window._vidgrabActiveVideo);
                }

                // 9. Deduplicate
                videoUrls = [...new Set(videoUrls)].filter(function(url) {
                    return url && url.startsWith('http') && !url.includes('blob:');
                });

                // 10. Send to Android
                if (videoUrls.length > 0) {
                    window.AndroidBridge = window.AndroidBridge || {};
                    window.AndroidBridge.onVideoDetected = function(urls) {};
                    // Use prompt to communicate with Android
                    try {
                        window.location.href = 'vidgrab://video_detected?' + encodeURIComponent(JSON.stringify(videoUrls));
                    } catch(e) {}
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    /**
     * Check if current URL matches known video platform patterns
     */
    private fun checkUrlForVideo(url: String?) {
        if (url == null) return

        val isVideoPage = url.contains("youtube.com/watch") ||
                url.contains("youtu.be/") ||
                url.contains("tiktok.com/video") ||
                url.contains("tiktok.com/@") ||
                url.contains("instagram.com/p/") ||
                url.contains("instagram.com/reel/") ||
                url.contains("facebook.com/watch") ||
                url.contains("facebook.com/videos") ||
                url.contains("fb.watch") ||
                url.contains("threads.net/@") ||
                url.contains("threads.net/t/") ||
                // Also detect profile pages that might have videos
                url.contains("youtube.com/@") ||
                url.contains("youtube.com/channel/")

        if (isVideoPage && !detectedVideoUrls.contains(url)) {
            detectedVideoUrls.add(url)
        }

        if (isVideoPage) {
            fabDownload.show()
        }
    }

    /**
     * Detect login walls and show friendly prompt
     */
    private fun checkLoginWall() {
        val js = """
            (function() {
                var url = window.location.href;
                var needsLogin = false;
                var platform = '';

                // Instagram: check for login wall
                if (url.includes('instagram.com')) {
                    var loginBtn = document.querySelector('[data-testid="login-button"]') ||
                                   document.querySelector('a[href="/accounts/login/"]') ||
                                   document.querySelector('button[type="submit"]');
                    var loginWall = document.querySelector('[class*="x9f619"] > div > div > div > h1');
                    if (loginBtn || loginWall) {
                        needsLogin = true;
                        platform = 'Instagram';
                    }
                }

                // Facebook: check for login wall
                if (url.includes('facebook.com')) {
                    var fbLogin = document.querySelector('[data-testid="royal_login_form"]') ||
                                  document.querySelector('#login_form');
                    if (fbLogin) {
                        needsLogin = true;
                        platform = 'Facebook';
                    }
                }

                // TikTok: check for login prompt
                if (url.includes('tiktok.com')) {
                    var ttLogin = document.querySelector('[data-e2e="login-btn"]') ||
                                  document.querySelector('[class*="DivLoginBanner"]');
                    if (ttLogin) {
                        needsLogin = true;
                        platform = 'TikTok';
                    }
                }

                if (needsLogin) {
                    window.location.href = 'vidgrab://login_needed?platform=' + platform;
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun showDownloadDialog() {
        val url = currentUrl
        if (url.isEmpty()) {
            Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show()
            return
        }

        // Detect platform
        val platform = detectPlatform(url)

        // Platform-specific download options
        val options = when (platform) {
            Platform.YOUTUBE -> arrayOf(
                "🎬 Video Only (Choose Quality)",
                "🎵 Audio Only",
                "⬇ Quick Download",
                "📋 Copy Link"
            )
            Platform.TIKTOK -> arrayOf(
                "🎬 Video (No Watermark)",
                "🎵 Audio / Music",
                "⬇ Quick Download",
                "📋 Copy Link"
            )
            Platform.INSTAGRAM -> arrayOf(
                "🎬 Video / Reel",
                "📷 Photo / Image",
                "⬇ Quick Download",
                "📋 Copy Link"
            )
            Platform.FACEBOOK -> arrayOf(
                "🎬 Video (MP4)",
                "⬇ Quick Download",
                "📋 Copy Link"
            )
            Platform.THREADS -> arrayOf(
                "🎬 Video",
                "⬇ Quick Download",
                "📋 Copy Link"
            )
            else -> arrayOf(
                "⬇ Quick Download",
                "📋 Copy Link"
            )
        }

        // Platform emoji for title
        val emoji = when (platform) {
            Platform.YOUTUBE -> "📺"
            Platform.TIKTOK -> "🎵"
            Platform.INSTAGRAM -> "📷"
            Platform.FACEBOOK -> "📘"
            Platform.THREADS -> "💬"
            else -> "🌐"
        }

        AlertDialog.Builder(this, R.style.Theme_VidGrab)
            .setTitle("$emoji Download from ${platform.displayName}")
            .setItems(options) { _, which ->
                val choice = options[which]
                when {
                    choice.contains("Copy") -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("URL", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // ALL download options: pass URL to MainActivity for inspection + download
                        val type = if (choice.contains("Audio") || choice.contains("Music")) "audio" else "video"
                        openMainApp(url, type)
                    }
                }
            }
            .show()
    }

    private fun openMainApp(url: String, downloadType: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra("download_type", downloadType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    /**
     * Handle custom URL scheme from JavaScript bridge
     */
    private fun handleCustomScheme(url: String) {
        when {
            url.startsWith("vidgrab://video_detected?") -> {
                // Video detected via IntersectionObserver
                if (fabDownload.visibility != View.VISIBLE) {
                    fabDownload.visibility = View.VISIBLE
                }
            }
            url.startsWith("vidgrab://login_needed?") -> {
                // Extract platform name
                val platform = url.substringAfter("platform=").substringBefore("&")
                showLoginPrompt(platform)
            }
        }
    }

    /**
     * Show friendly login prompt when platform requires login
     */
    private fun showLoginPrompt(platform: String) {
        AlertDialog.Builder(this, R.style.Theme_VidGrab)
            .setTitle("Login Required")
            .setMessage("$platform requires login to access this content.\n\nYou can:\n• Open $platform in your phone browser to login\n• Or download from the URL directly using Home tab")
            .setPositiveButton("OK", null)
            .setNegativeButton("Copy URL") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("URL", currentUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "URL copied!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else if (!isHomepage) {
            showHomepage()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        adBlockHandler.removeCallbacks(adBlockRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
