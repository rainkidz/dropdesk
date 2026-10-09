package com.tubenime.app.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.BottomNavBar
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.components.defaultNavItems
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.ChipBrown
import com.tubenime.app.ui.theme.ChipOlive
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.EspressoBrown
import com.tubenime.app.ui.theme.FanNoteCream
import com.tubenime.app.ui.theme.HighlightYellowLight
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.Inter
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TitleRed
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/**
 * Satu slot palet desain (nama + dot + kunci ThemeEngine):
 * urutan = Vintage/Midnight/Mint/Sakura/Citrus/Cyber.
 */
data class PaletteSlot(val name: String, val color: Color, val engineKey: String = "")

/** Satu baris cookie desain: badge mini + nama + status login. */
data class CookieSlot(
    val platform: String,
    val label: String,
    val badge: String,
    val badgeColor: Color,
    val badgeTextColor: Color = PureWhite,
    val connected: Boolean,
    val statusText: String,
)

private val ButtonRed = Color(0xFFBA1A1A) // tombol Login Required 17:1997
private val TtBadge = Color(0xFF333028) // badge TT 17:2003

/** Banner "Update tersedia" yang ditampilkan di atas PREMIUM STATUS. */
data class UpdateBanner(
    val latestVersion: String,
    val currentVersion: String,
    val notes: String,
)

// ── Styles verbatim JSON ──────────────────────────────────────────────
private val HeaderTitle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.65).sp)
private val WordmarkStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.1.sp)
private val BadgeText = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.4.sp)
private val SwatchName = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 9.sp)
private val RowTitle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 16.sp)
private val RowSub = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp)
private val ChipText = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.4.sp)

