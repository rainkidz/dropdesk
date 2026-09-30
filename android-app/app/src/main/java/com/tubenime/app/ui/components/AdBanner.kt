package com.tubenime.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.tubenime.app.AdsManager

/**
 * Banner AdMob adaptive (lebar penuh, tinggi menyesuaikan) untuk Compose.
 *
 * Tidak me-render apa pun bila user premium ([AdsManager.adsEnabled] false).
 * Lifecycle AdView (resume/pause/destroy) diikat ke LifecycleOwner agar tidak
 * bocor saat navigasi antar layar.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return // Android Studio preview: jangan sentuh SDK iklan
    if (!AdsManager.adsEnabled()) return

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val adView = remember(context) {
        val widthDp = (context.resources.displayMetrics.run { widthPixels / density }).toInt()
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = AdsManager.BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(lifecycle, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { adView },
        update = {},
    )
}
