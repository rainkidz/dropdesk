package com.tubenime.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob Rewarded Video — "tonton iklan pendek, buka semua fitur PRO selama
 * 30 menit". Pemicu selalu dari user (tidak auto-play). Reward jelas
 * sebelum tampil: lihat tombol "🎬 Watch Ad → 30 min PRO" di Settings.
 *
 * Flow:
 *  1. Settings tap tombol → loadAndShow(activity, onResult).
 *  2. Klien load rewarded ad di background, tampil saat ready.
 *  3. User lihat video sampai selesai → onUserEarnedReward dipanggil →
 *     PremiumManager.grantRewardedPremium(30 menit).
 *  4. User close sebelum selesai → tidak ada reward, callback Failed.
 *
 * Cooldown: 1 menit antar reward agar tidak bisa spam. Disimpan di prefs.
 */
object RewardedAdManager {

    private const val TAG = "RewardedAdManager"

    /**
     * ID unit rewarded ad. Server-overridable via /api/config/public saat
     * field `admobRewardedId` ditambahkan (untuk sekarang fallback hardcoded).
     */
    val REWARDED_AD_UNIT_ID: String =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/5224354917"
        else "ca-app-pub-7452006143730401/9999999999" // TODO: replace dengan ID asli

    private const val REWARD_DURATION_MS = 30L * 60 * 1000 // 30 menit
    private const val COOLDOWN_MS = 60L * 1000 // 1 menit antar reward

    private const val PREF_NAME = "tubenime_rewarded"
    private const val PREF_LAST_REWARD_MS = "rewarded_last_granted_ms"
    private const val PREF_LAST_OFFER_MS = "rewarded_last_offer_ms"

    @Volatile private var rewardedAd: RewardedAd? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** True bila user bisa tap tombol reward sekarang (cooldown sudah lewat). */
    fun canOffer(context: Context): Boolean {
        val last = prefs(context).getLong(PREF_LAST_REWARD_MS, 0L)
        return System.currentTimeMillis() - last >= COOLDOWN_MS
    }

    /** Cooldown tersisa dalam ms (untuk UI countdown). 0 = siap. */
    fun cooldownRemainingMs(context: Context): Long {
        val last = prefs(context).getLong(PREF_LAST_REWARD_MS, 0L)
        val elapsed = System.currentTimeMillis() - last
        return (COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    /** Pre-load rewarded ad di background. Panggil dari Activity.onResume. */
    fun preload(context: Context) {
        if (!AdsManager.adsEnabled()) return
        if (rewardedAd != null) return
        RewardedAd.load(
            context.applicationContext,
            REWARDED_AD_UNIT_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "rewarded ad loaded")
                }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    Log.w(TAG, "rewarded load failed: ${err.message}")
                    rewardedAd = null
                }
            },
        )
    }

    sealed interface Outcome {
        data class Rewarded(val durationMs: Long) : Outcome
        data object SkippedCooldown : Outcome
        data object NotReady : Outcome
        data object FailedToShow : Outcome
        data object Dismissed : Outcome
    }

    /**
     * Load (kalau belum) + show rewarded ad. Callback di main thread.
     * @param activity harus Activity foreground (AdMob requirement).
     */
    fun loadAndShow(activity: Activity, onResult: (Outcome) -> Unit) {
        if (!AdsManager.adsEnabled()) {
            onResult(Outcome.NotReady)
            return
        }
        if (!canOffer(activity)) {
            onResult(Outcome.SkippedCooldown)
            return
        }
        prefs(activity).edit().putLong(PREF_LAST_OFFER_MS, System.currentTimeMillis()).apply()
        val existing = rewardedAd
        if (existing != null) {
            showRewardedAd(activity, existing, onResult)
        } else {
            // Lazy load + show
            RewardedAd.load(
                activity.applicationContext,
                REWARDED_AD_UNIT_ID,
                com.google.android.gms.ads.AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        showRewardedAd(activity, ad, onResult)
                    }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        Log.w(TAG, "lazy load failed: ${err.message}")
                        onResult(Outcome.NotReady)
                    }
                },
            )
        }
    }

    private fun showRewardedAd(activity: Activity, ad: RewardedAd, onResult: (Outcome) -> Unit) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                // onResult sudah dipanggil di onUserEarnedReward / di bawah
                preload(activity)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                Log.w(TAG, "rewarded show failed: ${e.message}")
                rewardedAd = null
                onResult(Outcome.FailedToShow)
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { reward ->
            Log.d(TAG, "user earned reward: ${reward.amount} ${reward.type}")
            val untilMs = System.currentTimeMillis() + REWARD_DURATION_MS
            prefs(activity).edit()
                .putLong(PREF_LAST_REWARD_MS, System.currentTimeMillis())
                .putLong("rewarded_until_ms", untilMs)
                .apply()
            PremiumManager.grantRewardedPremium(REWARD_DURATION_MS)
            onResult(Outcome.Rewarded(REWARD_DURATION_MS))
        })
    }

    /** Bilang PremiumManager berapa lama reward berlaku (untuk UI countdown). */
    fun rewardExpiryMs(context: Context): Long =
        prefs(context).getLong("rewarded_until_ms", 0L)

    /** Helper: clear rewarded state (untuk testing atau Settings reset). */
    fun clearRewarded(context: Context) {
        prefs(context).edit().clear().apply()
        PremiumManager.clearRewardedPremium()
    }
}
