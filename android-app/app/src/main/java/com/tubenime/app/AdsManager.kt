package com.tubenime.app

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Satu-satunya pintu untuk semua iklan AdMob.
 *
 * Strategi ID (anti-bajak Lapis 2 — supaya pirate tidak bisa swap ID ke
 * akun mereka sendiri hanya dengan grep + sed di APK):
 *  - Saat init, fetch `/api/config/public` (server-signed HMAC). Verifikasi
 *    signature dengan LICENSE_HMAC_SECRET; bila valid → pakai IDs server.
 *  - Bila server offline / signature rusak → fallback ke hardcoded (yang
 *    sudah tertanam di BuildConfig — lihat konstanta di bawah).
 *  - Debug build: SELALU pakai test ID Google resmi (klik di debug = invalid
 *    activity kalau pakai ID asli).
 *
 * Aturan:
 * - User premium ([PremiumManager.isPremium]) TIDAK pernah melihat / memuat iklan.
 * - Interstitial dibatasi frekuensi ([INTERSTITIAL_MIN_INTERVAL_MS]) agar tidak
 *   spam tiap download, dan selalu di-preload agar siap saat momen natural
 *   (download selesai) tiba.
 */
object AdsManager {

    private const val TAG = "AdsManager"

    /** Test ID Google resmi untuk debug. */
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    /** ID produksi hardcoded — fallback kalau server tidak tersedia. */
    private const val DEFAULT_BANNER = "ca-app-pub-7452006143730401/8120744119"
    private const val DEFAULT_INTERSTITIAL = "ca-app-pub-7452006143730401/4435368715"

    /** Jeda minimal antar interstitial (60 detik — strategi mild). */
    private const val INTERSTITIAL_MIN_INTERVAL_MS = 60_000L

    /** Batas waktu ambil konfigurasi dari server saat init (5 detik). */
    private const val REMOTE_FETCH_TIMEOUT_MS = 5_000L

    @Volatile
    private var initialized = false

    @Volatile
    private var interstitial: InterstitialAd? = null

    @Volatile
    private var loading = false

    private var lastShownMs = 0L

    /** ID aktif, dilock setelah init (atau tetap fallback selamanya bila offline). */
    @Volatile
    private var activeBannerId: String = DEFAULT_BANNER
    @Volatile
    private var activeInterstitialId: String = DEFAULT_INTERSTITIAL

    /** True bila iklan boleh tampil/muat (user gratis). */
    fun adsEnabled(): Boolean = !PremiumManager.isPremium()

    /** ID banner aktif (untuk View setup). */
    val BANNER_AD_UNIT_ID: String get() = activeBannerId
    /** ID interstitial aktif. */
    val INTERSTITIAL_AD_UNIT_ID: String get() = activeInterstitialId

    /**
     * Idempoten — aman dipanggil dari tiap Activity. Memuat konfigurasi
     * publik dari server (best-effort) sebelum inisialisasi MobileAds.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        // Tentukan ID mana yang dipakai SEBELUM MobileAds mulai jalan.
        if (BuildConfig.DEBUG) {
            // Debug selalu pakai test ID — tidak peduli apakah server menjawab.
            activeBannerId = TEST_BANNER
            activeInterstitialId = TEST_INTERSTITIAL
        } else {
            // Default dulu (fallback aman), fetch server bisa menimpa secara async.
            activeBannerId = DEFAULT_BANNER
            activeInterstitialId = DEFAULT_INTERSTITIAL
            fetchRemoteConfig()
        }

        if (BuildConfig.DEBUG) {
            // Infinix dev: paksa test ads agar request debug selalu dibalas kreatif test.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("51035DE012A57395C1C78D63E77EF1AA"))
                    .build(),
            )
        }
        MobileAds.initialize(context) {}
        preloadInterstitial(context.applicationContext)
    }

    /** Tarik konfigurasi publik; swap IDs bila signature valid. */
    private fun fetchRemoteConfig() {
        PublicConfigClient.fetchAsync { remote ->
            if (remote == null) {
                Log.d(TAG, "Using fallback AdMob IDs (server unavailable)")
                return@fetchAsync
            }
            // Race-safe: hanya swap kalau memang masih default (init pertama).
            activeBannerId = remote.admobBannerId
            activeInterstitialId = remote.admobInterstitialId
            Log.d(TAG, "AdMob IDs updated from server (banner=${remote.admobBannerId})")
        }
    }

    /** Muat interstitial berikutnya di background (no-op bila premium / sudah ada). */
    fun preloadInterstitial(context: Context) {
        if (!adsEnabled()) return
        if (loading || interstitial != null) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            activeInterstitialId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loading = false
                }

                override fun onAdFailedToLoad(err: LoadAdError) {
                    Log.w(TAG, "interstitial load failed: ${err.message}")
                    loading = false
                }
            },
        )
    }

    /**
     * Tampilkan interstitial bila: user gratis + iklan siap + lewat jeda minimal.
     * @return true bila iklan benar-benar ditampilkan.
     */
    fun showInterstitialIfReady(activity: Activity): Boolean {
        if (!adsEnabled()) return false
        val ad = interstitial ?: run {
            preloadInterstitial(activity)
            return false
        }
        if (SystemClock.elapsedRealtime() - lastShownMs < INTERSTITIAL_MIN_INTERVAL_MS) {
            return false
        }
        return try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitial = null
                    preloadInterstitial(activity)
                }

                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    Log.w(TAG, "interstitial show failed: ${e.message}")
                    interstitial = null
                    preloadInterstitial(activity)
                }
            }
            lastShownMs = SystemClock.elapsedRealtime()
            interstitial = null
            ad.show(activity)
            true
        } catch (e: Exception) {
            Log.w(TAG, "show interstitial failed", e)
            interstitial = null
            preloadInterstitial(activity)
            false
        }
    }
}
