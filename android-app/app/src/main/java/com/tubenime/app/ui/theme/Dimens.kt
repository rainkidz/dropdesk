package com.tubenime.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semua dimensi gaya "Shōnen Jump Ink" — satu sumber untuk konsistensi. */
object Dimens {
    // Border tinta
    val BorderInk: Dp = 4.dp // semua kartu/panel
    val BorderBubble: Dp = 3.dp // speech bubble & stiker
    val BorderSticker: Dp = 2.dp

    // Hard offset shadow — 2dp down-right, solid ink, TANPA blur
    val HardShadowOffset: Dp = 2.dp

    // Radius — verbatim dari export (audit figma_spec.json)
    val RadiusPanel: Dp = 20.dp // panel utama (hero, dialog)
    val RadiusHero: Dp = 16.dp // section hero URL (Figma r16)
    val RadiusCard: Dp = 16.dp // kartu list (downloads, queue)
    val RadiusTile: Dp = 12.dp // tile/panel inner (Figma r12 — paling umum)
    val RadiusInner: Dp = 8.dp // elemen dalam (Figma r8: nav aktif, badge mini)
    val RadiusSticker: Dp = 6.dp // stiker kecil (Figma r6: PASTE LINK, strips)
    val RadiusBadge: Dp = 4.dp // badge TRENDING (Figma r4)
    val RadiusPill: Dp = 999.dp // stiker bulat penuh (bolt, r9999)

    // Spacing scale
    val SpaceXS: Dp = 4.dp
    val SpaceS: Dp = 8.dp
    val SpaceM: Dp = 12.dp
    val SpaceL: Dp = 16.dp
    val SpaceXL: Dp = 24.dp

    // Motif
    val HalftoneHeight: Dp = 18.dp
    val WashiRotation: Float = -15f // ±15°
    const val TiltTile: Float = -2f // tile ±2°
    const val TiltTitle: Float = -4f // judul raksasa −4°
    const val TiltMax: Float = 4f // aturan brief: maks ±4°, maks 2 elemen/layar

    // Tilt verbatim dari export tubenime_20260911-151449
    const val TiltCard: Float = 1.5f // kartu Downloads miring kanan (Article cards)
    const val TiltThumbnail: Float = -1.5f // thumbnail Inspect miring kiri
    const val TiltHeadline: Float = -2f // headline Downloads -2deg
    const val TiltTileRight: Float = 2f // Threads tile +2deg
    val BorderChip: Dp = 2.dp // pill/tab kecil (filter, storage pill)
}
