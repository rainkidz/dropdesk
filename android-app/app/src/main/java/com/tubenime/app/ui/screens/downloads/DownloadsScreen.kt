package com.tubenime.app.ui.screens.downloads

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.BubbleTail
import com.tubenime.app.ui.components.BottomNavBar
import com.tubenime.app.ui.components.CircularPercentSticker
import com.tubenime.app.ui.components.FilterTabRow
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.SpeechBubble
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.components.WashiTape
import com.tubenime.app.ui.components.defaultNavItems
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.BlushTint
import com.tubenime.app.ui.theme.Crimson
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.DividerSoft
import com.tubenime.app.ui.theme.EspressoBrown
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.HighlightYellowLight
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.InkSoft
import com.tubenime.app.ui.theme.Inter
import com.tubenime.app.ui.theme.PanelMuted
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.WashiMint
import com.tubenime.app.ui.theme.WashiPink
import com.tubenime.app.ui.theme.WashiYellow
import com.tubenime.app.ui.theme.inkShadowColor

/** Satu file hasil download (stateless — UI menerima daftar jadi). */
data class DownloadItem(
    val fileName: String,
    val size: String,
    val date: String,
    val platform: String,
    val quality: String,
    val tapeLabel: String,
    val tapeColor: Color,
    val mimeType: String = "video/mp4", // dipakai host utk intent play/share
    val uriString: String = "", // content:// atau file:// uri
    val percent: Int = 100, // 100 = selesai (stiker sirkular kuning)
)

fun sampleDownloads(): List<DownloadItem> = listOf(
    DownloadItem("Oshi_no_Ko_Idol_YOASOBI_1080p.mp4", "84.2 MB", "Today, 14:20", "YouTube", "1080P", "★ TAPE_01", WashiYellow),
    DownloadItem("Jujutsu_Kaisen_S2_OP_SpecialZ.mp4", "112.5 MB", "Yesterday", "TikTok", "1080P", "★ CHAPTER_OP", WashiPink),
    DownloadItem("Spy_x_Family_MixedNuts_Audio.mp3", "12.4 MB", "3 days ago", "YT Music", "MP3", "★ SOUNDTRACK", WashiMint),
)

// ── Text styles presisi dari node Figma (audit TEXT_SPEC.txt) ──
private val FileNameStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp) // Baloo w700 16 #1A1A1A
private val SizeStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp) // Inter w600 12 ink
private val DateStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp) // Inter w400 12 #5D5C5B
private val SourceStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 15.sp) // Rubik w400 10 (YouTube=#93000A, lainnya ink)
private val QualityChipStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 9.sp, lineHeight = 13.5.sp) // chip kualitas di thumbnail
private val HeadlineStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.7).sp) // MY DOWNLOADS

