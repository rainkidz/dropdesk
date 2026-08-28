package com.snapsave.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebView-based login activity.
 * User logs in to Facebook/Instagram/etc via WebView,
 * and we capture all cookies → save as Netscape cookies.txt for yt-dlp.
 */
class CookieLoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_LOGIN_URL = "login_url"

        const val PLATFORM_FACEBOOK = "facebook"
        const val PLATFORM_INSTAGRAM = "instagram"
        const val PLATFORM_THREADS = "threads"

        fun getCookiesFile(context: Context, platform: String): File {
            return File(context.filesDir, "cookies_${platform}.txt")
        }

        fun hasCookies(context: Context, platform: String): Boolean {
            val file = getCookiesFile(context, platform)
            return file.exists() && file.length() > 10
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var saveButton: Button
    private lateinit var skipButton: Button

    private var platform = ""
    private var loginUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cookie_login)

        platform = intent.getStringExtra(EXTRA_PLATFORM) ?: PLATFORM_FACEBOOK
        loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL) ?: getDefaultLoginUrl(platform)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        saveButton = findViewById(R.id.saveButton)
        skipButton = findViewById(R.id.skipButton)

        statusText.text = "Login to ${platform.replaceFirstChar { it.uppercase() }}..."

        // Clear old cookies for this platform
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                statusText.text = "Loading..."
                saveButton.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                statusText.text = "Login successful! Tap Save to capture cookies."
                saveButton.visibility = View.VISIBLE
            }
        }

        // Save button — capture cookies and save
        saveButton.setOnClickListener {
            saveCookies()
        }

        // Skip button — go back without saving
        skipButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        webView.loadUrl(loginUrl)
    }

    private fun saveCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        // Get cookies for the platform domain
        val domain = getCookieDomain(platform)
        val cookieStr = cookieManager.getCookie("https://www.$domain")

        if (cookieStr.isNullOrEmpty()) {
            // Try without www

            val altCookieStr = cookieManager.getCookie("https://$domain")
            if (altCookieStr.isNullOrEmpty()) {
                statusText.text = "No cookies captured. Make sure you logged in."
                return
            }
            saveCookieString(altCookieStr, domain)
            return
        }

        saveCookieString(cookieStr, domain)
    }

    private fun saveCookieString(cookieStr: String, domain: String) {
        // Convert cookie string to Netscape cookies.txt format
        // Cookie string format: "name1=value1; name2=value2; ..."
        val sb = StringBuilder()
        sb.appendLine("# Netscape HTTP Cookie File")
        sb.appendLine("# This file was generated by VidGrab")

        val includeSubdomains = "TRUE"
        val path = "/"
        val secure = "TRUE"
        val expiry = "0"

        cookieStr.split(";").forEach { cookie ->
            val trimmed = cookie.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                sb.appendLine(".$domain\t$includeSubdomains\t$path\t$secure\t$expiry\t$name\t$value")
            }
        }

        val cookiesFile = getCookiesFile(this, platform)
        cookiesFile.writeText(sb.toString())

        statusText.text = "✅ Cookies saved! You can now download ${platform.replaceFirstChar { it.uppercase() }} content."

        // Return result
        val resultIntent = Intent()
        resultIntent.putExtra("platform", platform)
        resultIntent.putExtra("cookies_file", cookiesFile.absolutePath)
        setResult(RESULT_OK, resultIntent)

        // Auto-close after 2 seconds
        saveButton.isEnabled = false
        saveButton.text = "✅ Saved!"
        webView.postDelayed({ finish() }, 2000)
    }

    private fun getDefaultLoginUrl(platform: String): String {
        return when (platform) {
            PLATFORM_FACEBOOK -> "https://www.facebook.com/"
            PLATFORM_INSTAGRAM -> "https://www.instagram.com/accounts/login/"
            PLATFORM_THREADS -> "https://www.threads.net/login"
            else -> "https://www.google.com"
        }
    }

    private fun getCookieDomain(platform: String): String {
        return when (platform) {
            PLATFORM_FACEBOOK -> "facebook.com"
            PLATFORM_INSTAGRAM -> "instagram.com"
            PLATFORM_THREADS -> "threads.net"
            else -> ""
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
