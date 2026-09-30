package com.tubenime.app

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.AttrRes
import android.util.TypedValue
import android.app.Activity
import java.util.WeakHashMap

/**
 * TubeNime palette engine.
 *
 * Four curated palettes (Sakura default, Mochi Mint, Yuzu Citrus, Midnight Pop), each
 * with day + night values. Layouts/drawables reference ?attr/paletteX; non-default
 * palettes are applied as a forced theme style overlay in attachBaseContext().
 * Night mode (system/light/dark) is handled separately by AppCompatDelegate.
 */
object ThemeEngine {

    const val PREFS_NAME = "tubenime_prefs"
    private const val KEY_PALETTE = "app_palette"

    const val SAKURA = "sakura"
    const val MOCHI = "mochi"
    const val YUZU = "yuzu"
    const val MIDNIGHT = "midnight"
    const val NEON = "neon"

    /**
     * Vintage Paper (desain 17:1917): kertas default tanpa overlay —
     * visual identik dengan SAKURA (keduanya tanpa overlay).
     */
    const val PAPER = "paper"

    /** Urutan = slot swatch desain Settings: Vintage/Midnight/Mint/Sakura/Citrus/Cyber. */
    val ALL = listOf(PAPER, MIDNIGHT, MOCHI, SAKURA, YUZU, NEON)

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PALETTE, PAPER) ?: PAPER

    fun save(context: Context, palette: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PALETTE, palette).apply()
    }

    /**
     * Call from every Activity's onCreate BEFORE super.onCreate(), via
     * ThemeEngine.applyTheme(this). Applies the palette style overlay.
     */
    private val applied = WeakHashMap<Activity, String>()

    fun applyTheme(activity: Activity) {
        val theme = activity.theme ?: return
        when (current(activity)) {
            MOCHI -> theme.applyStyle(R.style.Palette_Mochi, true)
            YUZU -> theme.applyStyle(R.style.Palette_Yuzu, true)
            MIDNIGHT -> theme.applyStyle(R.style.Palette_Midnight, true)
            NEON -> theme.applyStyle(R.style.Palette_Neon, true)
            // SAKURA = default values, nothing to apply
        }
        applied[activity] = current(activity)
    }

    /**
     * Call from onResume(): re-creates the activity when the palette changed
     * since its theme was applied (e.g. changed in Settings, then back-pressed).
     */
    fun checkRecreate(activity: Activity) {
        if (applied[activity] != null && applied[activity] != current(activity)) {
            activity.recreate()
        }
    }

    /** True when palette changed between the saved value and [palette]. */
    fun isDirty(activity: Activity, palette: String): Boolean =
        current(activity) != palette

    /** Resolve a palette theme attribute to a color int. */
    fun color(context: Context, @AttrRes attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /** Display order + labels for the picker dialog. */
    fun label(palette: String): String = when (palette) {
        PAPER -> "Vintage Paper"
        MOCHI -> "Mochi Mint"
        YUZU -> "Yuzu Citrus"
        MIDNIGHT -> "Midnight Pop"
        NEON -> "Neon Pop"
        else -> "Sakura"
    }

    /** Two [start, end] gradient colors for picker swatches, resolved for the CURRENT uiMode. */
    fun swatchColors(context: Context, palette: String): Pair<Int, Int> {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val ids = when (palette) {
            MOCHI -> R.color.anime_gradient_start_mochi to R.color.anime_gradient_end_mochi
            YUZU -> R.color.anime_gradient_start_yuzu to R.color.anime_gradient_end_yuzu
            MIDNIGHT -> R.color.anime_gradient_start_midnight to R.color.anime_gradient_end_midnight
            NEON -> R.color.anime_gradient_start_neon to R.color.anime_gradient_end_neon
            else -> R.color.anime_gradient_start to R.color.anime_gradient_end
        }
        return androidx.core.content.ContextCompat.getColor(context, ids.first) to
            androidx.core.content.ContextCompat.getColor(context, ids.second)
    }
}
