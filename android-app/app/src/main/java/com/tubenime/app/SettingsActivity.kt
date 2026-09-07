package com.tubenime.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    companion object {
        /** Master switch for the home-screen notice carousel (read by MainActivity). */
        const val KEY_SHOW_HOME_NOTICES = "show_home_notices"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("tubenime_prefs", MODE_PRIVATE)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        PremiumManager.init(this)
        setupPremium()
        setupAppearance()
        setupDownloads()
        setupNetwork()
        setupNotifications()
        setupUsage()
        setupAccount()
        setupAbout()
    }

    override fun onResume() {
        super.onResume()
        // Re-check the Play subscription and refresh any premium-dependent UI.
        PremiumManager.refreshPurchases()
        refreshPremiumStatus()
        refreshConcurrentRow()
        refreshLoginButtons()
    }

    // ── Premium ────────────────────────────────────────────────

    private fun setupPremium() {
        val status = findViewById<TextView>(R.id.premiumStatusValue)
        val button = findViewById<com.google.android.material.button.MaterialButton>(R.id.premiumManageButton)
        button.setOnClickListener { openPremium() }
        status.setOnClickListener { openPremium() }
        refreshPremiumStatus()
    }

    private fun refreshPremiumStatus() {
        val status = findViewById<TextView>(R.id.premiumStatusValue)
        val button = findViewById<com.google.android.material.button.MaterialButton>(R.id.premiumManageButton)

        // Anti-mod: repackaged builds (wrong signature) can never be premium.
        if (SecurityGuard.isTampered(this)) {
            status.text = "⚠️ Modified build detected — premium is disabled on this install."
            button.text = "Reinstall the official APK"
            button.isEnabled = false
            return
        }
        button.isEnabled = true

        val premium = PremiumManager.isPremium()
        if (premium) {
            val source = PremiumManager.sourceLabel()
            status.text = "✅ Premium is active" + (if (source.isNotEmpty()) " ($source)" else "")
            button.text = "Manage Premium"
        } else {
            status.text = "Free plan — 720p max, ads shown, no playlists, private posts or batch queue"
            button.text = "⭐ Go Premium"
        }
    }

    private fun openPremium() {
        startActivity(Intent(this, PremiumActivity::class.java))
    }

    /**
     * Blocks a premium-only action and sends the user to the upsell screen.
     * @return true when the action may proceed (user is premium).
     */
    private fun guardPremium(action: () -> Unit): Boolean {
        if (PremiumManager.isPremium()) {
            action()
            return true
        }
        Toast.makeText(this, "That's a Premium feature — upgrade to unlock it.", Toast.LENGTH_LONG).show()
        openPremium()
        return false
    }

    private fun setupAppearance() {
        val darkSwitch = findViewById<SwitchMaterial>(R.id.darkModeSwitch)
        val themeValue = findViewById<TextView>(R.id.themeValue)

        // Load saved state
        val nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        darkSwitch.isChecked = nightMode == AppCompatDelegate.MODE_NIGHT_YES
        themeValue.text = when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            else -> "System Default"
        }

        darkSwitch.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            prefs.edit().putInt("night_mode", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
            themeValue.text = if (isChecked) "Dark" else "Light"
        }

        themeValue.setOnClickListener {
            val options = arrayOf("System Default", "Light", "Dark")
            val currentMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val checkedItem = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                else -> 0
            }
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("App Theme")
                .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                    val mode = when (which) {
                        0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    prefs.edit().putInt("night_mode", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                    themeValue.text = options[which]
                    darkSwitch.isChecked = mode == AppCompatDelegate.MODE_NIGHT_YES
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupDownloads() {
        val locationValue = findViewById<TextView>(R.id.downloadLocationValue)
        val videoQualityValue = findViewById<TextView>(R.id.videoQualityValue)
        val audioQualityValue = findViewById<TextView>(R.id.audioQualityValue)
        val namingValue = findViewById<TextView>(R.id.namingValue)

        // Load saved values
        locationValue.text = prefs.getString("download_location", "Downloads/TubeNime") ?: "Downloads/TubeNime"
        videoQualityValue.text = prefs.getString("video_quality", "Best Available") ?: "Best Available"
        audioQualityValue.text = prefs.getString("audio_quality", "Best Available") ?: "Best Available"
        namingValue.text = prefs.getString("file_naming", "Video Title") ?: "Video Title"

        // Download Location picker
        locationValue.setOnClickListener {
            val options = arrayOf("Downloads/TubeNime", "Downloads", "Custom...")
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("Download Location")
                .setSingleChoiceItems(options, options.indexOf(locationValue.text)) { dialog, which ->
                    prefs.edit().putString("download_location", options[which]).apply()
                    locationValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }

        // Video Quality picker
        videoQualityValue.setOnClickListener {
            val options = arrayOf("Best Available", "1080p", "720p", "480p", "360p", "144p")
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("Default Video Quality")
                .setSingleChoiceItems(options, options.indexOf(videoQualityValue.text)) { dialog, which ->
                    prefs.edit().putString("video_quality", options[which]).apply()
                    videoQualityValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }

        // Audio Quality picker
        audioQualityValue.setOnClickListener {
            val options = arrayOf("Best Available", "320kbps", "256kbps", "192kbps", "128kbps")
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("Default Audio Quality")
                .setSingleChoiceItems(options, options.indexOf(audioQualityValue.text)) { dialog, which ->
                    prefs.edit().putString("audio_quality", options[which]).apply()
                    audioQualityValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }

        // File Naming picker
        namingValue.setOnClickListener {
            val options = arrayOf("Video Title", "Video Title + Quality", "Video ID", "Custom Format")
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("File Naming")
                .setSingleChoiceItems(options, options.indexOf(namingValue.text)) { dialog, which ->
                    prefs.edit().putString("file_naming", options[which]).apply()
                    namingValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupNetwork() {
        val wifiSwitch = findViewById<SwitchMaterial>(R.id.wifiOnlySwitch)
        val concurrentValue = findViewById<TextView>(R.id.concurrentValue)

        wifiSwitch.isChecked = prefs.getBoolean("wifi_only", false)

        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wifi_only", isChecked).apply()
        }

        concurrentValue.setOnClickListener {
            if (!PremiumManager.isPremium()) {
                Toast.makeText(this, "Parallel downloads are a Premium feature.", Toast.LENGTH_LONG).show()
                openPremium()
                return@setOnClickListener
            }
            val maxOption = minOf(6, TierRules.PREMIUM_MAX_CONCURRENT)
            val options = (1..maxOption).map { it.toString() }.toTypedArray()
            val current = prefs.getInt("max_concurrent", 3).coerceIn(1, maxOption).toString()
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("Max Concurrent Downloads")
                .setSingleChoiceItems(options, options.indexOf(current)) { dialog, which ->
                    prefs.edit().putInt("max_concurrent", options[which].toInt()).apply()
                    concurrentValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }
        refreshConcurrentRow()
    }

    /** Updates the concurrent-downloads label for the current tier. */
    private fun refreshConcurrentRow() {
        val concurrentValue = findViewById<TextView>(R.id.concurrentValue)
        concurrentValue.text = if (PremiumManager.isPremium()) {
            prefs.getInt("max_concurrent", 3).toString()
        } else {
            "1"
        }
    }

    private fun setupNotifications() {
        val notifCompleteSwitch = findViewById<SwitchMaterial>(R.id.notifCompleteSwitch)
        val notifErrorSwitch = findViewById<SwitchMaterial>(R.id.notifErrorSwitch)
        val homeNoticesSwitch = findViewById<SwitchMaterial>(R.id.homeNoticesSwitch)

        notifCompleteSwitch.isChecked = prefs.getBoolean("notif_complete", true)
        notifErrorSwitch.isChecked = prefs.getBoolean("notif_error", true)
        homeNoticesSwitch.isChecked = prefs.getBoolean(KEY_SHOW_HOME_NOTICES, true)

        notifCompleteSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notif_complete", isChecked).apply()
        }
        notifErrorSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notif_error", isChecked).apply()
        }

        // Turning home notifications back on also clears the X-button dismissal
        // so the banner becomes visible again on the next home visit.
        homeNoticesSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(KEY_SHOW_HOME_NOTICES, isChecked)
                .apply()
            if (isChecked) {
                prefs.edit().remove(MainActivity.WELCOME_DISMISS_KEY).apply()
            }
        }
    }

    /**
     * Shows which platforms produced successful downloads this week (UsageTracker).
     * Read-only — this is the knob that tells us whether users lean anime-heavy
     * (YouTube/Bilibili) or generic (TikTok/Instagram) over time.
     */
    private fun setupUsage() {
        val usageValue = findViewById<TextView>(R.id.weeklyUsageValue)
        val mix = UsageTracker.weeklyMix(this)
        if (mix.isEmpty()) {
            usageValue.text = "No downloads yet this week"
        } else {
            usageValue.text = mix.entries
                .sortedByDescending { it.value }
                .joinToString("  ·  ") { "${it.key.displayName} ${it.value}" }
        }
    }

    private fun setupAccount() {
        val loginFb = findViewById<com.google.android.material.button.MaterialButton>(R.id.loginFacebookBtn)
        val loginIg = findViewById<com.google.android.material.button.MaterialButton>(R.id.loginInstagramBtn)
        val loginThreads = findViewById<com.google.android.material.button.MaterialButton>(R.id.loginThreadsBtn)
        val clearHistory = findViewById<TextView>(R.id.clearHistoryBtn)

        // Update login status
        updateLoginBtn(loginFb, "facebook")
        updateLoginBtn(loginIg, "instagram")
        updateLoginBtn(loginThreads, "threads")

        // Platform login unlocks private content — a Premium feature.
        loginFb.setOnClickListener { guardPremium { openLogin(CookieLoginActivity.PLATFORM_FACEBOOK) } }
        loginIg.setOnClickListener { guardPremium { openLogin(CookieLoginActivity.PLATFORM_INSTAGRAM) } }
        loginThreads.setOnClickListener { guardPremium { openLogin(CookieLoginActivity.PLATFORM_THREADS) } }

        clearHistory.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_TubeNime)
                .setTitle("Clear Download History")
                .setMessage("This will remove all download records. Actual files will not be deleted.")
                .setPositiveButton("Clear") { _, _ ->
                    prefs.edit().remove("download_history").apply()
                    Toast.makeText(this, "Download history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateLoginBtn(btn: com.google.android.material.button.MaterialButton, platform: String) {
        val hasLogin = CookieLoginActivity.hasCookies(this, platform)
        val name = platform.replaceFirstChar { it.uppercase() }
        val lock = if (PremiumManager.isPremium()) "" else "🔒 "
        btn.text = lock + if (hasLogin) "✅ $name" else name
    }

    private fun refreshLoginButtons() {
        val loginFb = findViewById<com.google.android.material.button.MaterialButton>(R.id.loginFacebookBtn)
        val loginIg = findViewById<com.google.android.material.button.MaterialButton>(R.id.loginInstagramBtn)
        val loginThreads = findViewById<com.google.android.material.button.MaterialButton>(R.id.loginThreadsBtn)
        updateLoginBtn(loginFb, "facebook")
        updateLoginBtn(loginIg, "instagram")
        updateLoginBtn(loginThreads, "threads")
    }

    private fun openLogin(platform: String) {
        val intent = Intent(this, CookieLoginActivity::class.java).apply {
            putExtra(CookieLoginActivity.EXTRA_PLATFORM, platform)
        }
        startActivity(intent)
    }

    private fun setupAbout() {
        val versionText = findViewById<TextView>(R.id.versionText)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${pInfo.versionName}"
        } catch (_: Exception) {
            versionText.text = "v7.1.0"
        }
    }
}