/**
 * Figma 17:1857 "TubeNime - Settings": 5 seksi (PREMIUM STATUS / APPEARANCE /
 * DOWNLOADS / LOGIN COOKIES / ABOUT & MOTOR). Tiap panel putih r12 border
 * tinta 4dp + shadow (3,3) + badge kuning overlap. Stateless: semua state
 * di-hoist ke host.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    premiumTitle: String = "FREE TIER",
    premiumSubtitle: String = "3/3 Daily Quota remaining",
    onUpgradeClick: () -> Unit = {},
    rewardButtonVisible: Boolean = false,
    rewardButtonEnabled: Boolean = true,
    rewardButtonLabel: String = "WATCH AD → 30 MIN PRO",
    rewardCountdownLabel: String? = null,
    onWatchAdClick: () -> Unit = {},
    palettes: List<PaletteSlot> = samplePalettes(),
    selectedPalette: Int = 0,
    onPaletteSelect: (Int) -> Unit = {},
    qualities: List<String> = listOf("1080p", "720p", "480p"),
    qualityLabels: List<String> = listOf("1080p FHD", "720p HD", "480p SD"),
    selectedQuality: Int = 1,
    onQualitySelect: (Int) -> Unit = {},
    storageText: String = "Internal / TubeNime / Anime",
    onChangeStorage: () -> Unit = {},
    wifiOnly: Boolean = true,
    onWifiToggle: () -> Unit = {},
    cookies: List<CookieSlot> = sampleCookies(),
    onCookieClick: (CookieSlot) -> Unit = {},
    versionTitle: String = "v4.5.0 Shōnen Engine",
    versionSubtitle: String = "Build: 9-InkRoll",
    onClearCache: () -> Unit = {},
    updateBanner: UpdateBanner? = null,
    onUpdateClick: () -> Unit = {},
    selectedNavIndex: Int = 3, // Figma: tab Settings aktif
    onNavSelected: (Int) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── 17:1879 Header TOP APP BAR: paper + shadow (0,4) ──
        Column(Modifier.fillMaxWidth().background(Paper)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceL, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 17:1881 tombol back: kotak putih 40 r8 border 2 + shadow.
                Image(
                    painterResource(R.drawable.figma_btn_back_dl),
                    contentDescription = "Back",
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .size(40.dp),
                )
                // 17:1886 "Settings": Baloo w800 26 ls-0.65 tinta.
                Text(
                    "Settings",
                    style = HeaderTitle,
                    color = Ink,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Dimens.SpaceM),
                )
                Spacer(Modifier.weight(1f))
                // 17:1889 wordmark TUBENIME Rubik w900 22 ls1.1 #B7131A.
                Text("TUBENIME", style = WordmarkStyle, color = TitleRed, maxLines = 1)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(inkShadowColor()),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceL),
        ) {
            // ── 0. UPDATE BANNER (muncul hanya bila ada versi baru) ──
            if (updateBanner != null) {
                UpdateAvailableBanner(
                    latest = updateBanner.latestVersion,
                    current = updateBanner.currentVersion,
                    notes = updateBanner.notes,
                    onClick = onUpdateClick,
                )
            }

            // ── 1. PREMIUM STATUS ──
            SectionPanel(badge = "PREMIUM STATUS") {
                DashedPanel {
                    Column(Modifier.fillMaxWidth().padding(Dimens.SpaceL)) {
                        Text(premiumTitle, style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = (-0.45).sp), color = Ink)
                        Text(premiumSubtitle, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp), color = EspressoBrown, modifier = Modifier.padding(top = 2.dp))
                        if (!isPremium && rewardButtonVisible) {
                            val rewardShadow = inkShadowColor()
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Dimens.SpaceM)
                                    .drawBehind {
                                        val o = 3.dp.toPx()
                                        drawRoundRect(rewardShadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
                                    }
                                    .background(ActiveYellow, RoundedCornerShape(8.dp))
                                    .border(2.dp, Ink, RoundedCornerShape(8.dp))
                                    .clickable(enabled = rewardButtonEnabled, onClick = onWatchAdClick)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painterResource(R.drawable.figma_ic_star),
                                        contentDescription = null,
                                        tint = TitleRed,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        rewardButtonLabel,
                                        style = ChipText,
                                        color = Ink,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                            if (rewardCountdownLabel != null) {
                                Text(
                                    rewardCountdownLabel,
                                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = EspressoBrown,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        } else if (isPremium && rewardCountdownLabel != null) {
                            Text(
                                rewardCountdownLabel,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                color = EspressoBrown,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else if (!isPremium && !rewardButtonVisible) {
                            val upgradeShadow = inkShadowColor()
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Dimens.SpaceM)
                                    .drawBehind {
                                        val o = 3.dp.toPx()
                                        drawRoundRect(upgradeShadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
                                    }
                                    .background(TitleRed, RoundedCornerShape(8.dp))
                                    .border(2.dp, Ink, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onUpgradeClick)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painterResource(R.drawable.figma_ic_star),
                                        contentDescription = null,
                                        tint = PureWhite,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "UPGRADE TO PRO",
                                        style = ChipText,
                                        color = PureWhite,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. APPEARANCE ──
            SectionPanel(badge = "APPEARANCE") {
                Column(Modifier.fillMaxWidth().padding(Dimens.SpaceL)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Theme Palette", style = RowTitle, color = Ink, modifier = Modifier.weight(1f))
                        // Chip jumlah tema (desain: "6 THEMES"; saat ini 1).
                        Box(
                            Modifier
                                .background(FanNoteCream, RoundedCornerShape(4.dp))
                                .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (palettes.size == 1) "1 THEME" else "${palettes.size} THEMES",
                                style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.6.sp),
                                color = EspressoBrown,
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpaceM),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        palettes.forEachIndexed { index, slot ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .background(slot.color, CircleShape)
                                        .border(3.dp, Ink, CircleShape)
                                        .clickable { onPaletteSelect(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Lingkaran terpilih = cek merah (ikon cek
                                    // MISSING di export → glyph teks).
                                    if (index == selectedPalette) {
                                        Text("✓", style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 18.sp), color = TitleRed)
                                    }
                                }
                                Text(slot.name, style = SwatchName, color = Ink, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            // ── 3. DOWNLOADS ──
            SectionPanel(badge = "DOWNLOADS") {
                Column(Modifier.fillMaxWidth().padding(Dimens.SpaceL)) {
                    Text("Default Quality", style = RowTitle, color = Ink)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpaceS),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    ) {
                        qualities.forEachIndexed { index, _ ->
                            val label = qualityLabels.getOrElse(index) { qualities[index] }
                            val sel = index == selectedQuality
                            Row(
                                Modifier
                                    .weight(1f)
                                    .background(if (sel) ActiveYellow else FanNoteCream, RoundedCornerShape(8.dp))
                                    .border(2.dp, Ink, RoundedCornerShape(8.dp))
                                    .clickable { onQualitySelect(index) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (sel) {
                                    Text("✓ ", style = ChipText, color = ChipBrown)
                                }
                                Text(label, style = ChipText, color = if (sel) ChipBrown else Ink, maxLines = 1)
                            }
                        }
                    }
                    Text("Storage Location", style = RowTitle, color = Ink, modifier = Modifier.padding(top = Dimens.SpaceL))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpaceS)
                            .background(Paper, RoundedCornerShape(8.dp))
                            .border(2.dp, Ink, RoundedCornerShape(8.dp))
                            .padding(horizontal = Dimens.SpaceM, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            storageText,
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = Ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .background(PureWhite, RoundedCornerShape(4.dp))
                                .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                .clickable(onClick = onChangeStorage)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("CHANGE", style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Ink)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpaceL),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi Only Downloads", style = RowTitle, color = Ink)
                            Text(
                                "Prevent accidental mobile data usage",
                                style = RowSub,
                                color = EspressoBrown,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        ComicLever(checked = wifiOnly, onToggle = onWifiToggle)
                    }
                }
            }

            // ── 4. LOGIN COOKIES ──
            SectionPanel(badge = "LOGIN COOKIES") {
                Column(Modifier.fillMaxWidth().padding(Dimens.SpaceL)) {
                    val rowShadow = inkShadowColor()
                    cookies.forEach { slot ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Dimens.SpaceS)
                                .drawBehind {
                                    val o = 2.dp.toPx()
                                    drawRoundRect(rowShadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
                                }
                                .background(Paper, RoundedCornerShape(8.dp))
                                .border(2.dp, Ink, RoundedCornerShape(8.dp))
                                .clickable { onCookieClick(slot) }
                                .padding(horizontal = Dimens.SpaceM, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Mini badge platform (YT merah / IG merah tua / TT arang).
                            Box(
                                Modifier
                                    .background(slot.badgeColor, RoundedCornerShape(4.dp))
                                    .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    slot.badge,
                                    style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                    color = slot.badgeTextColor,
                                )
                            }
                            Text(
                                slot.label,
                                style = RowTitle,
                                color = Ink,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = Dimens.SpaceM),
                            )
                            if (slot.connected) {
                                Row(
                                    Modifier
                                        .background(PureWhite, RoundedCornerShape(4.dp))
                                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        slot.statusText,
                                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                        color = Ink,
                                    )
                                    Text(" ✓", style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 12.sp), color = ChipOlive)
                                }
                            } else {
                                Row(
                                    Modifier
                                        .background(ButtonRed, RoundedCornerShape(4.dp))
                                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Login Required",
                                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                        color = PureWhite,
                                    )
                                    Text(" !", style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 12.sp), color = PureWhite)
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. ABOUT & MOTOR ──
            SectionPanel(badge = "ABOUT & MOTOR") {
                Box(Modifier.fillMaxWidth()) {
                    HalftoneFade(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        maxAlpha = 0.18f,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SpaceL),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(versionTitle, style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp), color = Ink)
                            Text(versionSubtitle, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp), color = EspressoBrown, modifier = Modifier.padding(top = 2.dp))
                        }
                        // Tombol Clear Cache: paper r8 border 2 + shadow + ikon
                        // trash merah (ikon trash MISSING di export → delete reuse).
                        val cacheShadow = inkShadowColor()
                        Box(
                            Modifier
                                .drawBehind {
                                    val o = 2.dp.toPx()
                                    drawRoundRect(cacheShadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
                                }
                                .background(Paper, RoundedCornerShape(8.dp))
                                .border(2.dp, Ink, RoundedCornerShape(8.dp))
                                .clickable(onClick = onClearCache)
                                .padding(horizontal = Dimens.SpaceM, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(R.drawable.figma_ic_delete),
                                    contentDescription = null,
                                    tint = TitleRed,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Clear\nCache",
                                    style = ChipText,
                                    color = Ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            HalftoneFade(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.HalftoneHeight),
                maxAlpha = 0.15f,
            )
        }

        // ── Bottom nav (tab Settings aktif) ──
        BottomNavBar(
            items = defaultNavItems(),
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavSelected,
        )
    }
}

/**
 * Panel seksi desain: badge kuning overlap tepi atas + panel putih r12
 * border tinta 4dp + shadow (3,3).
 */
