package com.tubenime.app.ui.screens.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TubeNimeTheme

data class PlatformTile(
    val name: String,
    val subtitle: String,
    val iconRes: Int, // vector drawable hasil konversi SVG Figma (tile utuh)
    val weight: Float = 1f,
    val tilt: Float? = null, // Figma: kartu TikTok −2°, Threads +2° (node 1:236/1:275)
    val badge: String? = null,
    val iconSize: Int = 36, // dp: square 32 + shadow 2 + margin
    val showRedDot: Boolean = false, // Figma 1:261: dot merah 8dp di kartu Bilibili
)

/**
 * Tile dari Figma 1:160 — drawable = tile utuh dari export (bg brand + border
 * ink + glyph): hero YouTube r8 merah #E53935, TikTok −2° ink, Instagram merah,
 * Bilibili pink #FF85A1, Facebook biru #1877F2, Threads +2° ink.
 *
 * Subtitle menunjuk ke feed TRENDING anime ([AnimeChannels.platformTrendingUrl]):
 * tiap tile membuka konten populer anime platform itu, bukan channel tertentu.
 */
fun defaultPlatformTiles(): Pair<List<PlatformTile>, List<PlatformTile>> = Pair(
    listOf(
        PlatformTile("YouTube", "#ANIME TRENDING", R.drawable.figma_tile_youtube, weight = 1.5f, badge = "TRENDING"),
        PlatformTile("TikTok", "#ANIME VIRAL", R.drawable.figma_tile_tiktok, tilt = -2f),
        PlatformTile("Instagram", "EXPLORE ANIME", R.drawable.figma_tile_instagram),
    ),
    listOf(
        PlatformTile("Bilibili", "RANKING ANIME", R.drawable.figma_tile_bilibili, showRedDot = true),
        PlatformTile("Facebook", "WATCH VIRAL", R.drawable.figma_tile_facebook),
        PlatformTile("Threads", "TOP TALK", R.drawable.figma_tile_threads, tilt = 2f),
    ),
)

// Judul tile: YouTube (hero) Baloo w900 18; tile kecil Baloo w700 14
private val TileTitleHero = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.5.sp)
private val TileTitle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.5.sp)
private val TileSubtitle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 9.sp, lineHeight = 18.sp)

/**
 * Figma 1:160 "Section - ASYMMETRIC MANGA PLATFORM PANELS GRID" (spec presisi):
 * marker blok kuning 10×16 (node 1:216) + header Rubik w800 18 ls-0.45 +
 * CHAPTER 12 (Rubik w800 10 ls0.6 muted); Row 1 = YouTube 1.5x + TikTok(−2°) +
 * Instagram; Row 2 = Bilibili + Facebook + Threads(+2°). Border sw4 r12, badge
 * TRENDING putih-di-merah r4 (Rubik w400 9 ls0.45 — node 1:235).
 */
@Composable
fun PlatformGrid(
    modifier: Modifier = Modifier,
    rows: Pair<List<PlatformTile>, List<PlatformTile>> = defaultPlatformTiles(),
    onTileClick: (PlatformTile) -> Unit = {},
    showHeaderMarker: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHeaderMarker) {
                // Figma node 1:216: blok kuning 10×16 border tinta sebelum judul
                Box(
                    Modifier
                        .padding(end = Dimens.SpaceS)
                        .size(width = 10.dp, height = 16.dp)
                        .background(HighlightYellow)
                        .border(2.dp, Ink),
                )
            }
            Text(
                "PLATFORM MATRIX",
                style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = (-0.45).sp),
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text("CHAPTER 12", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            rows.first.forEach { PlatformTileView(it) { onTileClick(it) } }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            rows.second.forEach { PlatformTileView(it) { onTileClick(it) } }
        }
    }
}

@Composable
private fun RowScope.PlatformTileView(tile: PlatformTile, onClick: () -> Unit) {
    val tileModifier = tile.tilt?.let { Modifier.rotate(it) } ?: Modifier
    MangaCard(
        modifier = tileModifier
            .weight(tile.weight)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        fill = if (tile.weight > 1f) Paper else MaterialTheme.colorScheme.surface,
        showHalftone = false,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = if (tile.weight > 1f) 12.dp else 8.dp, vertical = 12.dp),
            horizontalAlignment = if (tile.weight > 1f) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            // Tile utuh hasil pixel-audit Figma render (square 32dp + ink shadow 2,2)
            Image(
                painterResource(tile.iconRes),
                contentDescription = tile.name,
                modifier = Modifier.size(tile.iconSize.dp),
            )
            Text(
                tile.name,
                style = if (tile.weight > 1f) TileTitleHero else TileTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                tile.subtitle,
                style = TileSubtitle.copy(lineHeight = 18.sp), // Figma: Rubik w400 10 lh18 #5D5C5B
                color = TextMuted,
                maxLines = 1,
            )
        }
        tile.badge?.let {
            StickerBadge(
                it,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                container = ActionRed, // Figma TRENDING: merah #E53935 r4
                contentColor = PureWhite,
                textWeight = FontWeight.Normal,
                textSize = 9.sp,
                textSpacing = 0.45.sp,
            )
        }
        // Figma node 1:261: dot merah 8dp di kartu Bilibili (x≈87-95, y≈28 dari
        // kartu 111×110 — kanan, sejajar icon). Digambar SETELAH badge agar di atas.
        if (tile.showRedDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 10.dp)
                    .size(8.dp)
                    .background(ActionRed, CircleShape),
            )
        }
    }
}

@Preview(name = "PlatformGrid", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 400)
@Composable
private fun PlatformGridPreview() {
    TubeNimeTheme {
        PlatformGrid(Modifier.padding(16.dp))
    }
}
