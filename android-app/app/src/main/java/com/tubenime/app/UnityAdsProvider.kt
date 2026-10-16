package com.tubenime.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Unity Ads (lapis 1 monetisasi) — rewarded + interstitial.
 *
 * Dipakai karena AdMob rewarded no-fill sebelum app ter-link ke store yang
 * didukung ("Publisher data not found"). Unity tidak mensyaratkan store
 * listing untuk menayangkan iklan.
 *
 * Memakai API static klasik SDK 4.12 (initialize/load/show per placement).
 * SDK 4.19+ sengaja TIDAK dipakai: metadata Kotlin 2.1-nya tidak bisa dibaca
 * toolchain Kotlin 1.9 proyek ini.
 *
 * Rewarded: reward dihitung bila onUnityAdsShowComplete == COMPLETED.
 * (SKIPPED = user menutup sebelum selesai → Dismissed, tanpa grant.)
 *
 * Banner TIDAK lewat sini (tetap AdMob AdView di Home/Downloads).
 *
 * Setup (sekali, oleh pemilik akun Unity):
 *  1. https://dashboard.unity3d.com → Monetization → New Project (Android).
 *  2. Catat Game ID + Placement ID rewarded & interstitial.
 *  3. Ganti 3 konstanta di bawah, rebuild.
 *
 * Sebelum ID diisi (isConfigured() == false): semua fungsi aman no-op dan
 * pemanggil jatuh kembali ke alur AdMob. Jadi commit ini tidak merusak
 * build/QA yang belum punya akun Unity.
 */
object UnityAdsProvider {

    private const val TAG = "UnityAdsProvider"

    // ── GANTI DENGAN ID ASLI DARI UNITY DASHBOARD ──────────────────────
    const val GAME_ID = "REPLACE_WITH_UNITY_GAME_ID"
    const val REWARDED_PLACEMENT_ID = "rewardedVideo"
    const val INTERSTITIAL_PLACEMENT_ID = "video"
    // ──────────────────────────────────────────────────────────────────
    // "rewardedVideo"/"video" = placement default proyek Unity baru; kalau
    // dashboard-mu membuat nama lain (mis. Rewarded_Android), sesuaikan.

    /** True bila pemilik sudah mengisi Game ID asli. */
    fun isConfigured(): Boolean =
        GAME_ID.isNotBlank() && GAME_ID != "REPLACE_WITH_UNITY_GAME_ID"

    @Volatile private var initialized = false
    @Volatile private var initOk = false

    @Volatile private var rewardedReady = false
    @Volatile private var loadingRewarded = false

    @Volatile private var interstitialReady = false
    @Volatile private var loadingInterstitial = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    /** Idempoten. testMode otomatis ikut build: debug = iklan test. */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        if (!isConfigured()) {
            Log.w(TAG, "Unity Game ID belum diisi — provider nonaktif, fallback AdMob.")
            return
        }
        try {
            UnityAds.initialize(
                context.applicationContext,
                GAME_ID,
                BuildConfig.DEBUG,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        initOk = true
                        Log.d(TAG, "Unity Ads initialized (testMode=${BuildConfig.DEBUG})")
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String,
                    ) {
                        initOk = false
                        Log.w(TAG, "Unity init failed: [$error] $message")
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unity init exception", e)
        }
    }

    private fun canLoad(): Boolean =
        isConfigured() && initOk && AdsManager.adsEnabled()

    // ── Rewarded ───────────────────────────────────────────────────────

    /** Pre-load rewarded di background. Panggil dari Activity.onResume. */
    fun preloadRewarded() {
        if (!canLoad()) return
        if (rewardedReady || loadingRewarded) return
        loadingRewarded = true
        try {
            UnityAds.load(REWARDED_PLACEMENT_ID, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    rewardedReady = true
                    loadingRewarded = false
                    Log.d(TAG, "unity rewarded loaded")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    rewardedReady = false
                    loadingRewarded = false
                    Log.w(TAG, "unity rewarded load failed: [$error] $message")
                }
            })
        } catch (e: Exception) {
            loadingRewarded = false
            Log.w(TAG, "unity rewarded load exception", e)
        }
    }

    sealed interface RewardedOutcome {
        data object Earned : RewardedOutcome
        data object NotReady : RewardedOutcome
        data object FailedToShow : RewardedOutcome
        /** Ditutup sebelum selesai (SKIPPED) atau error saat tayang. */
        data object Dismissed : RewardedOutcome
    }

    /** Tampilkan rewarded Unity. Callback selalu di main thread. */
    fun showRewarded(activity: Activity, onResult: (RewardedOutcome) -> Unit) {
        if (!canLoad() || !rewardedReady) {
            preloadRewarded()
            onMain { onResult(RewardedOutcome.NotReady) }
            return
        }
        rewardedReady = false
        try {
            UnityAds.show(
                activity,
                REWARDED_PLACEMENT_ID,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String,
                    ) {
                        Log.w(TAG, "unity rewarded show failed: [$error] $message")
                        onMain { onResult(RewardedOutcome.FailedToShow) }
                        preloadRewarded()
                    }

                    override fun onUnityAdsShowStart(placementId: String) = Unit
                    override fun onUnityAdsShowClick(placementId: String) = Unit

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState,
                    ) {
                        onMain {
                            onResult(
                                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                    RewardedOutcome.Earned
                                } else {
                                    RewardedOutcome.Dismissed
                                },
                            )
                        }
                        preloadRewarded()
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "unity rewarded show exception", e)
            onMain { onResult(RewardedOutcome.FailedToShow) }
            preloadRewarded()
        }
    }

    // ── Interstitial ───────────────────────────────────────────────────

    /** Pre-load interstitial di background. */
    fun preloadInterstitial() {
        if (!canLoad()) return
        if (interstitialReady || loadingInterstitial) return
        loadingInterstitial = true
        try {
            UnityAds.load(INTERSTITIAL_PLACEMENT_ID, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    interstitialReady = true
                    loadingInterstitial = false
                    Log.d(TAG, "unity interstitial loaded")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    interstitialReady = false
                    loadingInterstitial = false
                    Log.w(TAG, "unity interstitial load failed: [$error] $message")
                }
            })
        } catch (e: Exception) {
            loadingInterstitial = false
            Log.w(TAG, "unity interstitial load exception", e)
        }
    }

    /**
     * Tampilkan interstitial bila siap. @return true bila show() dipanggil
     * tanpa throw (gagal tayang dilaporkan async via onUnityAdsShowFailure).
     * Pembatasan frekuensi (60 dtk) tetap dipegang AdsManager.
     */
    fun showInterstitialIfReady(activity: Activity): Boolean {
        if (!canLoad() || !interstitialReady) {
            preloadInterstitial()
            return false
        }
        interstitialReady = false
        return try {
            UnityAds.show(
                activity,
                INTERSTITIAL_PLACEMENT_ID,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String,
                    ) {
                        Log.w(TAG, "unity interstitial show failed: [$error] $message")
                        preloadInterstitial()
                    }

                    override fun onUnityAdsShowStart(placementId: String) = Unit
                    override fun onUnityAdsShowClick(placementId: String) = Unit

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState,
                    ) {
                        preloadInterstitial()
                    }
                },
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "unity interstitial show exception", e)
            preloadInterstitial()
            false
        }
    }
}
