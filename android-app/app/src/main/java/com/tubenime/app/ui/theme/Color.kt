package com.tubenime.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Token warna — sumber kebenaran: designs/tubenime_20260911-151449.json
 * (census audit: designs/audit/DIVERGENCE_REPORT.md).
 * Kedua merah HIDUP berdampingan sesuai Figma:
 *   #E53935 = aksen Home (CTA hero, bolt, TRENDING)
 *   #DB322F = CTA Inspect + badge Queue (LIVE, posisi #1)
 */
// ── Kertas & tinta ──────────────────────────────────────────────────────────
val Paper = Color(0xFFFAF3E7) // background semua layar
val PaperDeep = Color(0xFFEEE7DC) // paper shade placeholder / track
val Ink = Color(0xFF1A1A1A) // border 4dp, heading, hard shadow
val InkSoft = Color(0xFF1E1B14) // nav bar, banner ink-fill, panel gelap Queue
val TextOnPaper = Color(0xFF212121) // body text
val TextMuted = Color(0xFF5D5C5B) // caption sekunder (16 node di export)
val EspressoBrown = Color(0xFF5B403D) // sub-teks Inspect (13 node)
val PureWhite = Color(0xFFFFFFFF) // kartu, teks di atas ink/red (54 node)
val PureBlack = Color(0xFF000000) // stroke ikon Queue (menu/search)

// ── Merah ───────────────────────────────────────────────────────────────────
val ActionRed = Color(0xFFE53935) // HOME: CTA hero INSPECT LINK, bolt, TRENDING
val CtaRed = Color(0xFFDB322F) // INSPECT: DOWNLOAD VIDEO; QUEUE: LIVE + badge #1
val TitleRed = Color(0xFFB7131A) // wordmark TUBENIME (Queue), VIEW ALL (Home)
val Crimson = Color(0xFF93000A) // source label Downloads (YouTube, YT Music)
val ActionRedDark = Color(0xFFB7131A) // alias TitleRed (pressed)

// ── Kuning ──────────────────────────────────────────────────────────────────
val HighlightYellow = Color(0xFFFFC940) // stiker/badge kuning brief (Queue badge #2)
val ActiveYellow = Color(0xFFFDC73E) // tab AKTIF semua layar + panel video Inspect + Pause All
val HighlightYellowLight = Color(0xFFFFDF9B) // fill panel muda: fan-note, shortcut-1, "100%"
val OnYellow = Color(0xFF705400) // teks di atas ActiveYellow (8 node)
val ChipBrown = Color(0xFF251A00) // tag "1080p" di kartu Queue kuning
val ChipOlive = Color(0xFF785A00) // status "QUEUED" (Queue)

// ── Panel & kertas turunan ──────────────────────────────────────────────────
val BubbleCream = Color(0xFFFFF8EF) // speech bubble / count bubble
val FanNoteCream = Color(0xFFF4EDE1) // panel fan-note Downloads + toggle sticker Inspect
val PanelMuted = Color(0xFFE0D9CE) // container thumbnail abu (Downloads)
val DividerSoft = Color(0xFFE8E2D6) // garis aksi Share/Delete (Downloads)
val BlushTint = Color(0xFFFFDAD6) // backdrop kartu tint merah muda (Downloads)

// ── Washi tape (verbatim node name di export) ───────────────────────────────
val WashiYellow = HighlightYellow
val WashiPink = Color(0xFFFF85A1) // "Manga Pink #FF85A1" — diperbaiki dari #F8A5C2
val WashiMint = Color(0xFF8CE0BA) // "Mint Green #8CE0BA" — diperbaiki dari #9FE2BF

// ── Dark scheme ─────────────────────────────────────────────────────────────
val DarkPaper = Color(0xFF171410)
val DarkSurface = Color(0xFF241F18)
val DarkInkLine = Color(0xFFEFE6D6) // border di dark mode
val DarkText = Color(0xFFF1EAD9)

// ── Aksen platform (brand, dipakai di PlatformGrid — paritas colors.xml) ────
val PlatformYouTube = Color(0xFFE53935)
val PlatformTikTok = Color(0xFF1A1A1A)
val PlatformInstagram = Color(0xFFE1306C)
val PlatformBilibili = Color(0xFF00A1D6)
val PlatformFacebook = Color(0xFF1877F2)
val PlatformThreads = Color(0xFF1A1A1A)
