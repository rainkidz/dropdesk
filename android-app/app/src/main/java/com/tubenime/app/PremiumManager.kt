package com.tubenime.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single source of truth for the premium entitlement.
 *
 * Premium is granted when ANY of these is true:
 *  1. Google Play reports an active subscription for one of our product IDs
 *     (queried on start / resume and refreshed after every purchase).
 *  2. The debug toggle is switched on — only possible on debuggable builds,
 *     so demoing premium on sideloaded APKs (which cannot talk to Play Billing)
 *     is easy while release builds are unaffected.
 */
object PremiumManager {

    private const val TAG = "PremiumManager"

    // ── Subscription product IDs (create these in Play Console with the same IDs) ──
    const val PRODUCT_ID_MONTHLY = "tubenime_premium_monthly"
    const val PRODUCT_ID_YEARLY = "tubenime_premium_yearly"

    const val PREFS_NAME = "tubenime_prefs"
    private const val PREF_DEBUG_PREMIUM = "premium_debug"
    private const val PREF_SERVER_UNTIL_MS = "premium_server_until_ms"
    private const val PREF_REMOTE_VERDICT = "premium_remote_verdict"
    private const val PREF_LAST_ATTEST_MS = "premium_last_attest_ms"

    /** How often Play Integrity attestation is re-run (throttle). */
    private const val ATTEST_THROTTLE_MS = 12L * 60 * 60 * 1000

    /** Called on the main thread whenever the premium state may have changed. */
    interface Listener {
        fun onPremiumChanged()
    }

    @Volatile
    private var purchased = false
    @Volatile
    private var billingAvailable = false
    @Volatile
    private var productDetailsLoaded = false
    @Volatile
    private var monthlyProduct: ProductDetails? = null
    @Volatile
    private var yearlyProduct: ProductDetails? = null

    private var billingClient: BillingClient? = null
    private var appContext: Context? = null
    private var isDebuggable = false

    /** Cached result of the client-side signature check (see SecurityGuard). */
    @Volatile
    private var installTampered = false

    /**
     * Result of the last server-side Play Integrity attestation (only relevant
     * when an enforcement server is configured). Defaults to trusted when the
     * server is disabled; once a verdict arrives it is persisted.
     */
    @Volatile
    private var remoteVerdictOk = true

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Init ───────────────────────────────────────────────────────────────────────

    /** Call once from an early activity (Splash/Main). Idempotent. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Anti-mod: a re-signed release APK fails the signature check and can
        // never hold premium, regardless of purchases or the debug toggle.
        installTampered = SecurityGuard.isTampered(appContext!!)
        remoteVerdictOk = prefs()?.getBoolean(PREF_REMOTE_VERDICT, !PremiumConfig.isAttestationEnabled()) ?: true

        connectBilling()
    }

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Public state ────────────────────────────────────────────────────────────────

    fun isPremium(): Boolean {
        if (installTampered) return false
        // Server attestation only gates shipped (non-debug) builds.
        if (!isDebuggable && !remoteVerdictOk) return false
        return isDebugPremium() || (billingAvailable && purchased)
    }

    /** Whether this build was compiled debuggable (only those expose the test toggle). */
    fun isDebugBuild(): Boolean = isDebuggable

    fun isDebugPremium(): Boolean {
        if (!isDebuggable || installTampered) return false
        return prefs()?.getBoolean(PREF_DEBUG_PREMIUM, false) ?: false
    }

    /** True when the installed APK failed the release-signature check. */
    fun isInstallTampered(): Boolean = installTampered

    /**
     * Switch the developer/test premium toggle. Only takes effect on debuggable
     * builds, so it can never unlock premium in a release APK.
     */
    fun setDebugPremium(enabled: Boolean) {
        if (!isDebuggable || installTampered) {
            Log.w(TAG, "Ignoring debug premium toggle on a non-debuggable or modified build")
            return
        }
        prefs()?.edit()?.putBoolean(PREF_DEBUG_PREMIUM, enabled)?.apply()
        notifyChanged()
    }

    /** True when Google Play Billing is connected and usable on this install. */
    fun isBillingReady(): Boolean = billingAvailable

    fun isProductDetailsLoaded(): Boolean = productDetailsLoaded

    fun monthlyDetails(): ProductDetails? = monthlyProduct

    fun yearlyDetails(): ProductDetails? = yearlyProduct

    /** Best-effort formatted price (e.g. "Rp 25.000/month") or null if not loaded. */
    fun monthlyPriceLabel(): String? = basePriceLabel(monthlyProduct)

    fun yearlyPriceLabel(): String? = basePriceLabel(yearlyProduct)

    private fun basePriceLabel(product: ProductDetails?): String? {
        val offer = product?.subscriptionOfferDetails?.firstOrNull() ?: return null
        return offer.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    }

    /** Human-readable description of why premium is currently active. */
    fun sourceLabel(): String = when {
        isDebugPremium() -> "Test mode"
        billingAvailable && purchased -> "Subscription"
        else -> ""
    }

