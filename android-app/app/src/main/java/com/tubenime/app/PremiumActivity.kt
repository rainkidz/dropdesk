package com.tubenime.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Premium upsell screen: lists benefits, offers subscription buttons,
 * restores purchases, and (on debug builds only) exposes the test toggle.
 */
class PremiumActivity : AppCompatActivity(), PremiumManager.Listener {

    private lateinit var premiumStatusText: TextView
    private lateinit var monthlyButton: com.google.android.material.button.MaterialButton
    private lateinit var yearlyButton: com.google.android.material.button.MaterialButton
    private lateinit var restoreButton: com.google.android.material.button.MaterialButton
    private lateinit var debugSection: View
    private lateinit var debugSwitch: SwitchMaterial
    private lateinit var debugStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        PremiumManager.init(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        premiumStatusText = findViewById(R.id.premiumStatusText)
        monthlyButton = findViewById(R.id.monthlyButton)
        yearlyButton = findViewById(R.id.yearlyButton)
        restoreButton = findViewById(R.id.restoreButton)
        debugSection = findViewById(R.id.debugSection)
        debugSwitch = findViewById(R.id.debugSwitch)
        debugStatusText = findViewById(R.id.debugStatusText)

        // Test toggle — debug builds only
        if (PremiumManager.isDebugBuild()) {
            debugSection.visibility = View.VISIBLE
            debugSwitch.isChecked = PremiumManager.isDebugPremium()
            debugSwitch.setOnCheckedChangeListener { _, isChecked ->
                PremiumManager.setDebugPremium(isChecked)
                debugStatusText.text = if (isChecked) {
                    "Premium is ON in test mode."
                } else {
                    "Premium is OFF in test mode."
                }
                refresh()
            }
        }

        monthlyButton.setOnClickListener { onBuyClicked(PremiumManager.PRODUCT_ID_MONTHLY) }
        yearlyButton.setOnClickListener { onBuyClicked(PremiumManager.PRODUCT_ID_YEARLY) }

        restoreButton.setOnClickListener {
            val started = PremiumManager.restorePurchases()
            if (started) {
                Toast.makeText(this, "Checking your purchases…", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Play Billing isn't available on this install.", Toast.LENGTH_LONG).show()
            }
        }

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

    private fun refresh() {
        if (isFinishing) return
        val premium = PremiumManager.isPremium()

        if (premium) {
            val source = PremiumManager.sourceLabel()
            premiumStatusText.text = if (source.isNotEmpty()) {
                "✅ Premium is active ($source). Enjoy!"
            } else {
                "✅ Premium is active. Enjoy!"
            }
            monthlyButton.isEnabled = false
            yearlyButton.isEnabled = false
            restoreButton.isEnabled = false
            if (PremiumManager.isDebugBuild() && debugSwitch.isChecked != true) {
                debugSwitch.isChecked = true
            }
        } else {
            premiumStatusText.text = "Free plan — upgrade to unlock everything."
            monthlyButton.isEnabled = true
            yearlyButton.isEnabled = true
            restoreButton.isEnabled = true

            // Show real prices once Play reports them; otherwise a neutral label.
            val monthlyPrice = PremiumManager.monthlyPriceLabel()
            monthlyButton.text = if (monthlyPrice != null) "Subscribe Monthly — $monthlyPrice" else "Subscribe Monthly"
            val yearlyPrice = PremiumManager.yearlyPriceLabel()
            yearlyButton.text = if (yearlyPrice != null) "Subscribe Yearly — $yearlyPrice" else "Subscribe Yearly"
        }
    }

    private fun onBuyClicked(productId: String) {
        if (PremiumManager.isPremium()) {
            refresh()
            return
        }
        // Debug builds (sideloaded APKs) cannot reach Play Billing — point at the toggle.
        if (!PremiumManager.isBillingReady()) {
            if (PremiumManager.isDebugBuild()) {
                Toast.makeText(this, "Billing is unavailable here — use the test toggle below.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Purchases aren't available yet on this build.", Toast.LENGTH_LONG).show()
            }
            return
        }
        val launched = PremiumManager.launchBillingFlow(this, productId)
        if (!launched) {
            Toast.makeText(this, "Couldn't start the purchase — try again.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // The billing flow itself doesn't go through onActivityResult — purchases
        // arrive on PurchasesUpdatedListener. This is only a safety net.
        PremiumManager.refreshPurchases()
    }

    override fun onBackPressed() {
        // If the user just bought premium, tell the caller so it can refresh its UI.
        if (PremiumManager.isPremium()) {
            setResult(RESULT_OK)
        }
        super.onBackPressed()
    }
}
