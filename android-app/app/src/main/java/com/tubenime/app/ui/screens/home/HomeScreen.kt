package com.tubenime.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.AdBanner
import com.tubenime.app.ui.components.BottomNavBar
import com.tubenime.app.ui.components.HalftoneBlob
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.SpeechBubble
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.components.defaultNavItems
import com.tubenime.app.ui.screens.home.components.HeroUrlPanel
import com.tubenime.app.ui.screens.home.components.PlatformGrid
import com.tubenime.app.ui.screens.home.components.ShortcutCards
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.HighlightYellowLight
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.isDarkTheme

/**
 * Figma 1:160 "TubeNime - Home": judul raksasa miring −4° + halftone baseline →
 * badge mini "RAW V3.4" → hero URL panel → speech bubble tooltip → grid platform
 * asimetris → shortcut strips → bottom nav (4 tab, underline highlighter).
 * Stateless: semua state di-hoist; hanya placeholder lokal utk preview interaksi.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    url: String = "",
    onUrlChange: (String) -> Unit = {},
    onInspectClick: () -> Unit = {},
    onPasteClick: () -> Unit = {},
    onPlatformClick: (String) -> Unit = {},
    onShortcutClick: (String) -> Unit = {},
    selectedNavIndex: Int = 0,
    onNavSelected: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceL),
        ) {
            // ── Figma "Top Manga Title Header" — geometri presisi + gaya comic:
            // judul bbox 212×48 TANPA rotasi dengan DROP_SHADOW kuning (terlihat di
            // render node_1_160.png); bolt 43×43 (y −7dp) & badge 92×24 keduanya
            // ber-drop-shadow ink 2.5/2dp (JSON effects) — sama dengan panel lain.
            // Judul SELALU 48sp (node 1:163): di 360dp teks ~195dp masih muat
            // (kolom tersedia ±225dp setelah bolt+badge).
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val titleStyle = MaterialTheme.typography.displayLarge
                // Node pixel-audit: shadow judul KUNING (offset 2,2 — glyph outline
                // kuning terlihat di kanan-bawah tiap huruf, node_1_160.png)
                val shadowPx = with(LocalDensity.current) { 2.dp.toPx() }
                val titleShadow = Shadow(
                    color = HighlightYellowLight,
                    offset = Offset(shadowPx, shadowPx),
                    blurRadius = 0f,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "TubeNime",
                        style = titleStyle.copy(
                            shadow = if (isDarkTheme())
                                titleShadow.copy(color = Color.Black.copy(alpha = 0.55f))
                            else titleShadow,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Figma: "Red Lightning Bolt Sticker" x=236 — naik 7dp dari center.
                    // Hard shadow ink (2.5,2.5) sudah termasuk di dalam drawable
                    // (feOffset SVG node 1:168) — tidak perlu Box shadow manual.
                    Image(
                        painterResource(R.drawable.figma_ic_bolt_sticker),
                        contentDescription = "Fast",
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .offset(y = (-7).dp)
                            .size(43.dp),
                    )
                    // Figma: "Quick Action Mini Manga Badge / Counter" — putih + ink
                    // + glyph flame, tinggi 24dp, drop shadow ink 2dp (JSON effects)
                    StickerBadge(
                        "RAW V3.4",
                        Modifier
                            .padding(start = 3.dp)
                            .height(24.dp),
                        container = PureWhite,
                        contentColor = Ink,
                        leadingIconRes = R.drawable.figma_ic_badge_glyph,
                        hardShadow = true,
                    )
                }
            }
            // Figma node 1:167 "Halftone dot baseline fade" (y=78, OVERLAP baseline
            // judul — glyph-audit: elips hanya ~2dp di bawah ink glyph, bukan 27dp).
            // Geser naik 22dp agar posisi vertikalnya sesuai node.
            HalftoneBlob(
                Modifier
                    .padding(start = 2.dp, top = 2.dp)
                    .offset(y = (-8.5).dp)
                    .size(width = 212.dp, height = 12.dp),
                centerXFraction = 0.449f,
                centerYFraction = 0.5f,
                radiusXFraction = 0.119f,
                radiusYFraction = 0.21f,
                alpha = 0.37f,
            )

            // ── Figma: "Section - HERO URL INPUT PANEL" ──
            HeroUrlPanel(
                value = url,
                onValueChange = onUrlChange,
                onInspectClick = onInspectClick,
                onPasteClick = onPasteClick,
                modifier = Modifier.padding(top = Dimens.SpaceXL),
            )

            // ── Figma: "Contextual Manga Speech Bubble Tooltip" ──
            // Figma: bubble rata KIRI (x454, ekor kiri-bawah) + glyph bohlam merah
            // kecil sebelum teks (node 1:207/1:208); teks Baloo w700 12.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceXL), // Figma: panel bottom 314 → bubble 338
            ) {
                SpeechBubble("Tap a platform to browse!", leadingIconRes = R.drawable.figma_ic_glyph_a)
            }

            // ── Figma: "Section - ASYMMETRIC MANGA PLATFORM PANELS GRID" ──
            PlatformGrid(
                modifier = Modifier.padding(top = Dimens.SpaceXL), // Figma: bubble 368 → header 392
                showHeaderMarker = true,
                onTileClick = { onPlatformClick(it.name) },
            )

            // ── Figma: "Section - SHORTCUT CARDS" ──
            ShortcutCards(
                Modifier.padding(top = Dimens.SpaceXL, bottom = Dimens.SpaceXL),
                onItemClick = { onShortcutClick(it.title) },
                onViewAllClick = { onShortcutClick("Trending Now") },
            )
        }

        // Banner AdMob — selalu terlihat di atas bottom nav (disembunyikan
        // otomatis untuk user premium oleh AdBanner itu sendiri).
        AdBanner()

        // Bottom nav — selalu terlihat (Figma: bottom-pinned)
        BottomNavBar(
            items = defaultNavItems(),
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavSelected,
        )
    }
}

@Preview(name = "HomeScreen", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    // Preview memakai remember agar input bisa diketik di Android Studio preview
    TubeNimeTheme {
        val urlState = remember { mutableStateOf("") }
        HomeScreen(url = urlState.value, onUrlChange = { urlState.value = it })
    }
}
