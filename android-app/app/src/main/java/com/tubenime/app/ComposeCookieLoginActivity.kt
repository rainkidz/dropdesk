package com.tubenime.app

import android.content.Context

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.viewinterop.AndroidView
import com.tubenime.app.ui.screens.cookie.CookieLoginScreen
import com.tubenime.app.ui.theme.TubeNimeTheme
import java.io.File

/**
 * Host Compose Cookie Login (menggantikan CookieLoginActivity XML).
 * Logic diport utuh: WebView login → capture cookies → konversi ke Netscape
 * cookies.txt → simpan ke filesDir untuk yt-dlp; helper statis
 * [CookieLoginStore.getCookiesFile]/[hasCookies] tetap jadi sumber shared.
 */
class ComposeCookieLoginActivity : ComponentActivity() {

    private var platform = CookieLoginStore.PLATFORM_FACEBOOK
    private var loginUrl = ""

    private val statusText = mutableStateOf("")
    private val progress = mutableStateOf<Float?>(0f)
    private val saveEnabled = mutableStateOf(false)
    private var webViewRef: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        ThemeEngine.applyTheme(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = intent.getStringExtra(CookieLoginStore.EXTRA_PLATFORM) ?: CookieLoginStore.PLATFORM_FACEBOOK
        loginUrl = intent.getStringExtra(CookieLoginStore.EXTRA_LOGIN_URL) ?: defaultLoginUrl(platform)

        statusText.value = "Login to ${platform.replaceFirstChar { it.uppercase() }}..."

        // Clear old cookies for this platform
        CookieManager.getInstance().removeAllCookies(null)

        setContent {
            TubeNimeTheme {
                CookieLoginScreen(
                    platform = platform.replaceFirstChar { it.uppercase() },
                    statusText = statusText.value,
                    loadingProgress = progress.value,
                    saveEnabled = saveEnabled.value,
                    onBack = { finish() },
                    onSaveClick = ::saveCookies,
                    onSkipClick = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    webViewSlot = {
                        AndroidView(
                            factory = { context ->
                                createWebView(context).apply { loadUrl(loginUrl) }
                            },
                        )
                    },
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: android.content.Context): WebView {
        val webView = WebView(context)
        webViewRef = webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.value = newProgress / 100f
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                statusText.value = "Loading..."
                saveEnabled.value = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                statusText.value = "Login successful! Tap Save to capture cookies."
                saveEnabled.value = true
            }
        }
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return webView
    }

    // ── Port: capture & konversi cookies (dari CookieLoginActivity) ──

    private fun saveCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val domain = cookieDomain(platform)
        val cookieStr = cookieManager.getCookie("https://www.$domain")
            ?: cookieManager.getCookie("https://$domain")

        if (cookieStr.isNullOrEmpty()) {
            statusText.value = "No cookies captured. Make sure you logged in."
            return
        }

        // Konversi ke Netscape cookies.txt format
        val sb = StringBuilder()
        sb.appendLine("# Netscape HTTP Cookie File")
        sb.appendLine("# This file was generated by TubeNime")
        cookieStr.split(";").forEach { cookie ->
            val trimmed = cookie.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                sb.appendLine(".$domain\tTRUE\t/\tTRUE\t0\t$name\t$value")
            }
        }

        val cookiesFile = CookieLoginStore.getCookiesFile(this, platform)
        cookiesFile.writeText(sb.toString())

        statusText.value = "✅ Cookies saved! You can now download ${platform.replaceFirstChar { it.uppercase() }} content."
        saveEnabled.value = false
        Toast.makeText(this, "Cookies saved for $platform", Toast.LENGTH_SHORT).show()

        val resultIntent = android.content.Intent()
            .putExtra("platform", platform)
            .putExtra("cookies_file", cookiesFile.absolutePath)
        setResult(RESULT_OK, resultIntent)

        webViewRef?.postDelayed({ finish() }, 2000)
    }

    private fun defaultLoginUrl(platform: String): String = when (platform) {
        CookieLoginStore.PLATFORM_FACEBOOK -> "https://www.facebook.com/"
        CookieLoginStore.PLATFORM_INSTAGRAM -> "https://www.instagram.com/accounts/login/"
        CookieLoginStore.PLATFORM_THREADS -> "https://www.threads.net/login"
        CookieLoginStore.PLATFORM_YOUTUBE -> "https://www.youtube.com/"
        CookieLoginStore.PLATFORM_TIKTOK -> "https://www.tiktok.com/login/"
        else -> "https://www.google.com"
    }

    private fun cookieDomain(platform: String): String = when (platform) {
        CookieLoginStore.PLATFORM_FACEBOOK -> "facebook.com"
        CookieLoginStore.PLATFORM_INSTAGRAM -> "instagram.com"
        CookieLoginStore.PLATFORM_THREADS -> "threads.net"
        CookieLoginStore.PLATFORM_YOUTUBE -> "youtube.com"
        CookieLoginStore.PLATFORM_TIKTOK -> "tiktok.com"
        else -> ""
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