/**
 * Figma 1:501 "TubeNime - Downloads" (fan-notebook, spec presisi): headline
 * "MY DOWNLOADS" miring −2° + halftone → filter tabs Rubik w800 12 (counts
 * verbatim di preview) + storage pill → kartu manga miring 1.5° dengan washi
 * tape overlap (★ label Rubik w900 9 ls0.9) + stiker sirkular 100% + aksi
 * share/delete (garis #E8E2D6) → panel fan-note krem #F4EDE1 ("OFFLINE STORAGE
 * ENGINE" / "Auto-delete watched episodes is turned OFF." / "Tap any file to
 * open player!"). Bottom nav (node 1:328) dengan tab Downloads aktif.
 * Stateless; filter & aksi di-hoist ke host.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    modifier: Modifier = Modifier,
    filterLabels: List<String> = listOf("All", "Videos", "Audio"),
    selectedFilter: Int = 0,
    onFilterSelected: (Int) -> Unit = {},
    storageText: String = "",
    onBack: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchActive: Boolean = false,
    onItemClick: (DownloadItem) -> Unit = {},
    onShareClick: (DownloadItem) -> Unit = {},
    onDeleteClick: (DownloadItem) -> Unit = {},
    queueCount: Int = 0,
    onQueueClick: () -> Unit = {},
    showTip: Boolean = true,
    onTipDismiss: () -> Unit = {},
    selectedNavIndex: Int = 2, // Figma 1:501: tab Downloads AKTIF
    onNavSelected: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Figma: "Header - Top Bar": back + "MY DOWNLOADS" −2° + halftone ──
        // Header 1:657: 382x64 + shadow tinta (0,4) di bawah.
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Figma: "Button - Go Back" 40x40 (hasil konversi SVG)
                Image(
                    painterResource(R.drawable.figma_btn_back_dl),
                    contentDescription = "Back",
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .size(40.dp),
                )
                Column(Modifier.padding(start = Dimens.SpaceS)) {
                    Text(
                        "MY DOWNLOADS", // uppercase verbatim node (Baloo w800 28 ls-0.7)
                        style = HeadlineStyle,
                        color = InkSoft,
                        modifier = Modifier.rotate(Dimens.TiltHeadline),
                    )
                    // Halftone desain (1:662) adalah patch DI BELAKANG judul,
                    // bukan baris dot di bawahnya — jangan tambah fade di sini.
                }
                Box(Modifier.weight(1f))
                // Entry eksplisit ke Download Queue (ganti toggle tab): hanya
                // tampil saat antrean tak-kosong.
                if (queueCount > 0) {
                    QueueLivePill(queueCount, onQueueClick, Modifier.padding(end = Dimens.SpaceS))
                }
                // Figma 1:501 "Button - Search Downloads" (node 1:665) 40x40 kanan
                Image(
                    painterResource(R.drawable.figma_btn_search_dl),
                    contentDescription = "Search Downloads",
                    modifier = Modifier
                        .clickable(onClick = onSearchClick)
                        .size(40.dp),
                )
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
                .padding(horizontal = Dimens.SpaceL),
        ) {
            // Figma 1:501: search aktif → field filter inline (putih r12 stroke ink)
            if (searchActive) {
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceS),
                    placeholder = {
                        Text("Search downloads…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Dimens.RadiusTile),
                    colors = androidx.compose.material3.TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Ink,
                        unfocusedBorderColor = Ink,
                        cursorColor = Ink,
                        containerColor = Paper,
                    ),
                )
            }
            // ── Figma: "Filter Tabs + Storage Counter Pill" ──
            FilterTabRow(
                tabs = filterLabels,
                selectedIndex = selectedFilter,
                storageText = storageText,
                Modifier.padding(bottom = Dimens.SpaceL),
                onTabSelected = onFilterSelected,
            )

            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    SpeechBubble(
                        "No downloads yet — go grab something!",
                        tail = BubbleTail.UP,
                    )
                }
            }

            items.forEach { item ->
                Box(Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceXL)) {
                    MangaCard(shape = RoundedCornerShape(Dimens.RadiusTile), showHalftone = false) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(start = Dimens.SpaceL, top = Dimens.SpaceL, bottom = Dimens.SpaceL, end = 64.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Figma: thumbnail container + "Play button overlay" (SVG)
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(Dimens.RadiusInner))
                                    .background(PanelMuted),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painterResource(R.drawable.figma_play_overlay),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                                // Figma: chip kualitas "1080P"/"MP4" di bawah thumbnail
                                Text(
                                    item.quality,
                                    style = QualityChipStyle,
                                    color = Paper,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .background(Ink)
                                        .padding(horizontal = 4.dp),
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = Dimens.SpaceM)) {
                                Text(item.fileName, style = FileNameStyle, color = Ink, maxLines = 2)
                                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.size, style = SizeStyle, color = Ink)
                                    Text(
                                        "  ${item.date}",
                                        style = DateStyle,
                                        color = TextMuted,
                                    )
                                }
                                // Figma 1:536 "Platform Badge": chip ber-border +
                                // tint (YouTube/YT Music pink). Ikon badge MISSING
                                // di export → chip teks saja.
                                PlatformChip(item.platform, Modifier.padding(top = 4.dp))
                                DashedDivider(Modifier.padding(top = 6.dp))
                                // Figma "Actions Row per card": tombol outline
                                // kotak (share & delete); play = tap kartu.
                                Row(Modifier.padding(top = Dimens.SpaceS), verticalAlignment = Alignment.CenterVertically) {
                                    OutlineActionButton(
                                        R.drawable.figma_ic_share,
                                        "Share",
                                        { onShareClick(item) },
                                        Modifier.padding(end = Dimens.SpaceM),
                                    )
                                    OutlineActionButton(
                                        R.drawable.figma_ic_delete,
                                        "Delete",
                                        { onDeleteClick(item) },
                                    )
                                }
                            }
                        }
                        // Figma: "Download Complete 100% Circular Stamp" merah —
                        // SATU lingkaran berisi "DONE" + "100%" putih.
                        if (item.percent >= 100) {
                            DoneStamp(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-8).dp, y = (-8).dp),
                            )
                        } else {
                            CircularPercentSticker(
                                item.percent,
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-8).dp, y = (-8).dp),
                            )
                        }
                    }
                    // Washi tape overlap di sudut kiri-atas (di luar clip kartu)
                    WashiTape(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-10).dp, y = (-12).dp),
                        color = item.tapeColor,
                        label = item.tapeLabel,
                    )
                }
            }

            // ── Figma 1:645: banner kuning "Tap any file to open player!"
            // (fill #FDC73E r16 border 3dp + ekor bawah + dismiss X).
            // Ikon bohlam & X MISSING di export → glyph_a + dismiss reuse.
            if (showTip) {
                TipBanner(onDismiss = onTipDismiss, Modifier.padding(bottom = Dimens.SpaceM))
            }

            // ── Figma 1:636: "Fan Note" fill #FFDF9B r12 + badge lingkaran
            // kuning + judul Rubik-700 12 + body Inter-400 12.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HighlightYellowLight, RoundedCornerShape(Dimens.RadiusTile))
                    .padding(Dimens.SpaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(R.drawable.figma_ic_fannote),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Column(Modifier.weight(1f).padding(start = Dimens.SpaceM)) {
                    Text(
                        "OFFLINE STORAGE ENGINE",
                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp),
                        color = InkSoft,
                    )
                    Text(
                        "Auto-delete watched episodes is turned OFF.", // Inter 12
                        style = DateStyle,
                        color = EspressoBrown,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            HalftoneFade(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.HalftoneHeight)
                    .padding(top = Dimens.SpaceS),
                maxAlpha = 0.18f,
            )
        }

        // ── Figma 1:501 "Bottom Navigation Bar" (y829, 55dp): bottom-pinned,
        // tab Downloads aktif (pill kuning + underline stabilo) ──
        BottomNavBar(
            items = defaultNavItems(),
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavSelected,
        )
    }
}

/**
 * Pill LIVE entry ke Download Queue (pengganti toggle tab): dot merah +
 * "QUEUE (n)". Tampil hanya saat antrean tak-kosong.
 */