@Composable
private fun SectionPanel(
    badge: String,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceXL)) {
        MangaCard(
            shape = RoundedCornerShape(12.dp),
            fill = PureWhite,
            showHalftone = false,
            shadowOffset = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        ) {
            Box(Modifier.fillMaxWidth()) { content() }
        }
        StickerBadge(
            badge,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp)
                .height(28.dp),
            container = ActiveYellow,
            contentColor = ChipBrown,
            radius = 4.dp,
            border = 2.dp,
            shadowOffset = 2.dp,
            hardShadow = true,
            fontFamily = Rubik,
            textWeight = FontWeight.ExtraBold,
            textSize = 12.sp,
            textSpacing = 0.4.sp,
        )
    }
}

/** Panel overlay dashed (seksi premium): fill + border putus-putus tinta. */
@Composable
private fun DashedPanel(content: @Composable () -> Unit) {
    val ink = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .fillMaxWidth()
            .padding(Dimens.SpaceL)
            .background(HighlightYellowLight, RoundedCornerShape(8.dp))
            .drawWithContent {
                drawContent()
                val r = 8.dp.toPx()
                val p = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                }
                drawPath(p, ink, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            },
    ) {
        content()
    }
}

/**
 * Comic lever desain (17:1976): pil kuning r9999 border 3dp + shadow,
 * knob putih border 2dp + dot merah saat ON.
 */