    // ── Listeners ──────────────────────────────────────────────────────────────────

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        mainHandler.post {
            listeners.forEach { l ->
                runCatching { l.onPremiumChanged() }
            }
        }
    }

    // ── Billing ────────────────────────────────────────────────────────────────────

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    Log.d(TAG, "No purchases returned")
                } else {
                    purchases.forEach { handlePurchase(it) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Log.d(TAG, "Purchase canceled by user")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Happens when the user taps buy while a subscription is already active.
                Log.d(TAG, "Item already owned — refreshing")
                refreshPurchases()
            }
            else -> Log.w(TAG, "Purchase failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
        }
    }

    private fun connectBilling() {
        val context = appContext ?: return
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingAvailable = true
                    Log.d(TAG, "Billing connected")
                    refreshPurchases()
                    refreshProductDetails()
                } else {
                    billingAvailable = false
                    Log.w(TAG, "Billing unavailable: ${billingResult.responseCode} — likely a sideloaded APK")
                    notifyChanged()
                }
            }

            override fun onBillingServiceDisconnected() {
                billingAvailable = false
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(params, object : AcknowledgePurchaseResponseListener {
                    override fun onAcknowledgePurchaseResponse(billingResult: BillingResult) {
                        Log.d(TAG, "Acknowledge result: ${billingResult.responseCode}")
                    }
                })
            }
            setPurchased(true)
        } else {
            // PENDING or UNSPECIFIED_STATE — no entitlement yet.
            Log.d(TAG, "Purchase not active yet (state=${purchase.purchaseState})")
        }
    }

    private fun setPurchased(value: Boolean) {
        val changed = purchased != value
        purchased = value
        if (changed) notifyChanged()
    }

    /** Re-query Play for the current subscription state (called on resume too). */
    fun refreshPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            val activePurchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (activePurchase != null && PremiumConfig.isServerVerificationEnabled()) {
                // A server is configured — re-validate the purchase token with it.
                Thread {
                    val productId = activePurchase.products.firstOrNull() ?: PRODUCT_ID_MONTHLY
                    val granted = when (val outcome = RemotePremiumVerifier.verifySubscriptionBlocking(
                        packageName = PremiumConfig.ENFORCEMENT_PACKAGE,
                        productId = productId,
                        purchaseToken = activePurchase.purchaseToken
                    )) {
                        is RemotePremiumVerifier.VerifyOutcome.Ok -> {
                            outcome.expiresAtEpochMs?.let { storeServerUntil(it) }
                            true
                        }
                        is RemotePremiumVerifier.VerifyOutcome.Rejected -> {
                            Log.w(TAG, "Server rejected subscription: ${outcome.reason}")
                            false
                        }
                        is RemotePremiumVerifier.VerifyOutcome.Unreachable -> {
                            // Offline grace: trust a previously verified expiry.
                            serverCacheValid()
                        }
                    }
                    setPurchased(granted)
                }.start()
            } else {
                val active = billingResult.responseCode == BillingClient.BillingResponseCode.OK && activePurchase != null
                setPurchased(active)
            }
        }
    }

    /**
     * Re-attests the install with Play Integrity (server-side decoding).
     * Called once per app launch, throttled to [ATTEST_THROTTLE_MS].
     */
    fun runInstallAttestation(context: Context) {
        if (!PremiumConfig.isAttestationEnabled()) return
        val ctx = context.applicationContext
        val lastRun = prefs()?.getLong(PREF_LAST_ATTEST_MS, 0L) ?: 0L
        if (System.currentTimeMillis() - lastRun < ATTEST_THROTTLE_MS) return
        prefs()?.edit()?.putLong(PREF_LAST_ATTEST_MS, System.currentTimeMillis())?.apply()

        PlayIntegrityReporter.requestAndReport(
            ctx,
            PremiumConfig.ENFORCEMENT_PACKAGE,
            object : PlayIntegrityReporter.Callback {
                override fun onResult(outcome: RemotePremiumVerifier.AttestOutcome) {
                    when (outcome) {
                        is RemotePremiumVerifier.AttestOutcome.Verified -> storeRemoteVerdict(true)
                        is RemotePremiumVerifier.AttestOutcome.Rejected -> {
                            Log.w(TAG, "Attestation rejected: ${outcome.reason}")
                            storeRemoteVerdict(false)
                        }
                        is RemotePremiumVerifier.AttestOutcome.Unavailable -> {
                            // Cannot attest right now — keep the last known verdict.
                            Log.d(TAG, "Attestation unavailable: ${outcome.message}")
                        }
                    }
                }
            }
        )
    }

    private fun storeServerUntil(epochMs: Long) {
        if (epochMs <= 0) return
        prefs()?.edit()?.putLong(PREF_SERVER_UNTIL_MS, epochMs)?.apply()
    }

    private fun serverCacheValid(): Boolean {
        val until = prefs()?.getLong(PREF_SERVER_UNTIL_MS, 0L) ?: 0L
        return until > System.currentTimeMillis()
    }

    private fun storeRemoteVerdict(ok: Boolean) {
        remoteVerdictOk = ok
        prefs()?.edit()?.putBoolean(PREF_REMOTE_VERDICT, ok)?.apply()
        notifyChanged()
    }

    private fun refreshProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID_YEARLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                productDetailsList.forEach { details ->
                    when (details.productId) {
                        PRODUCT_ID_MONTHLY -> monthlyProduct = details
                        PRODUCT_ID_YEARLY -> yearlyProduct = details
                    }
                }
                productDetailsLoaded = true
                notifyChanged()
            } else {
                Log.w(TAG, "Product details query failed: ${billingResult.responseCode}")
            }
        }
    }

    /** Restore purchases from Play (usually a button in the premium screen). */
    fun restorePurchases(): Boolean {
        return if (billingAvailable) {
            refreshPurchases()
            true
        } else {
            false
        }
    }

    /**
     * Launch the Play purchase dialog for the given product.
     * @return false if billing/products are not ready (caller should show a message).
     */
    fun launchBillingFlow(activity: Activity, productId: String): Boolean {
        val client = billingClient ?: return false
        if (!client.isReady) return false
        val product = when (productId) {
            PRODUCT_ID_MONTHLY -> monthlyProduct
            PRODUCT_ID_YEARLY -> yearlyProduct
            else -> null
        } ?: return false

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        Log.d(TAG, "launchBillingFlow($productId) -> ${result.responseCode}")
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }
}
