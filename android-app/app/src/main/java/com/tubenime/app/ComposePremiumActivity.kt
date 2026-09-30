package com.tubenime.app

import android.content.Context

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.mutableStateOf
import com.tubenime.app.ui.screens.premium.PremiumBenefit
import com.tubenime.app.ui.screens.premium.PremiumScreen
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.TubeNimeTheme
import kotlin.concurrent.thread

/**
 * Host Compose layar Premium (menggantikan PremiumActivity XML).
 * Logic diport utuh: Play Billing via [PremiumManager] (monthly/yearly/restore),
 * listener onPremiumChanged → state, debug test toggle (debug builds),
 * setResult(RESULT_OK) saat kembali dengan premium aktif.
 */
class ComposePremiumActivity : ComponentActivity(), PremiumManager.Listener {

    private val isPremium = mutableStateOf(false)
    private val statusLabel = mutableStateOf("")
    private val monthlyPrice = mutableStateOf("")
    private val yearlyPrice = mutableStateOf("")
    private val debugPremium = mutableStateOf(false)
    private val licenseError = mutableStateOf<String?>(null)
    private val autoPayBusy = mutableStateOf(false)
    private val autoPayStatus = mutableStateOf<String?>(null)

    @Volatile
    private var polling = false

    private val benefits = listOf(
        PremiumBenefit("Video+Audio merged up to 1080p+", "MERGE", ActionRed, android.graphics.Color.WHITE.toCompose()),
        PremiumBenefit("Playlist & batch downloads", "BATCH", HighlightYellow, Ink),
        PremiumBenefit("Private content & 20-slot queue", "PRIVATE", Ink, Paper),
    )