@Composable
private fun QueueLivePill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shadow = inkShadowColor()
    val shape = RoundedCornerShape(Dimens.RadiusInner)
    Row(
        modifier
            .drawBehind {
                val off = Offset(2.dp.toPx(), 2.dp.toPx())
                drawRoundRect(shadow, off, size, androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            }
            .background(PureWhite, shape)
            .border(2.dp, Ink, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(ActionRed, CircleShape),
        )
        Text(
            "QUEUE ($count)",
            style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.6.sp),
            color = Ink,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Figma 1:536 "Platform Badge": chip ber-border + tint per platform
 * (YouTube/YT Music = pink). Ikon badge MISSING di export → teks saja.
 */
@Composable
private fun PlatformChip(platform: String, modifier: Modifier = Modifier) {
    val hot = platform == "YouTube" || platform == "YT Music"
    Box(
        modifier
            .background(if (hot) BlushTint else PureWhite, RoundedCornerShape(Dimens.RadiusBadge))
            .border(2.dp, Ink, RoundedCornerShape(Dimens.RadiusBadge))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            platform,
            style = SourceStyle,
            color = if (hot) Crimson else Ink,
        )
    }
}

/** Figma "Actions Row": tombol outline kotak 40dp (glyph designs asli). */
@Composable
private fun OutlineActionButton(iconRes: Int, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shadow = inkShadowColor()
    val shape = RoundedCornerShape(Dimens.RadiusInner)
    Box(
        modifier
            .size(40.dp)
            .drawBehind {
                val s = size
                drawRect(shadow, Offset(2.dp.toPx(), 2.dp.toPx()), s)
            }
            .background(PureWhite, shape)
            .border(2.dp, Ink, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(iconRes),
            contentDescription = desc,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Figma 1:645: banner kuning "Tap any file to open player!" (Baloo-800 13)
 * + ekor bawah + dismiss X. Ikon bohlam MISSING → glyph_a reuse.
 */
@Composable
private fun TipBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shadow = inkShadowColor()
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val off = Offset(3.dp.toPx(), 3.dp.toPx())
                    drawRoundRect(shadow, off, size, androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                }
                .background(HighlightYellow, shape)
                .border(3.dp, Ink, shape)
                .padding(horizontal = Dimens.SpaceL, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.figma_ic_glyph_a),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Tap any file to open player!",
                    style = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.32.sp, lineHeight = 17.9.sp),
                    color = Ink,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceS),
                )
                Image(
                    painterResource(R.drawable.figma_ic_dismiss),
                    contentDescription = "Dismiss",
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(start = Dimens.SpaceS)
                        .size(11.dp),
                )
            }
        }
    }
}

/**
 * Figma 1:551 "Download Complete 100% Circular Stamp": SATU lingkaran merah
 * berisi "DONE" + "100%" putih (bukan lingkaran kuning + pill terpisah).
 */
@Composable
private fun DoneStamp(modifier: Modifier = Modifier) {
    val shadow = inkShadowColor()
    Box(
        modifier
            .size(56.dp)
            .drawBehind {
                val off = Offset(2.dp.toPx(), 2.dp.toPx())
                drawCircle(shadow, radius = size.minDimension / 2f, center = center + off)
            }
            .background(ActionRed, CircleShape)
            .border(3.dp, Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "DONE",
                style = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.4.sp),
                color = PureWhite,
            )
            Text(
                "100%",
                style = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.Black, fontSize = 13.sp),
                color = PureWhite,
            )
        }
    }
}

/** Garis putus-putus pemisah meta vs aksi (seperti di kartu desain). */
@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        drawLine(
            color = DividerSoft,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
    }
}

@Preview(name = "DownloadsScreen", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 900)
@Composable
private fun DownloadsScreenPreview() {
    TubeNimeTheme {
        DownloadsScreen(
            items = sampleDownloads(),
            filterLabels = listOf("All (14)", "Videos (10)", "Audio (4)"), // counts verbatim Figma
            storageText = "1.8 GB FREE",
        )
    }
}
