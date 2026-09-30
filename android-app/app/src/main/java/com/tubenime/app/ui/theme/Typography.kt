package com.tubenime.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tubenime.app.R

/** Display & CTA: Baloo 2 (font family sudah di res/font). */
val Baloo2 = FontFamily(
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
)

/** Body: Inter — khusus placeholder & meta teks per export (7 node di Inspect/Downloads). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Rubik — font body/label dari export tubenime_20260911-151449 (dominan: 21
 * node di Home, 25 di Queue). Dipakai untuk wordmark, nav, label, status.
 */
val Rubik = FontFamily(
    Font(R.font.rubik_400, FontWeight.Normal),
    Font(R.font.rubik_500, FontWeight.Medium),
    Font(R.font.rubik_600, FontWeight.SemiBold),
    Font(R.font.rubik_700, FontWeight.Bold),
    Font(R.font.rubik_800, FontWeight.ExtraBold),
    Font(R.font.rubik_900, FontWeight.Black),
)

/** Versi mono-flex dari [Rubik] untuk label letter-spaced kecil (badge/chip). */
private fun rubikSpaced(weight: FontWeight, size: Float, ls: Float, lh: Float) = TextStyle(
    fontFamily = Rubik, fontWeight = weight,
    fontSize = size.sp, lineHeight = lh.sp, letterSpacing = ls.sp,
)

/**
 * Skala presisi dari export (audit: designs/audit/TEXT_SPEC.txt).
 * Setiap slot dianotasi dengan node Figma asalnya.
 */
val TubeNimeTypography = Typography(
    // "TubeNime" judul Home — Baloo 2 w900 44sp ls-1.2 lh44 #1A1A1A (360dp fit)
    displayLarge = TextStyle(
        fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp,
    ),
    // "Download Queue" / "MY DOWNLOADS" — Baloo 2 w800 28sp ls-0.7
    headlineMedium = TextStyle(
        fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-0.7).sp,
    ),
    // "TubeNime" header Inspect — Baloo 2 w800 22sp ls-0.55 #B7131A
    headlineSmall = TextStyle(
        fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.55).sp,
    ),
    // "Chainsaw Man…" judul video Inspect — Baloo 2 w700 20sp ls-0.5
    titleLarge = TextStyle(
        fontFamily = Baloo2, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.5).sp,
    ),
    // wordmark "TUBENIME" Queue — Rubik w900 22sp ls1.1 #B7131A
    titleMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Black,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 1.1.sp,
    ),
    // "YouTube"/"Bilibili" tile Home — Baloo 2 w900 18sp
    titleSmall = TextStyle(
        fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp, lineHeight = 22.sp,
    ),
    // "EXTRACT STREAM" — Rubik w900 16sp; "Attack_on_Titan…" Rubik w700 16sp
    bodyLarge = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    // "1080p FHD 60fps" tiket — Rubik w900 18sp #1E1B14
    bodyMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Black,
        fontSize = 18.sp, lineHeight = 22.sp,
    ),
    // placeholder Inter w400 13sp #5D5C5B
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 15.6.sp,
    ),
    // tombol CTA: INSPECT LINK Baloo w800 18 ls0.45 / DOWNLOAD VIDEO w900 22 ls0.55
    labelLarge = TextStyle(
        fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.45.sp,
    ),
    // "PANEL 01"/nav — Rubik w800 10sp ls0.6
    labelSmall = rubikSpaced(FontWeight.ExtraBold, 10f, 0.6f, 12f),
    // "ALL"/"VIDEO" tab — Rubik w800 12sp ls0.4
    labelMedium = rubikSpaced(FontWeight.ExtraBold, 12f, 0.4f, 14f),
    // "AD-FREE DIRECT INKING STREAM" — Rubik w700 10sp ls0.5 #5B403D → pakai LabelTiny
)

/** Label mikro 10sp ls0.5 (Rubik w700) untuk caption sekunder (FAST EXTRACTION, dll). */
val LabelTiny = rubikSpaced(FontWeight.Bold, 10f, 0.5f, 15f)
