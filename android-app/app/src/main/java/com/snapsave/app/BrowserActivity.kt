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

        // Check if opened with a URL from intent
        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (!initialUrl.isNullOrEmpty()) {
            loadUrl(initialUrl)
        }
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
                currentUrl = url
                urlInput.setText(url)
                return false
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
                // Inject JavaScript to detect video elements
                injectVideoDetection()
                // Check URL for known video patterns
                checkUrlForVideo(url)
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
        fabDownload.visibility = View.GONE
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
     * Inject JavaScript to detect <video>, <source>, and <iframe> elements
     * and extract their src URLs for download
     */
    private fun injectVideoDetection() {
        val js = """
            (function() {
                var videoUrls = [];
                
                // Find <video> elements
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    if (v.src) videoUrls.push(v.src);
                    v.querySelectorAll('source').forEach(function(s) {
                        if (s.src) videoUrls.push(s.src);
                    });
                });
                
                // Find <video> inside shadow DOMs
                var allElements = document.querySelectorAll('*');
                allElements.forEach(function(el) {
                    if (el.shadowRoot) {
                        el.shadowRoot.querySelectorAll('video').forEach(function(v) {
                            if (v.src) videoUrls.push(v.src);
                            v.querySelectorAll('source').forEach(function(s) {
                                if (s.src) videoUrls.push(s.src);
                            });
                        });
                    }
                });
                
                // Find og:video meta tags
                document.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"]').forEach(function(m) {
                    var content = m.getAttribute('content');
                    if (content) videoUrls.push(content);
                });
                
                // Find twitter:player:stream
                document.querySelectorAll('meta[name="twitter:player:stream"]').forEach(function(m) {
                    var content = m.getAttribute('content');
                    if (content) videoUrls.push(content);
                });
                
                // Deduplicate
                videoUrls = [...new Set(videoUrls)];
                
                // Send to Android
                if (videoUrls.length > 0) {
                    VidGrabBridge.onVideoDetected(JSON.stringify(videoUrls));
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
                url.contains("threads.net/t/")

        if (isVideoPage && !detectedVideoUrls.contains(url)) {
            detectedVideoUrls.add(url)
        }

        if (isVideoPage) {
            fabDownload.visibility = View.VISIBLE
        }
    }

    private fun showDownloadDialog() {
        val url = currentUrl
        if (url.isEmpty()) {
            Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show()
            return
        }

        // Detect platform
        val platform = PlatformDetector.detect(url)

        val options = when (platform) {
            Platform.YOUTUBE -> arrayOf("🎵 Audio Only", "🎬 Video Only", "⬇ Quick Download", "📋 Copy Link")
            Platform.TIKTOK -> arrayOf("🎬 Video (No Watermark)", "🎵 Audio / Music", "📋 Copy Link")
            Platform.FACEBOOK -> arrayOf("🎬 Video (MP4)", "📋 Copy Link")
            Platform.INSTAGRAM -> arrayOf("🎬 Video / Reel", "📷 Photo / Image", "📋 Copy Link")
            else -> arrayOf("⬇ Quick Download", "📋 Copy Link")
        }

        AlertDialog.Builder(this, R.style.Theme_VidGrab)
            .setTitle("Download from ${platform.displayName}")
            .setItems(options) { _, which ->
                val choice = options[which]
                when {
                    choice.contains("Copy") -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("URL", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show()
                    }
                    choice.contains("Video") || choice.contains("Quick") -> {
                        openMainApp(url, "video")
                    }
                    choice.contains("Audio") -> {
                        openMainApp(url, "audio")
                    }
                    choice.contains("Photo") -> {
                        openMainApp(url, "video")
                    }
                }
            }
            .show()
    }

    private fun openMainApp(url: String, downloadType: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra("download_type", downloadType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
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
        webView.destroy()
        super.onDestroy()
    }
}
