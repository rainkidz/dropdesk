package com.snapsave.app

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("vidgrab_prefs", MODE_PRIVATE)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        setupAppearance()
        setupDownloads()
        setupNetwork()
        setupNotifications()
        setupAccount()
        setupAbout()
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
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
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
        locationValue.text = prefs.getString("download_location", "Downloads/SnapSave") ?: "Downloads/SnapSave"
        videoQualityValue.text = prefs.getString("video_quality", "Best Available") ?: "Best Available"
        audioQualityValue.text = prefs.getString("audio_quality", "Best Available") ?: "Best Available"
        namingValue.text = prefs.getString("file_naming", "Video Title") ?: "Video Title"

        // Download Location picker
        locationValue.setOnClickListener {
            val options = arrayOf("Downloads/SnapSave", "Downloads/VidGrab", "Downloads", "Custom...")
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
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
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
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
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
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
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
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
        concurrentValue.text = prefs.getInt("max_concurrent", 3).toString()

        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wifi_only", isChecked).apply()
        }

        concurrentValue.setOnClickListener {
            val options = arrayOf("1", "2", "3", "4", "5", "6")
            val current = prefs.getInt("max_concurrent", 3).toString()
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
                .setTitle("Max Concurrent Downloads")
                .setSingleChoiceItems(options, options.indexOf(current)) { dialog, which ->
                    prefs.edit().putInt("max_concurrent", options[which].toInt()).apply()
                    concurrentValue.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupNotifications() {
        val notifCompleteSwitch = findViewById<SwitchMaterial>(R.id.notifCompleteSwitch)
        val notifErrorSwitch = findViewById<SwitchMaterial>(R.id.notifErrorSwitch)

        notifCompleteSwitch.isChecked = prefs.getBoolean("notif_complete", true)
        notifErrorSwitch.isChecked = prefs.getBoolean("notif_error", true)

        notifCompleteSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notif_complete", isChecked).apply()
        }
        notifErrorSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notif_error", isChecked).apply()
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

        loginFb.setOnClickListener { openLogin(CookieLoginActivity.PLATFORM_FACEBOOK) }
        loginIg.setOnClickListener { openLogin(CookieLoginActivity.PLATFORM_INSTAGRAM) }
        loginThreads.setOnClickListener { openLogin(CookieLoginActivity.PLATFORM_THREADS) }

        clearHistory.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_VidGrab)
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
        btn.text = if (hasLogin) "✅ $name" else name
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