    override fun attachBaseContext(newBase: Context) {

        super.attachBaseContext(newBase)

        ThemeEngine.applyTheme(this)

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PremiumManager.init(this)

        setContent {
            TubeNimeTheme {
                PremiumScreen(
                    benefits = benefits,
                    isPremium = isPremium.value,
                    statusLabel = statusLabel.value,
                    priceMonthly = monthlyPrice.value.ifEmpty { "Rp 39.000 / bulan" },
                    priceYearly = yearlyPrice.value.ifEmpty { "Rp 390.000 / tahun" },
                    debugVisible = PremiumManager.isDebugBuild(),
                    debugPremium = debugPremium.value,
                    licenseVisible = LicenseManager.isConfigured(),
                    licenseError = licenseError.value,
                    payLocalTitle = "Lokal — ${LicenseConfig.PRICE_LOCAL} (QRIS)",
                    payLocalBody = LicenseConfig.PAY_LOCAL,
                    payIntlTitle = "Luar negeri — ${LicenseConfig.PRICE_INTL} (PayPal)",
                    payIntlBody = "${LicenseConfig.PAYPAL_LINK}\n${LicenseConfig.SELLER_CONTACT}",
                    sellerContact = "",
                    autoPayVisible = BillingRepository.isEnabled(),
                    autoPayBusy = autoPayBusy.value,
                    autoPayStatus = autoPayStatus.value,
                    onBack = { finish() },
                    onBuyMonthly = { onBuyClicked(PremiumManager.PRODUCT_ID_MONTHLY) },
                    onBuyYearly = { onBuyClicked(PremiumManager.PRODUCT_ID_YEARLY) },
                    onRestore = ::restore,
                    onRedeem = ::redeem,
                    onPayAuto = ::payAuto,
                    onDebugToggle = { on ->
                        PremiumManager.setDebugPremium(on)
                        debugPremium.value = on
                        refresh()
                    },
                )
            }
        }

        // Kembali dengan premium aktif → beri tahu caller agar refresh UI-nya
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (PremiumManager.isPremium()) setResult(RESULT_OK)
                finish()
            }
        })

        PremiumManager.addListener(this)
        PremiumManager.refreshPurchases()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        PremiumManager.refreshPurchases()
        refresh()
    }

    override fun onDestroy() {
        PremiumManager.removeListener(this)
        super.onDestroy()
    }

    // PremiumManager.Listener
    override fun onPremiumChanged() {
        runOnUiThread { refresh() }
    }

    // ── Port dari PremiumActivity.refresh() ─────────────────────

    private fun refresh() {
        val premium = PremiumManager.isPremium()
        isPremium.value = premium
        if (premium) {
            val source = PremiumManager.sourceLabel()
            val until = PremiumManager.licensedUntilLabel()?.let { " • s/d $it" }.orEmpty()
            statusLabel.value = if (source.isNotEmpty()) "✅ Premium is active ($source$until). Enjoy!" else "✅ Premium is active. Enjoy!"
        } else {
            statusLabel.value = "Free plan — upgrade to unlock everything."
            // Sideload tanpa Play Billing: tampilkan harga lisensi manual.
            monthlyPrice.value = PremiumManager.monthlyPriceLabel().orEmpty()
                .ifEmpty { LicenseConfig.PRICE_LOCAL }
            yearlyPrice.value = PremiumManager.yearlyPriceLabel().orEmpty()
                .ifEmpty { LicenseConfig.PRICE_INTL }
        }
        debugPremium.value = PremiumManager.isDebugPremium()
    }

    private fun onBuyClicked(productId: String) {
        if (PremiumManager.isPremium()) {
            refresh()
            return
        }
        if (!PremiumManager.isBillingReady()) {
            val msg = if (PremiumManager.isDebugBuild()) {
                "Billing is unavailable here — use the test toggle."
            } else {
                "Purchases aren't available yet on this build."
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }
        val launched = PremiumManager.launchBillingFlow(this, productId)
        if (!launched) {
            Toast.makeText(this, "Couldn't start the purchase — try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun restore() {
        val started = PremiumManager.restorePurchases()
        if (started) {
            Toast.makeText(this, "Checking your purchases…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Play Billing isn't available on this install.", Toast.LENGTH_LONG).show()
        }
    }

    private fun redeem(code: String) {
        if (code.isBlank()) {
            licenseError.value = "Masukkan kode aktivasi dulu."
            return
        }
        when (PremiumManager.redeemLicense(code)) {
            LicenseManager.RedeemResult.Ok -> {
                licenseError.value = null
                Toast.makeText(this, "Premium aktif! Selamat menikmati.", Toast.LENGTH_SHORT).show()
                refresh()
            }
            LicenseManager.RedeemResult.BadFormat ->
                licenseError.value = "Format kode salah. Contoh: TN1-202510-AB12-CDEFGHJK"
            LicenseManager.RedeemResult.BadSignature ->
                licenseError.value = "Kode tidak valid. Periksa lagi hurufnya."
            LicenseManager.RedeemResult.Expired ->
                licenseError.value = "Kode sudah kedaluwarsa. Perpanjang untuk bulan berjalan."
            LicenseManager.RedeemResult.NotConfigured ->
                licenseError.value = "Redeem belum tersedia di build ini."
        }
    }

    // ── Bayar otomatis via Midtrans ─────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun payAuto() {
        if (autoPayBusy.value) return
        autoPayBusy.value = true
        autoPayStatus.value = "Membuka pembayaran…"
        thread(name = "midtrans-create") {
            val result = BillingRepository.create(1)
            mainHandler.post {
                result.fold(
                    onSuccess = { order ->
                        openCustomTab(order.redirectUrl)
                        autoPayStatus.value = "Menunggu pembayaran…"
                        pollUntilPaid(order.orderId)
                    },
                    onFailure = { e ->
                        autoPayBusy.value = false
                        autoPayStatus.value = "Gagal membuat pembayaran: ${e.message ?: "unknown"}"
                        Toast.makeText(this, "Tidak bisa membuka pembayaran. Coba lagi.", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    private fun openCustomTab(url: String) {
        try {
            val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
            intent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            Toast.makeText(this, "Browser belum terpasang untuk membuka pembayaran.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pollUntilPaid(orderId: String) {
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000L // 10 menit
        val runnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                if (!autoPayBusy.value) return // dibatalkan
                if (now >= deadline) {
                    autoPayBusy.value = false
                    autoPayStatus.value = "Waktu habis. Coba lagi."
                    Toast.makeText(this@ComposePremiumActivity, "Pembayaran kedaluwarsa.", Toast.LENGTH_LONG).show()
                    return
                }
                thread(name = "midtrans-poll") {
                    val state = BillingRepository.status(orderId)
                    mainHandler.post {
                        when (state) {
                            is BillingRepository.OrderState.Pending ->
                                autoPayStatus.value = "Menunggu pembayaran… (cek halaman Snap)"
                            is BillingRepository.OrderState.Paid -> {
                                when (PremiumManager.redeemLicense(state.licenseKey)) {
                                    LicenseManager.RedeemResult.Ok -> {
                                        autoPayBusy.value = false
                                        autoPayStatus.value = "✅ Premium aktif."
                                        Toast.makeText(this@ComposePremiumActivity, "Pembayaran sukses! Premium aktif.", Toast.LENGTH_LONG).show()
                                        refresh()
                                    }
                                    else -> {
                                        autoPayBusy.value = false
                                        autoPayStatus.value = "Bayar sukses, tapi redeem lokal gagal."
                                    }
                                }
                            }
                            is BillingRepository.OrderState.Ended -> {
                                autoPayBusy.value = false
                                autoPayStatus.value = "Pembayaran ${state.status}. Coba lagi."
                            }
                            is BillingRepository.OrderState.Error -> {
                                // Lanjut polling kecuali error fatal.
                                autoPayStatus.value = "Jaringan: ${state.message}. Coba lagi…"
                                mainHandler.postDelayed(this, 6000)
                                return@post
                            }
                        }
                        if (autoPayBusy.value) mainHandler.postDelayed(this, 5000)
                    }
                }
            }
        }
        mainHandler.postDelayed(runnable, 5000)
    }
}

/** Konversi kecil android int color → Compose Color utk badge putih. */
private fun Int.toCompose(): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(android.graphics.Color.red(this), android.graphics.Color.green(this), android.graphics.Color.blue(this))
