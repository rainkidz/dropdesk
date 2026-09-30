package com.tubenime.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.mutableStateOf
import com.tubenime.app.ui.components.NavAnim
import com.tubenime.app.ui.screens.settings.CookieSlot
import com.tubenime.app.ui.screens.settings.PaletteSlot
import com.tubenime.app.ui.screens.settings.SettingsScreen
import com.tubenime.app.ui.screens.settings.UpdateBanner
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Host Compose layar Settings (menggantikan SettingsActivity XML).
 * UI 100% mengikuti Figma 17:1857 — 5 seksi (PREMIUM STATUS / APPEARANCE /
 * DOWNLOADS / LOGIN COOKIES / ABOUT & MOTOR). Logic diport utuh: premium,
 * palette (ThemeEngine), prefs download, wifi-only, login cookies, versi.
 */
class ComposeSettingsActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private val premiumActive = mutableStateOf(false)
    private val premiumTitle = mutableStateOf("FREE TIER")
    private val premiumSubtitle = mutableStateOf("3/3 Daily Quota remaining")
    private val paletteIndex = mutableStateOf(0)
    private val videoQuality = mutableStateOf("720p")
    private val wifiOnly = mutableStateOf(false)
    private val loginStates = mutableStateOf(mapOf<String, Boolean>())
    private val updateBanner = mutableStateOf<UpdateBanner?>(null)
    private val rewardBusy = mutableStateOf(false)
    private val rewardButtonLabel = mutableStateOf("WATCH AD → 30 MIN PRO")
    private val rewardCountdown = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {

        super.attachBaseContext(newBase)

        ThemeEngine.applyTheme(this)

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("tubenime_prefs", MODE_PRIVATE)
        PremiumManager.init(this)
        // Desain tidak punya toggle dark mode: hormati pref tersimpan sekali jalan.
        com.tubenime.app.ui.theme.darkModeOverride.value =
            prefs.getInt("night_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES

        setContent {
            TubeNimeTheme {
                SettingsScreen(
                    isPremium = premiumActive.value,
                    premiumTitle = premiumTitle.value,
                    premiumSubtitle = premiumSubtitle.value,
                    onUpgradeClick = { NavAnim.go(this, Intent(this, ComposePremiumActivity::class.java), NavAnim.TAB_SETTINGS, NavAnim.TAB_NONE) },
                    rewardButtonVisible = !premiumActive.value && !SecurityGuard.isTampered(this),
                    rewardButtonEnabled = !rewardBusy.value && RewardedAdManager.canOffer(this),
                    rewardButtonLabel = rewardButtonLabel.value,
                    rewardCountdownLabel = rewardCountdown.value,
                    onWatchAdClick = ::onWatchAd,
                    palettes = designPalettes(),
                    selectedPalette = paletteIndex.value,
                    onPaletteSelect = ::onPaletteSelected,
                    selectedQuality = qualityIndex(),
                    onQualitySelect = ::onQualitySelected,
                    storageText = storageLabel(),
                    onChangeStorage = ::pickStorage,
                    wifiOnly = wifiOnly.value,
                    onWifiToggle = {
                        val v = !prefs.getBoolean("wifi_only", false)
                        prefs.edit().putBoolean("wifi_only", v).apply()
                        wifiOnly.value = v
                    },
                    cookies = cookieSlots(),
                    onCookieClick = { guardPremiumLogin(it.platform) },
                    versionTitle = "${versionName()} Shōnen Engine",
                    versionSubtitle = "Build: ${versionCode()}-InkRoll",
                    onClearCache = ::clearCache,
                    updateBanner = updateBanner.value,
                    onUpdateClick = ::openUpdateUrl,
                    selectedNavIndex = 3, // tab Settings aktif
                    onNavSelected = ::openNav,
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeEngine.checkRecreate(this)
        PremiumManager.refreshPurchases()
        refreshAll()
        refreshUpdateBanner()
        RewardedAdManager.preload(this)
    }

    private fun onWatchAd() {
        if (rewardBusy.value) return
        if (!RewardedAdManager.canOffer(this)) {
            val waitSec = (RewardedAdManager.cooldownRemainingMs(this) / 1000).coerceAtLeast(1)
            Toast.makeText(this, "Ad ready again in ${waitSec}s.", Toast.LENGTH_SHORT).show()
            refreshAll()
            return
        }
        rewardBusy.value = true
        rewardButtonLabel.value = "LOADING AD…"
        RewardedAdManager.loadAndShow(this) { outcome ->
            runOnUiThread {
                rewardBusy.value = false
                rewardButtonLabel.value = "WATCH AD → 30 MIN PRO"
                when (outcome) {
                    is RewardedAdManager.Outcome.Rewarded ->
                        Toast.makeText(this, "PRO unlocked for 30 min. Enjoy!", Toast.LENGTH_LONG).show()
                    is RewardedAdManager.Outcome.SkippedCooldown ->
                        Toast.makeText(this, "Ad ready again soon.", Toast.LENGTH_SHORT).show()
                    is RewardedAdManager.Outcome.NotReady ->
                        Toast.makeText(this, "Ad not ready — check connection, try again.", Toast.LENGTH_LONG).show()
                    is RewardedAdManager.Outcome.FailedToShow ->
                        Toast.makeText(this, "Couldn't show ad — try again.", Toast.LENGTH_LONG).show()
                    is RewardedAdManager.Outcome.Dismissed ->
                        Toast.makeText(this, "Watch till the end to earn PRO.", Toast.LENGTH_SHORT).show()
                }
                refreshAll()
            }
        }
    }

    private fun refreshUpdateBanner() {
        val cached = UpdateChecker.cachedInfo(this)
        if (cached != null) {
            updateBanner.value = UpdateBanner(
                latestVersion = cached.latestVersion,
                currentVersion = cached.currentVersion,
                notes = cached.notes,
            )
        } else {
            updateBanner.value = null
        }
        // Tarik info terbaru (throttled 6 jam oleh UpdateChecker).
        UpdateChecker.checkAsync(this) { result ->
            if (result is UpdateChecker.Result.Ok) {
                runOnUiThread {
                    updateBanner.value = UpdateBanner(
                        latestVersion = result.info.latestVersion,
                        currentVersion = result.info.currentVersion,
                        notes = result.info.notes,
                    )
                }
            }
        }
    }

    private fun openUpdateUrl() {
        val cached = UpdateChecker.cachedInfo(this)
        val url = cached?.htmlUrl ?: "https://github.com/rainkidz/dropdesk/releases/latest"
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            Toast.makeText(this, "Browser unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Model desain ──────────────────────────────────────────────

    /**
     * Desain hanya punya 1 tema (Vintage). 5 slot lain disembunyikan sampai
     * ada desainnya — kembalikan 6 slot + kunci engine bila sudah ada.
     */
    private fun designPalettes(): List<PaletteSlot> = listOf(
        PaletteSlot("Vintage", androidx.compose.ui.graphics.Color(0xFFFAF3E7), ThemeEngine.PAPER),
    )

    private val qualities = listOf("1080p", "720p", "480p")

    private fun qualityIndex(): Int =
        qualities.indexOf(prefs.getString("video_quality", "720p")).coerceAtLeast(0)

    private fun onQualitySelected(index: Int) {
        if (index !in qualities.indices) return
        prefs.edit().putString("video_quality", qualities[index]).apply()
        videoQuality.value = qualities[index]
    }

    private fun storageLabel(): String = when (prefs.getString("download_location", "Downloads/TubeNime")) {
        "Downloads" -> "Internal / Downloads"
        else -> "Internal / TubeNime / Anime"
    }

    private fun pickStorage() {
        val options = listOf("Downloads/TubeNime", "Downloads")
        val current = prefs.getString("download_location", "Downloads/TubeNime") ?: options[0]
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TubeNime)
            .setTitle("Storage Location")
            .setSingleChoiceItems(options.toTypedArray(), options.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                prefs.edit().putString("download_location", options[which]).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cookieSlots(): List<CookieSlot> {
        val states = loginStates.value
        fun slot(platform: String, label: String, badge: String, badgeColor: androidx.compose.ui.graphics.Color, activeText: String): CookieSlot {
            val on = states[platform] == true
            return CookieSlot(platform, label, badge, badgeColor, connected = on, statusText = activeText)
        }
        return listOf(
            slot(CookieLoginStore.PLATFORM_YOUTUBE, "YouTube", "YT", androidx.compose.ui.graphics.Color(0xFFB7131A), "Active"),
            slot(CookieLoginStore.PLATFORM_INSTAGRAM, "Instagram", "IG", androidx.compose.ui.graphics.Color(0xFFBA1A1A), "Active"),
            slot(CookieLoginStore.PLATFORM_TIKTOK, "TikTok", "TT", androidx.compose.ui.graphics.Color(0xFF333028), "Connected"),
        )
    }

    // ── Refresh ─────────────────────────────────────────────────

    private fun refreshAll() {
        paletteIndex.value = designPalettes().indexOfFirst {
            it.engineKey == ThemeEngine.current(this)
        }.coerceAtLeast(0)
        videoQuality.value = prefs.getString("video_quality", "720p") ?: "720p"
        wifiOnly.value = prefs.getBoolean("wifi_only", false)

        if (SecurityGuard.isTampered(this)) {
            premiumActive.value = false
            premiumTitle.value = "MODIFIED BUILD"
            premiumSubtitle.value = "Premium disabled on this build"
            rewardCountdown.value = null
        } else {
            premiumActive.value = PremiumManager.isPremium()
            if (premiumActive.value) {
                premiumTitle.value = "PRO"
                val source = PremiumManager.sourceLabel()
                premiumSubtitle.value = "Active" + (if (source.isNotEmpty()) " ($source)" else "")
                rewardCountdown.value = PremiumManager.rewardedRemainingLabel()?.let { "Sisa PRO: $it" }
            } else {
                premiumTitle.value = "FREE TIER"
                premiumSubtitle.value = "Watch ad → PRO 30 min • 720p max"
                val cooldownMs = RewardedAdManager.cooldownRemainingMs(this)
                rewardCountdown.value = if (cooldownMs > 0L) {
                    "Ready again in ${cooldownMs / 1000}s"
                } else {
                    null
                }
            }
        }

        loginStates.value = mapOf(
            CookieLoginStore.PLATFORM_YOUTUBE to CookieLoginStore.hasCookies(this, CookieLoginStore.PLATFORM_YOUTUBE),
            CookieLoginStore.PLATFORM_INSTAGRAM to CookieLoginStore.hasCookies(this, CookieLoginStore.PLATFORM_INSTAGRAM),
            CookieLoginStore.PLATFORM_TIKTOK to CookieLoginStore.hasCookies(this, CookieLoginStore.PLATFORM_TIKTOK),
        )
    }

    // ── Navigasi bottom nav (tab Settings aktif) ──

    private fun openNav(index: Int) {
        if (index == 3) return // sudah di Settings
        val target = when (index) {
            0 -> ComposeHomeActivity::class.java
            1 -> ComposeBrowserActivity::class.java
            2 -> ComposeDownloadsActivity::class.java
            else -> return
        }
        NavAnim.go(
            this,
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            NavAnim.TAB_SETTINGS,
            index,
        )
    }

    // ── Helpers ─────────────────────────────────────────────────

    /** Blokir aksi premium-only + arahkan ke reward (bukan paid upsell). Return true bila boleh lanjut. */
    private fun guardPremium(): Boolean {
        if (PremiumManager.isPremium()) return true
        Toast.makeText(this, "Watch a short ad above to unlock PRO for 30 min.", Toast.LENGTH_LONG).show()
        return false
    }

    private fun guardPremiumLogin(platform: String) {
        if (!guardPremium()) return
        startActivity(
            Intent(this, ComposeCookieLoginActivity::class.java)
                .putExtra(CookieLoginStore.EXTRA_PLATFORM, platform)
        )
    }

    private fun onPaletteSelected(index: Int) {
        val slots = designPalettes()
        if (index !in slots.indices) return
        paletteIndex.value = index
        ThemeEngine.save(this, slots[index].engineKey)
        ThemeEngine.checkRecreate(this) // instant visual feedback
    }

    private fun clearCache() {
        val ok = try {
            var deleted = 0L
            cacheDir.walkTopDown().filter { it.isFile }.forEach {
                deleted += it.length()
                it.delete()
            }
            externalCacheDir?.walkTopDown()?.filter { it.isFile }?.forEach {
                deleted += it.length()
                it.delete()
            }
            deleted
        } catch (_: Exception) {
            -1L
        }
        Toast.makeText(
            this,
            if (ok >= 0) "Cache cleared" else "Cannot clear cache",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun versionName(): String = try {
        "v${packageManager.getPackageInfo(packageName, 0).versionName}"
    } catch (e: Exception) {
        "v4.3.0"
    }

    private fun versionCode(): Long = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }
}
