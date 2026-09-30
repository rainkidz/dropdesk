package com.tubenime.app

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        ThemeEngine.applyTheme(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.tubenime.app.ui.theme.initDarkModeOverride(this) // muat pref dark mode utk semua host Compose
        PremiumManager.init(this) // state premium/tamper siap sebelum layar mana pun
        AdsManager.init(this) // MobileAds + preload interstitial
        setContentView(R.layout.activity_splash)

        val logoSlot = findViewById<View>(R.id.splashLogoSlot)
        val inkAvatar = findViewById<ImageView>(R.id.splashInkAvatar)
        val logo = findViewById<ImageView>(R.id.splashLogo)
        val title = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        // ── State awal ──────────────────────────────────────────────
        title.alpha = 0f
        title.translationY = 30f
        subtitle.alpha = 0f
        subtitle.translationY = 20f
        logo.visibility = View.INVISIBLE

        // ── Phase 1: avatar goresan tinta (speed-arcs menelusuri diri,
        //    lalu play triangle pop) — AVD ~1.4s total. ───────────────
        val avd = inkAvatar.drawable as? AnimatedVectorDrawable
        var phase1Done = false
        if (avd != null) {
            avd.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    if (!phase1Done) {
                        phase1Done = true
                        startPhase2(logoSlot, inkAvatar, logo, title, subtitle)
                    }
                }
            })
            // Start SETELAH view ter-attach & frame pertama tergambar —
            // AVD.start() dari onCreate bisa ter-drop di sebagian device.
            inkAvatar.post { avd.start() }
        } else {
            // Fallback bila AVD tak tersedia: langsung phase 2.
            startPhase2(logoSlot, inkAvatar, logo, title, subtitle)
        }

        // Pengaman: bila callback tak kunjung datang (device aneh),
        // paksa lanjut setelah 1.8s.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!phase1Done) {
                phase1Done = true
                startPhase2(logoSlot, inkAvatar, logo, title, subtitle)
            }
        }, 1800)

        // Navigate to the Compose home (Shōnen Jump Ink) after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ComposeHomeActivity::class.java))
            overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top)
            finish()
        }, 2400)
    }

    /** Phase 2: crossfade avatar → logo resmi (pop overshoot), judul & tagline slide-in. */
    private fun startPhase2(logoSlot: View, inkAvatar: View, logo: View, title: TextView, subtitle: TextView) {
        logo.visibility = View.VISIBLE
        logo.alpha = 0f
        logo.scaleX = 0.6f
        logo.scaleY = 0.6f
        // Avatar tinta memudar bersamaan logo resmi pop
        inkAvatar.animate().alpha(0f).setDuration(220).start()
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
        title.animate().alpha(1f).translationY(0f).setDuration(380).setStartDelay(90).start()
        subtitle.animate().alpha(1f).translationY(0f).setDuration(380).setStartDelay(170).start()
    }
}
