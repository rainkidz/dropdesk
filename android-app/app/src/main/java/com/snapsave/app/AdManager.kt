package com.snapsave.app

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages Google AdMob ads for the app.
 * - Banner ads at bottom of screen
 * - Interstitial ads after downloads
 */
object AdManager {

    private const val TAG = "AdManager"

    // Test Ad Unit IDs (replace with real IDs for production)
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isInitialized = false
    private var adsEnabled = true // Can be disabled for premium users

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Initialize AdMob SDK. Call once at app launch.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        scope.launch {
            MobileAds.initialize(context) { initializationStatus ->
                Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
                isInitialized = true
            }
        }
    }

    /**
     * Enable or disable ads (e.g., for premium users).
     */
    fun setAdsEnabled(enabled: Boolean) {
        adsEnabled = enabled
    }

    /**
     * Check if ads are enabled.
     */
    fun isAdsEnabled(): Boolean = adsEnabled

    /**
     * Create and load a banner ad.
     * @param activity The activity context
     * @param adContainer The FrameLayout to place the ad in
     */
    fun loadBannerAd(activity: Activity, adContainer: FrameLayout) {
        if (!adsEnabled) {
            adContainer.visibility = View.GONE
            return
        }

        val adView = AdView(activity).apply {
            adUnitId = BANNER_AD_UNIT_ID
            setAdSize(AdSize.BANNER)
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "Banner ad loaded")
                adContainer.removeAllViews()
                adContainer.addView(adView)
                adContainer.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Banner ad failed to load: ${error.message}")
                adContainer.visibility = View.GONE
            }

            override fun onAdOpened() {
                Log.d(TAG, "Banner ad opened")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Banner ad clicked")
            }
        }

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    /**
     * Load an interstitial ad. Call after app launch.
     */
    fun loadInterstitialAd(context: Context) {
        if (!adsEnabled) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "Interstitial ad loaded")
                interstitialAd = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Interstitial ad failed to load: ${error.message}")
                interstitialAd = null
            }
        })
    }

    /**
     * Show interstitial ad if available.
     * @param activity The activity to show the ad in
     * @param onAdDismissed Callback when ad is dismissed (proceed with action)
     * @param onAdFailed Callback if ad can't be shown (proceed anyway)
     */
    fun showInterstitialAd(
        activity: Activity,
        onAdDismissed: () -> Unit = {},
        onAdFailed: () -> Unit = {}
    ) {
        if (!adsEnabled) {
            onAdFailed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Log.d(TAG, "Interstitial ad not ready, proceeding")
            onAdFailed()
            // Preload next ad
            loadInterstitialAd(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
                onAdDismissed()
                // Preload next ad
                loadInterstitialAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial ad failed to show: ${error.message}")
                interstitialAd = null
                onAdFailed()
                // Preload next ad
                loadInterstitialAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad shown")
            }
        }

        ad.show(activity)
    }

    /**
     * Destroy banner ad when activity is destroyed.
     */
    fun destroyBannerAd(adContainer: FrameLayout) {
        adContainer.removeAllViews()
    }
}
