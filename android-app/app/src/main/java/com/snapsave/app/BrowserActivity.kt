package com.snapsave.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var urlInput: TextInputEditText
    private lateinit var goButton: MaterialButton
    private lateinit var downloadButton: MaterialButton

    private var currentUrl = ""

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
        downloadButton = findViewById(R.id.downloadButton)

        // Get initial URL from intent
        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: getPlatformUrl()

        setupWebView()
        setupListeners()

        // Load initial URL
        urlInput.setText(initialUrl)
        webView.loadUrl(initialUrl)
    }

    private fun getPlatformUrl(): String {
        val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: "youtube"
        return when (platform) {
            "youtube" -> "https://m.youtube.com"
            "tiktok" -> "https://www.tiktok.com"
            "instagram" -> "https://www.instagram.com"
            "facebook" -> "https://m.facebook.com"
            "threads" -> "https://www.threads.net"
            else -> "https://www.google.com"
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
            userAgentString = webView.settings.userAgentString.replace(
                "wv", ""
            ) // Remove WebView identifier
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
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                // Check if URL looks like a video
                checkForVideoUrl(url)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed() // Note: In production, you should handle this more securely
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        // Go button
        goButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                val fullUrl = if (!url.startsWith("http")) "https://$url" else url
                webView.loadUrl(fullUrl)
            }
        }

        // Keyboard action
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                goButton.performClick()
                true
            } else false
        }

        // Download button
        downloadButton.setOnClickListener {
            val url = currentUrl
            if (url.isNotEmpty()) {
                showDownloadDialog(url)
            } else {
                Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkForVideoUrl(url: String?) {
        if (url == null) return

        val isVideoUrl = url.contains("youtube.com/watch") ||
                url.contains("youtu.be/") ||
                url.contains("tiktok.com/video") ||
                url.contains("tiktok.com/@") ||
                url.contains("instagram.com/p/") ||
                url.contains("instagram.com/reel/") ||
                url.contains("facebook.com/watch") ||
                url.contains("fb.watch")

        downloadButton.visibility = if (isVideoUrl) View.VISIBLE else View.GONE
    }

    private fun showDownloadDialog(url: String) {
        val options = arrayOf("Download as Video", "Download as Audio", "Copy Link", "Open in Main App")
        
        AlertDialog.Builder(this, R.style.Theme_VidGrab)
            .setTitle("Download Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openMainApp(url, "video")
                    1 -> openMainApp(url, "audio")
                    2 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("URL", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show()
                    }
                    3 -> openMainApp(url, null)
                }
            }
            .show()
    }

    private fun openMainApp(url: String, downloadType: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra("download_type", downloadType)
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