@Composable
private fun ComicLever(checked: Boolean, onToggle: () -> Unit) {
    val ink = MaterialTheme.colorScheme.outline
    val pill = CircleShape
    val leverShadow = inkShadowColor()
    Box(
        Modifier
            .size(width = 64.dp, height = 36.dp)
            .drawBehind {
                val o = 2.dp.toPx()
                drawRoundRect(leverShadow, Offset(o, o), size, CornerRadius(size.height / 2f))
            }
            .background(if (checked) ActiveYellow else Paper, pill)
            .border(3.dp, ink, pill)
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(PureWhite, CircleShape)
                .border(2.dp, ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(ActionRed, CircleShape),
                )
            }
        }
    }
}

/**
 * Desain hanya punya 1 tema (kertas terang) — 5 slot lain disembunyikan
 * sampai ada desainnya. Kembalikan 6 slot + kunci engine bila sudah ada.
 */
fun samplePalettes(): List<PaletteSlot> = listOf(
    PaletteSlot("Vintage", Color(0xFFFAF3E7), com.tubenime.app.ThemeEngine.PAPER),
)

    fun sampleCookies(): List<CookieSlot> = listOf(
        CookieSlot("youtube", "YouTube", "YT", TitleRed, connected = true, statusText = "Active"),
        CookieSlot("instagram", "Instagram", "IG", ButtonRed, connected = false, statusText = "Login Required"),
        CookieSlot("tiktok", "TikTok", "TT", TtBadge, connected = true, statusText = "Connected"),
    )

/**
 * Banner kuning "Update tersedia" — pola manga card sama dengan panel lain
 * agar konsisten, plus tombol "VIEW" yang membuka halaman release di browser.
 */
@Composable
private fun UpdateAvailableBanner(
    latest: String,
    current: String,
    notes: String,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.outline
    val shadow = inkShadowColor()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.SpaceL)
            .drawBehind {
                val o = 3.dp.toPx()
                drawRoundRect(shadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
            }
            .background(ActiveYellow, RoundedCornerShape(8.dp))
            .border(2.dp, ink, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(Dimens.SpaceM),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.figma_ic_star),
                    contentDescription = null,
                    tint = TitleRed,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "  UPDATE AVAILABLE  v$latest",
                    style = ChipText,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .background(Paper, RoundedCornerShape(4.dp))
                        .border(1.dp, ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "VIEW",
                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.6.sp),
                        color = TitleRed,
                    )
                }
            }
            if (notes.isNotBlank()) {
                Text(
                    notes.lineSequence().firstOrNull().orEmpty().take(120),
                    style = RowSub,
                    color = ink,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 2,
                )
            } else {
                Text(
                    "Current v$current → v$latest. Tap to download the latest APK.",
                    style = RowSub,
                    color = ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Preview(name = "SettingsScreen", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 1400)
@Composable
private fun SettingsScreenPreview() {
    TubeNimeTheme {
        SettingsScreen()
    }
}
