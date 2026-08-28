package com.snapsave.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animate logo
        val logo = findViewById<ImageView>(R.id.splashLogo)
        val title = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        // Initial state - invisible and scaled down
        logo.alpha = 0f
        logo.scaleX = 0.5f
        logo.scaleY = 0.5f
        title.alpha = 0f
        title.translationY = 30f
        subtitle.alpha = 0f
        subtitle.translationY = 20f

        // Animate logo - bounce in
        val logoAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1f)
            )
            duration = 600
            interpolator = OvershootInterpolator(1.5f)
        }

        // Animate title - slide up + fade in
        val titleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(title, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(title, "translationY", 30f, 0f)
            )
            duration = 400
            startDelay = 300
        }

        // Animate subtitle - slide up + fade in
        val subtitleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(subtitle, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(subtitle, "translationY", 20f, 0f)
            )
            duration = 400
            startDelay = 500
        }

        // Play animations sequentially
        AnimatorSet().apply {
            playTogether(logoAnim, titleAnim, subtitleAnim)
            start()
        }

        // Navigate to MainActivity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top)
            finish()
        }, 2000)
    }
}
