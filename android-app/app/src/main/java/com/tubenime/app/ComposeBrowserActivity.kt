package com.tubenime.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.viewinterop.AndroidView
import com.tubenime.app.ui.components.NavAnim
import com.tubenime.app.ui.screens.browser.BrowserPlatformCard
import com.tubenime.app.ui.screens.browser.BrowserScreen
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Host Compose layar Browser (menggantikan BrowserActivity XML — 1.005 baris).
 * Logic diport UTUH:
 * - Ad-blocker: domain blocklist + empty-response intercept + CSS injection
 *   per-platform (YouTube/TikTok/Instagram/Facebook/generic) + JS injector 3s
 * - Video detection: <video> src, shadow DOM, og:video meta, ytInitialPlayerResponse,
 *   IntersectionObserver untuk scroll-feed (TikTok/Reels)
 * - YouTube SPA URL tracking (pushState/replaceState/popstate → tubenime://)
 * - Login-wall detection → prompt ramah
 * - FAB/pill download → kirim URL ke ComposeHomeActivity (inspect + download)
 * UI 100% Compose "Shōnen Jump Ink"; WebView disisipkan via slot.
 */
class ComposeBrowserActivity : ComponentActivity() {

    private var webViewRef: WebView? = null
    private val detectedVideoUrls = mutableListOf<String>()
    private val adBlockHandler = Handler(Looper.getMainLooper())

    // ── State UI (hoisted ke screen) ────────────────────────────
    private val urlText = mutableStateOf("")
    private val isHomepage = mutableStateOf(true)
    private val canGoBack = mutableStateOf(false)
    private val canGoForward = mutableStateOf(false)
    private val showDownloadPill = mutableStateOf(false)

    private var currentUrl = ""
    private var pendingUrl: String? = null

    private val adBlockRunnable = object : Runnable {
        override fun run() {
            if (!isHomepage.value && !isFinishing) {
                injectAdBlockJs()
            }
            adBlockHandler.postDelayed(this, 3000)
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PLATFORM = "extra_platform"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        ThemeEngine.applyTheme(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Langsung ke viewport bila dibuka dari matrix Home (tanpa flash homepage).
        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotEmpty() }?.let {
            pendingUrl = it
            urlText.value = it
            isHomepage.value = false
        }

        setContent {
            TubeNimeTheme {
                BrowserScreen(
                    url = urlText.value,
                    isHomepage = isHomepage.value,
                    canGoBack = canGoBack.value,
                    canGoForward = canGoForward.value,
                    showDownloadPill = showDownloadPill.value,
                    platforms = defaultPlatforms(),
                    selectedNavIndex = 1, // tab Browser aktif
                    onNavSelected = ::openNav,
                    onUrlChange = { urlText.value = it },
                    onUrlSubmit = { submitUrlBar() },
                    onBack = ::onToolbarBack,
                    onForward = { webViewRef?.goForward() },
                    onShare = ::shareCurrentUrl,
                    onReload = { reloadOrSubmit() },
                    onPageInfo = ::showPageInfo,
                    onBookmark = ::bookmarkCurrentUrl,
                    onMenuClick = ::onToolbarBack,
                    onSearchClick = { submitUrlBar() },
                    onPlatformClick = { loadUrl(it.url) },
                    onDownloadClick = ::getActualUrlAndSendToHome,
                    webSlot = {
                        AndroidView(
                            factory = { context ->
                                createWebView(context).also { wv ->
                                    webViewRef = wv
                                    pendingUrl?.let {
                                        pendingUrl = null
                                        wv.loadUrl(it)
                                        hideHomepage()
                                    }
                                }
                            },
                            update = { wv ->
                                canGoBack.value = wv.canGoBack()
                                canGoForward.value = wv.canGoForward()
                                // Terapkan URL susulan (kasus tap matrix saat WebView belum siap).
                                pendingUrl?.let {
                                    pendingUrl = null
                                    wv.loadUrl(it)
                                    hideHomepage()
                                }
                            },
                        )
                    },
                )
                BackHandler {
                    val wv = webViewRef
                    when {
                        wv != null && wv.canGoBack() -> wv.goBack()
                        !isHomepage.value -> showHomepage()
                        else -> finish()
                    }
                }
            }
        }

        adBlockHandler.postDelayed(adBlockRunnable, 3000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Tap matrix kedua saat Browser sudah terbuka (SINGLE_TOP): muat URL baru.
        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotEmpty() }?.let { loadUrl(it) }
    }

    // Homepage browser: feed trending anime yang sama dengan platform matrix.
    private fun defaultPlatforms(): List<BrowserPlatformCard> {
        val anime = com.tubenime.app.ui.screens.home.components.AnimeChannels
        return listOf(
            BrowserPlatformCard("YouTube", anime.platformTrendingUrl("YouTube")!!, R.drawable.figma_tile_youtube),
            BrowserPlatformCard("TikTok", anime.platformTrendingUrl("TikTok")!!, R.drawable.figma_tile_tiktok),
            BrowserPlatformCard("Instagram", anime.platformTrendingUrl("Instagram")!!, R.drawable.figma_tile_instagram),
            BrowserPlatformCard("Bilibili", anime.platformTrendingUrl("Bilibili")!!, R.drawable.figma_tile_bilibili),
            BrowserPlatformCard("Facebook", anime.platformTrendingUrl("Facebook")!!, R.drawable.figma_tile_facebook),
            BrowserPlatformCard("Threads", anime.platformTrendingUrl("Threads")!!, R.drawable.figma_tile_threads),
        )
    }

    // ── Navigasi bottom nav (tab Browser aktif) ──

    private fun openNav(index: Int) {
        if (index == 1) return // sudah di Browser
        val target = when (index) {
            0 -> ComposeHomeActivity::class.java
            2 -> ComposeDownloadsActivity::class.java
            3 -> ComposeSettingsActivity::class.java
            else -> return
        }
        NavAnim.go(
            this,
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            NavAnim.TAB_BROWSER,
            index,
        )
    }

    // ── WebView factory (port setupWebView) ─────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: android.content.Context): WebView {
        val webView = WebView(context)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
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
            userAgentString = userAgentString.replace("wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("tubenime://")) {
                    handleCustomScheme(url)
                    return true
                }
                // Block non-web schemes (bilibili.tv redirect ke bstar://)
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return true
                }
                checkUrlForVideo(url)
                currentUrl = url
                urlText.value = url
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                if (isAdUrl(url)) {
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentUrl = url ?: ""
                urlText.value = currentUrl
                hideHomepage()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!url.isNullOrEmpty() && url != "about:blank") {
                    currentUrl = url
                    urlText.value = url
                }
                view?.let {
                    canGoBack.value = it.canGoBack()
                    canGoForward.value = it.canGoForward()
                }
                injectSpaUrlTracking()
                injectAdBlockCss()
                injectAdBlockJs()
                injectVideoDetection()
                checkUrlForVideo(url)
                checkLoginWall()
            }

            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {}
        return webView
    }

    // ── Navigasi & state layar ──────────────────────────────────

    private fun loadUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val fullUrl = if (!trimmed.startsWith("http")) "https://$trimmed" else trimmed
        urlText.value = fullUrl
        currentUrl = fullUrl
        // Antre bila WebView belum siap (factory/update akan menghabiskan pendingUrl).
        if (webViewRef == null) pendingUrl = fullUrl else webViewRef?.loadUrl(fullUrl)
        hideHomepage()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun submitUrlBar() {
        val t = urlText.value.trim()
        if (t.isEmpty()) {
            Toast.makeText(this, "Ketik URL atau pilih platform dulu", Toast.LENGTH_SHORT).show()
            return
        }
        loadUrl(t)
    }

    private fun reloadOrSubmit() {
        if (isHomepage.value) submitUrlBar() else webViewRef?.reload()
    }

    private fun showPageInfo() {
        val url = currentUrl.ifEmpty { urlText.value }.ifEmpty { "(homepage)" }
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TubeNime)
            .setTitle("Page info")
            .setMessage(url)
            .setPositiveButton("OK", null)
            .setNegativeButton("Copy URL") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                Toast.makeText(this, "URL copied!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun bookmarkCurrentUrl() {
        val url = currentUrl.ifEmpty { urlText.value }
        if (url.isEmpty()) {
            Toast.makeText(this, "Belum ada URL untuk di-bookmark", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
        Toast.makeText(this, "Bookmarked (URL copied!)", Toast.LENGTH_SHORT).show()
    }

    private fun showHomepage() {
        isHomepage.value = true
        showDownloadPill.value = false
        urlText.value = ""
    }

    private fun hideHomepage() {
        isHomepage.value = false
    }

    private fun onToolbarBack() {
        val wv = webViewRef
        when {
            wv != null && wv.canGoBack() -> wv.goBack()
            !isHomepage.value -> showHomepage()
            else -> finish()
        }
    }

    private fun shareCurrentUrl() {
        val url = currentUrl.ifEmpty { urlText.value }
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    },
                    "Share URL",
                )
            )
        } catch (_: Exception) {}
    }

    // ── Ad-blocker (port utuh dari BrowserActivity) ─────────────

    private val adDomains = setOf(
        "pagead2.googlesyndication.com", "googleadservices.com", "www.googleadservices.com",
        "adservice.google.com", "doubleclick.net", "*.doubleclick.net", "tpc.googlesyndication.com",
        "www-doubleclick-net.cdn.ampproject.org", "googletagmanager.com", "googletagservices.com",
        "googlesyndication.com", "youtube.com/api/stats/ads", "youtube.com/get_video_info&ei=",
        "s.ytimg.com/yts/jsbin/", "static.doubleclick.net", "ad.doubleclick.net",
        "an.facebook.com", "pixel.facebook.com", "www.facebook.com/tr",
        "ads-twitter.com", "analytics.twitter.com", "ad.turn.com", "advertising.com",
        "adnxs.com", "adsrvr.org", "casalemedia.com", "contextweb.com", "demdex.net",
        "everesttech.net", "indeed.com/ads", "taboola.com", "outbrain.com", "moatads.com",
        "serving-sys.com", "smaato.net", "unity3d.com/ads", "applovin.com", "inmobi.com",
        "vungle.com", "chartboost.com",
    )

    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        return adDomains.any { domain ->
            if (domain.startsWith("*.")) lower.contains(domain.removePrefix("*."))
            else lower.contains(domain)
        }
    }

    private fun detectPlatform(url: String): Platform = PlatformDetector.detect(url)

    private fun getAdBlockCss(): String = when (detectPlatform(currentUrl)) {
        Platform.YOUTUBE -> """
            ytd-ad-slot-renderer, ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer,
            ytd-video-masthead-ad-ad-slot-renderer, ytd-in-feed-ad-renderer, ytd-ad-banner-renderer,
            ytd-statement-banner-renderer, #player-ads, #ad-container,
            .ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay,
            .ytp-ad-overlay-slot, .video-ads, .ytp-ad-module,
            ytd-rich-section-renderer:has(ytd-statement-banner-renderer),
            ytd-rich-section-renderer:has([href*='promoted']),
            ytd-mealbar-promo-renderer, .ytp-ad-bottomlink-section,
            ytd-popup-container, ytd-membership-pill-renderer
        """.trimIndent()
        Platform.TIKTOK -> """
            [data-e2e='top-tab-create'], [class*='DivCookieBanner'], [class*='DivLoginBanner'],
            [class*='DivFooter'], [id*='google_ads'], [class*='ad-container'],
            iframe[src*='doubleclick']
        """.trimIndent()
        Platform.INSTAGRAM -> """
            [class*='x1n2onr6'][class*='x78zumc'], [role='navigation'],
            [class*='x9f619'] > div:first-child, [data-testid='ad-banner'],
            [id*='google_ads'], iframe[src*='doubleclick']
        """.trimIndent()
        Platform.FACEBOOK -> """
            [aria-label='Sponsored'], [data-testid='fbfeed_story'], [class*='_9Ag-'],
            [class*='x1dr75xp'], [id*='google_ads'], [class*='ad-container'],
            iframe[src*='doubleclick'], iframe[src*='googlesyndication']
        """.trimIndent()
        else -> """
            [id*='google_ads'], [id*='ad-slot'], [class*='ad-container'],
            [class*='advertisement'], [data-ad], iframe[src*='doubleclick'],
            iframe[src*='googlesyndication'], iframe[src*='googleads']
        """.trimIndent()
    }

    private fun injectAdBlockCss() {
        val css = getAdBlockCss()
        val js = """
            (function() {
                var style = document.createElement('style');
                style.id = 'tubenime-adblock';
                style.textContent = '$css';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
    }

    private fun injectAdBlockJs() {
        val platformJs = when (detectPlatform(currentUrl)) {
            Platform.YOUTUBE -> """
                (function() {
                    document.querySelectorAll('iframe').forEach(function(f) {
                        var src = (f.src || '').toLowerCase();
                        if (src.includes('doubleclick') || src.includes('googlesyndication') ||
                            src.includes('googleads')) { f.remove(); }
                    });
                    document.querySelectorAll('[id*="google_ads"], [id*="ad-slot"], [class*="ad-container"]').forEach(function(el) { el.remove(); });
                    document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay, .ytp-ad-overlay-slot, .ytp-ad-bottomlink-section, .ytp-ad-module').forEach(function(el) { el.remove(); });
                    var player = document.querySelector('video');
                    if (player && player.duration > 0) {
                        var adLayer = document.querySelector('.ytp-ad-player-overlay');
                        if (adLayer) { try { player.currentTime = player.duration; } catch(e) {} }
                    }
                    document.querySelectorAll('ytd-rich-section-renderer').forEach(function(el) {
                        if (el.innerHTML.includes('promoted') || el.innerHTML.includes('sponsor')) { el.remove(); }
                    });
                })();
            """.trimIndent()
            Platform.TIKTOK -> """
                (function() {
                    document.querySelectorAll('iframe').forEach(function(f) {
                        var src = (f.src || '').toLowerCase();
                        if (src.includes('doubleclick') || src.includes('googlesyndication')) { f.remove(); }
                    });
                    document.querySelectorAll('[class*="sponsor"], [class*="Promoted"], [data-e2e="sponsored"]').forEach(function(el) { el.remove(); });
                    document.querySelectorAll('[class*="DivAppPromotion"], [class*="DivDownloadBanner"]').forEach(function(el) { el.remove(); });
                })();
            """.trimIndent()
            Platform.INSTAGRAM -> """
                (function() {
                    document.querySelectorAll('iframe').forEach(function(f) {
                        var src = (f.src || '').toLowerCase();
                        if (src.includes('doubleclick') || src.includes('googlesyndication')) { f.remove(); }
                    });
                    document.querySelectorAll('[class*="x1lliihq"]').forEach(function(el) {
                        if (el.textContent.includes('Sponsored') || el.innerHTML.includes('Sponsored')) {
                            el.closest('article') ? el.closest('article').remove() : el.remove();
                        }
                    });
                    document.querySelectorAll('[role="dialog"]').forEach(function(el) {
                        if (el.innerHTML.includes('Log in') || el.innerHTML.includes('Sign up')) { el.remove(); }
                    });
                })();
            """.trimIndent()
            Platform.FACEBOOK -> """
                (function() {
                    document.querySelectorAll('iframe').forEach(function(f) {
                        var src = (f.src || '').toLowerCase();
                        if (src.includes('doubleclick') || src.includes('googlesyndication')) { f.remove(); }
                    });
                    document.querySelectorAll('[aria-label="Sponsored"]').forEach(function(el) {
                        var story = el.closest('[data-testid="fbfeed_story"]');
                        if (story) story.remove();
                    });
                    document.querySelectorAll('[class*="_9Ag-"], [class*="x1dr75xp"]').forEach(function(el) { el.remove(); });
                })();
            """.trimIndent()
            else -> """
                (function() {
                    document.querySelectorAll('iframe').forEach(function(f) {
                        var src = (f.src || '').toLowerCase();
                        if (src.includes('doubleclick') || src.includes('googlesyndication') ||
                            src.includes('googleads')) { f.remove(); }
                    });
                    document.querySelectorAll('[id*="google_ads"], [id*="ad-slot"], [class*="ad-container"], [class*="advertisement"]').forEach(function(el) { el.remove(); });
                })();
            """.trimIndent()
        }
        webViewRef?.evaluateJavascript(platformJs, null)
    }

    // ── SPA URL tracking (YouTube) ──────────────────────────────

    private fun injectSpaUrlTracking() {
        val js = """
            (function() {
                if (!window.location.href.includes('youtube.com')) return;
                var origPush = history.pushState;
                var origReplace = history.replaceState;
                history.pushState = function() {
                    origPush.apply(this, arguments);
                    window._tubenimeOnUrlChange();
                };
                history.replaceState = function() {
                    origReplace.apply(this, arguments);
                    window._tubenimeOnUrlChange();
                };
                window.addEventListener('popstate', function() {
                    window._tubenimeOnUrlChange();
                });
                window._tubenimeOnUrlChange = function() {
                    try {
                        window.location.href = 'tubenime://url_changed?' + encodeURIComponent(window.location.href);
                    } catch(e) {}
                };
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
    }

    // ── Video detection (port utuh) ─────────────────────────────

    private fun injectVideoDetection() {
        val js = """
            (function() {
                var videoUrls = [];
                var activeVideoSrc = null;
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    if (v.src) videoUrls.push(v.src);
                    v.querySelectorAll('source').forEach(function(s) {
                        if (s.src) videoUrls.push(s.src);
                    });
                });
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
                document.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"], meta[property="og:video:secure_url"]').forEach(function(m) {
                    var content = m.getAttribute('content');
                    if (content && content.startsWith('http')) videoUrls.push(content);
                });
                document.querySelectorAll('meta[name="twitter:player:stream"], meta[name="twitter:video.src"]').forEach(function(m) {
                    var content = m.getAttribute('content');
                    if (content && content.startsWith('http')) videoUrls.push(content);
                });
                try {
                    if (window.ytInitialPlayerResponse) {
                        var sr = window.ytInitialPlayerResponse.streamingData;
                        if (sr && sr.formats) {
                            sr.formats.forEach(function(f) { if (f.url) videoUrls.push(f.url); });
                        }
                        if (sr && sr.adaptiveFormats) {
                            sr.adaptiveFormats.forEach(function(f) { if (f.url) videoUrls.push(f.url); });
                        }
                    }
                } catch(e) {}
                try {
                    var scripts = document.querySelectorAll('script');
                    scripts.forEach(function(s) {
                        var text = s.textContent || '';
                        var match = text.match(/"playbackUrl":"(https:.*?\.googlevideo\.com.*?)"/);
                        if (match) videoUrls.push(match[1].replace(/\\u0026/g, '&'));
                    });
                } catch(e) {}
                if (!window._tubenimeObserver) {
                    window._tubenimeObserver = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            if (entry.isIntersecting && entry.intersectionRatio > 0.5) {
                                var video = entry.target;
                                if (video.src) { window._tubenimeActiveVideo = video.src; }
                            }
                        });
                    }, { threshold: [0.5] });
                    document.querySelectorAll('video').forEach(function(v) {
                        window._tubenimeObserver.observe(v);
                    });
                }
                if (window._tubenimeActiveVideo) { videoUrls.unshift(window._tubenimeActiveVideo); }
                videoUrls = [...new Set(videoUrls)].filter(function(url) {
                    return url && url.startsWith('http') && !url.includes('blob:');
                });
                if (videoUrls.length > 0) {
                    try {
                        window.location.href = 'tubenime://video_detected?' + encodeURIComponent(JSON.stringify(videoUrls));
                    } catch(e) {}
                }
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
    }

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
                url.contains("youtube.com/@") ||
                url.contains("youtube.com/channel/")

        if (isVideoPage && !detectedVideoUrls.contains(url)) {
            detectedVideoUrls.add(url)
        }
        showDownloadPill.value = isVideoPage
    }

    // ── Login-wall detection ────────────────────────────────────

    private fun checkLoginWall() {
        val js = """
            (function() {
                var url = window.location.href;
                var needsLogin = false;
                var platform = '';
                if (url.includes('instagram.com')) {
                    var loginBtn = document.querySelector('[data-testid="login-button"]') ||
                                   document.querySelector('a[href="/accounts/login/"]') ||
                                   document.querySelector('button[type="submit"]');
                    var loginWall = document.querySelector('[class*="x9f619"] > div > div > div > h1');
                    if (loginBtn || loginWall) { needsLogin = true; platform = 'Instagram'; }
                }
                if (url.includes('facebook.com')) {
                    var fbLogin = document.querySelector('[data-testid="royal_login_form"]') ||
                                  document.querySelector('#login_form');
                    if (fbLogin) { needsLogin = true; platform = 'Facebook'; }
                }
                if (url.includes('tiktok.com')) {
                    var ttLogin = document.querySelector('[data-e2e="login-btn"]') ||
                                  document.querySelector('[class*="DivLoginBanner"]');
                    if (ttLogin) { needsLogin = true; platform = 'TikTok'; }
                }
                if (needsLogin) {
                    window.location.href = 'tubenime://login_needed?platform=' + platform;
                }
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
    }

    // ── Custom scheme + aksi download ───────────────────────────

    private fun handleCustomScheme(url: String) {
        when {
            url.startsWith("tubenime://video_detected?") -> showDownloadPill.value = true
            url.startsWith("tubenime://login_needed?") -> {
                val platform = url.substringAfter("platform=").substringBefore("&")
                showLoginPrompt(platform)
            }
            url.startsWith("tubenime://url_changed?") -> {
                val encodedUrl = url.substringAfter("url_changed?")
                try {
                    val actualUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                    currentUrl = actualUrl
                    urlText.value = actualUrl
                    checkUrlForVideo(actualUrl)
                } catch (_: Exception) {}
            }
        }
    }

    private fun showLoginPrompt(platform: String) {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TubeNime)
            .setTitle("Login Required")
            .setMessage("$platform requires login to access this content.\n\nYou can:\n• Open $platform in your phone browser to login\n• Or download from the URL directly using Home tab")
            .setPositiveButton("OK", null)
            .setNegativeButton("Copy URL") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                Toast.makeText(this, "URL copied!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun getActualUrlAndSendToHome() {
        val wv = webViewRef ?: return sendToHome(currentUrl)
        wv.evaluateJavascript("(function() { return window.location.href; })()") { result ->
            val actualUrl = result?.removeSurrounding("\"") ?: currentUrl
            currentUrl = actualUrl
            urlText.value = actualUrl
            sendToHome(actualUrl)
        }
    }

    private fun sendToHome(url: String) {
        if (url.isEmpty() || url == "about:blank") {
            Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show()
            return
        }
        val platform = detectPlatform(url)
        // "audio" utk opsi Audio/Music; selebihnya video (port showDownloadDialog)
        // Mundur ke Home (tab 0 < 1): Impact Cut dari kiri.
        NavAnim.go(
            this,
            Intent(this, ComposeHomeActivity::class.java).apply {
                action = Intent.ACTION_SEND
                this.type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra("download_type", "video")
                putExtra("platform_hint", platform.name)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            NavAnim.TAB_BROWSER,
            NavAnim.TAB_HOME,
        )
        finish()
    }

    override fun onDestroy() {
        adBlockHandler.removeCallbacks(adBlockRunnable)
        webViewRef?.destroy()
        webViewRef = null
        super.onDestroy()
    }
}
