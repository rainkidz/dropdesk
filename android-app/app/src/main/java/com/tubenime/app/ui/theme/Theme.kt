package com.tubenime.app.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Override dark mode global (dibaca semua host Compose).
 * null = ikuti sistem; true = dark; false = light.
 *
 * CATATAN ARSITEKTUR: host Compose memakai ComponentActivity, yang TIDAK
 * dihormati AppCompatDelegate.setDefaultNightMode() (itu hanya berlaku untuk
 * AppCompatActivity). Karena itu dark mode dikontrol lewat state ini —
 * perubahan langsung merekomposisi semua layar tanpa recreate activity.
 */
val darkModeOverride: MutableState<Boolean?> = mutableStateOf(null)

/**
 * Muat preferensi dark mode dari prefs ke [darkModeOverride].
 * Default = LIGHT (identitas "Shōnen Jump Ink" paper-first), bukan follow
 * sistem — follow-system membuat app tak pernah terang di HP dark-mode.
 * Panggil di onCreate activity entry (Splash → Home).
 */
fun initDarkModeOverride(context: Context) {
    val mode = context
        .getSharedPreferences("tubenime_prefs", Context.MODE_PRIVATE)
        .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_NO)
    darkModeOverride.value = mode == AppCompatDelegate.MODE_NIGHT_YES
}

/** Dark mode efektif: override > ikuti sistem. */
@Composable
fun isDarkTheme(): Boolean =
    darkModeOverride.value ?: isSystemInDarkTheme()

private val LightColors = lightColorScheme(
    primary = ActionRed,
    onPrimary = PureWhite,
    primaryContainer = HighlightYellow,
    onPrimaryContainer = Ink,
    secondary = ActiveYellow, // tab aktif & tombol sekunder kuning (Figma #FDC73E)
    onSecondary = OnYellow, // teks di atas kuning (Figma #705400)
    secondaryContainer = PaperDeep,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = TextOnPaper,
    surface = Color.White, // kartu manga selalu putih di light
    onSurface = TextOnPaper,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = TextMuted,
    outline = Ink, // warna border tinta
    error = CtaRed, // merah CTA Inspect/Queue
    onError = PureWhite,
)

private val DarkColors = darkColorScheme(
    primary = ActionRed,
    onPrimary = PureWhite,
    primaryContainer = HighlightYellow,
    onPrimaryContainer = Ink,
    secondary = ActiveYellow, // tab aktif & tombol sekunder kuning (Figma #FDC73E)
    onSecondary = OnYellow, // teks di atas kuning (Figma #705400)
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkText,
    tertiary = DarkInkLine,
    onTertiary = DarkPaper,
    background = DarkPaper,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkText,
    outline = DarkInkLine, // border tinta jadi paper-line di dark
    error = CtaRed,
    onError = PureWhite,
)

/** Border tinta theme-aware (pakai warna eksplisit override bila diberikan). */
@Composable
fun inkBorder(): Color = MaterialTheme.colorScheme.outline

/** Hard shadow: ink pekat di light; hitam transparan di dark (agar tetap terlihat). */
@Composable
fun inkShadowColor(): Color =
    if (isDarkTheme()) Color(0x88000000) else Ink

/**
 * Wrapper Material 3 — komponen manga membaca token via colorScheme.
 * Dark mode mengikuti [darkModeOverride] (dari Settings) atau sistem bila null.
 * LocalContext dibaca untuk invalidasi saat config berubah (rotasi/dll).
 */
@Composable
fun TubeNimeTheme(
    content: @Composable () -> Unit,
) {
    LocalContext.current // subscribe config changes
    val darkTheme = isDarkTheme()
    // Status bar ikut tema: paper terang + ikon gelap, atau tinta gelap +
    // ikon terang (pengganti pink @color/primary di XML).
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor = (if (darkTheme) DarkPaper else Paper).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TubeNimeTypography,
        content = content,
    )
}
