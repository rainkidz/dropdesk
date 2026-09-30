package com.tubenime.app.ui.screens.queue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.inkShadowColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.BubbleTail
import com.tubenime.app.ui.components.BottomNavBar
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.PremiumInkBanner
import com.tubenime.app.ui.components.SpeechBubble
import com.tubenime.app.ui.components.defaultNavItems
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.ChipBrown
import com.tubenime.app.ui.theme.ChipOlive
import com.tubenime.app.ui.theme.CtaRed
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.EspressoBrown
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.InkSoft
import com.tubenime.app.ui.theme.Inter
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TitleRed
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Satu item antrean (stateless — dipetakan dari DownloadQueue.QueueItem).
 * [stateLabel] nilai kanonik: pending/downloading/paused/completed/failed.
 */
data class QueueItem(
    val id: Int,
    val fileName: String,
    val meta: String,
    val stateLabel: String,
    val progress: Int? = null, // persen 0-100; null = belum mulai
    val speed: String? = null,
    val progressMeta: String? = null, // "850 MB / 1.1 GB", "42 MB / 180 MB (23%)"
    val statusText: String? = null, // "Waiting in line... 95 MB", "ETA: ~1m"
    val displayState: String? = null, // override tampilan: QUEUED / STANDBY / …
)

fun sampleQueue(): List<QueueItem> = listOf(
    QueueItem(1, "Attack_on_Titan_Final_Ep_1080p.mp4", "SUB", "downloading", progress = 74, speed = "8.4 MB/s", progressMeta = "850 MB / 1.1 GB"),
    QueueItem(2, "Bocchi_The_Rock_Live_Performance.mp4", "1080p", "pending", progressMeta = "Queued • 42 MB / 180 MB (23%)", statusText = "ETA: ~1m", displayState = "QUEUED"),
    QueueItem(3, "Cyberpunk_Edgerunners_AMV.mp4", "AMV", "pending", statusText = "Waiting in line... 95 MB", displayState = "STANDBY"),
)

// ── Text styles presisi dari node Figma (audit TEXT_SPEC.txt) ──
private val WordmarkStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.1.sp) // Rubik w900 22 ls1.1 #B7131A
private val TitleStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.7).sp) // Baloo w800 28
private val FileNameStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp) // Rubik w700 16
private val MetaTagStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 15.sp) // Rubik w400 10
private val StatusStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.6.sp) // DOWNLOADING w900 ls0.6
private val SpeedStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.4.sp) // Rubik w700 12 ls0.4
private val ProgressMetaStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.4.sp) // Inter w600 11 ls0.4
private val SmallActionStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.4.sp) // Pause All w800 12 ls0.4
private val StorageStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.6.sp) // Rubik w800 10 ls0.6
private val PositionStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 20.sp) // Baloo w900 16

/** Warna badge posisi sesuai Figma: #1 #DB322F (putih), #2 #FDC73E (ink), #3 tinta (paper). */
private fun positionColor(pos: Int): Color = when (pos) {
    1 -> CtaRed
    2 -> ActiveYellow
    else -> InkSoft
}

/** Warna status verbatim: DOWNLOADING #B7131A, QUEUED #785A00, STANDBY #5D5C5B. */
private fun statusColor(state: String): Color = when (state) {
    "downloading", "DOWNLOADING" -> TitleRed
    "pending", "QUEUED" -> ChipOlive
    "failed" -> CtaRed
    "completed" -> OnYellow
    "STANDBY" -> EspressoBrown
    else -> EspressoBrown // paused
}

/**
 * Figma 1:5 "TubeNime - Download Queue" (spec presisi): wordmark TUBENIME
 * (Rubik w900 22 ls1.1 #B7131A) + judul + chip LIVE (#DB322F) → count "N ITEMS"
 * + Pause All / Clear Failed → panel item dengan badge posisi sirkular overlap
 * kiri (#1 merah / #2 kuning / #3 tinta, Baloo w900 16) + status & speed
 * berwarna verbatim + progress bar tinta → row Storage/Wi-Fi Only → banner
 * premium ink-fill (teks verbatim) + tombol Upgrade kuning. Aksi di-hoist.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    items: List<QueueItem>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPauseAll: () -> Unit = {},
    onClearFailed: () -> Unit = {},
    onItemPauseResume: (QueueItem) -> Unit = {},
    onItemRemove: (QueueItem) -> Unit = {},
    onItemClick: (QueueItem) -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    storageText: String = "Storage: 48.2 GB free / 128 GB", // host isi nilai nyata
    wifiOnly: Boolean = true,
    searchActive: Boolean = false,
    searchQuery: String = "",
    onSearchToggle: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    selectedNavIndex: Int = 2, // Figma 1:5: tab Downloads aktif
    onNavSelected: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Figma 1:7 "Header - TopAppBar": menu + TUBENIME + search + strip.
        // Sama pola BrowserHeader (kotak 40dp cream + shadow + strip 6dp).
        QueueHeader(onMenuClick = onBack, onSearchClick = onSearchToggle)

        // ── Figma 1:17 judul + LIVE + count box ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Download Queue",
                style = TitleStyle,
                color = InkSoft,
                modifier = Modifier.weight(1f),
            )
            // 1:27 "Task Count Comic Bubble Tag": kotak bordered berisi count.
            Box(
                Modifier
                    .background(PureWhite, RoundedCornerShape(4.dp))
                    .border(2.dp, Ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${items.size}", style = StorageStyle, color = Ink)
                    Text("ITEMS", style = StorageStyle, color = Ink)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Dimens.SpaceL, bottom = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LIVE pill merah + dot kuning (render 1:5).
            Row(
                Modifier
                    .background(CtaRed, RoundedCornerShape(Dimens.RadiusPill))
                    .border(2.dp, Ink, RoundedCornerShape(Dimens.RadiusPill))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(ActiveYellow, CircleShape),
                )
                Text(
                    "LIVE",
                    style = StatusStyle.copy(fontSize = 12.sp),
                    color = PureWhite,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        // Field filter inline (pola DownloadsScreen).
        if (searchActive) {
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceL)
                    .padding(bottom = Dimens.SpaceS),
                placeholder = {
                    Text("Filter queue…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
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

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceL),
        ) {
            // ── Figma: aksi massal Pause All / Clear Failed — rata KIRI (x20) ──
            // Tombol putih r8 border 2dp + shadow (2,2) + ikon ink.
            Row(
                Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Start,
            ) {
                val bulkShadow = inkShadowColor()
                Row(
                    Modifier
                        .drawBehind {
                            val o = 2.dp.toPx()
                            drawRoundRect(bulkShadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
                        }
                        .background(PureWhite, RoundedCornerShape(Dimens.RadiusInner))
                        .border(2.dp, Ink, RoundedCornerShape(Dimens.RadiusInner))
                        .clickable(onClick = onPauseAll)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painterResource(R.drawable.figma_ic_pause),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp).size(14.dp),
                    )
                    Text("Pause All", style = SmallActionStyle, color = Ink)
                }
                Row(
                    Modifier
                        .padding(start = Dimens.SpaceS)
                        .drawBehind {
                            val o = 2.dp.toPx()
                            drawRoundRect(bulkShadow, Offset(o, o), size, CornerRadius(8.dp.toPx()))
                        }
                        .background(PureWhite, RoundedCornerShape(Dimens.RadiusInner))
                        .border(2.dp, Ink, RoundedCornerShape(Dimens.RadiusInner))
                        .clickable(onClick = onClearFailed)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painterResource(R.drawable.figma_ic_clearfailed),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp).size(14.dp),
                    )
                    Text("Clear Failed", style = SmallActionStyle, color = Ink)
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    SpeechBubble("Queue is empty — all clear!", tail = BubbleTail.UP)
                }
            }

            items.forEachIndexed { index, item ->
                val position = index + 1
                Box(Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceXL)) {
                    MangaCard(shape = RoundedCornerShape(Dimens.RadiusTile), showHalftone = false) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(start = 48.dp, end = Dimens.SpaceL, top = Dimens.SpaceL, bottom = Dimens.SpaceL),
                        ) {
                            Text(item.fileName, style = FileNameStyle, color = InkSoft, maxLines = 2)
                            // Figma: chip meta bordered (SUB / 1080p / AMV) — Rubik w400 10.
                            Row(
                                Modifier
                                    .padding(top = 4.dp)
                                    .background(PureWhite, RoundedCornerShape(4.dp))
                                    .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(item.meta, style = MetaTagStyle, color = InkSoft)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = Dimens.SpaceS),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            Text(
                                (item.displayState ?: item.stateLabel).uppercase(),
                                style = StatusStyle,
                                color = statusColor(item.displayState ?: item.stateLabel),
                            )
                                item.speed?.let {
                                    Text(
                                        "$it • ${item.progress ?: 0}%", // "8.4 MB/s • 74%"
                                        style = SpeedStyle,
                                        color = if (item.stateLabel == "downloading") TitleRed else statusColor(item.stateLabel),
                                        modifier = Modifier.padding(start = Dimens.SpaceM),
                                    )
                                }
                                Box(Modifier.weight(1f))
                                // Aksi per-item: tombol kotak putih r8 border 2dp +
                                // shadow (glyph asli Figma pause/play/close).
                                val itemShadow = inkShadowColor()
                                when (item.stateLabel) {
                                    "downloading" -> ItemActionBox(
                                        R.drawable.figma_ic_pause_glyph, "Pause",
                                        { onItemPauseResume(item) }, itemShadow,
                                        Modifier.padding(end = Dimens.SpaceS),
                                    )
                                    "paused" -> ItemActionBox(
                                        R.drawable.figma_ic_play_glyph, "Resume",
                                        { onItemPauseResume(item) }, itemShadow,
                                        Modifier.padding(end = Dimens.SpaceS),
                                    )
                                }
                                ItemActionBox(
                                    R.drawable.figma_ic_close_glyph, "Remove",
                                    { onItemRemove(item) }, itemShadow,
                                )
                            }
                            // Figma: "850 MB / 1.1 GB" / "Queued • … (23%)" / "Waiting in line..."
                            if (item.progressMeta != null || item.statusText != null) {
                                Text(
                                    item.progressMeta ?: item.statusText.orEmpty(),
                                    style = ProgressMetaStyle,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            item.statusText?.let {
                                if (item.progressMeta != null) {
                                    Text(it, style = ProgressMetaStyle, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            // Progress bar tinta (Figma: thick bar, fill merah utk #1)
                            item.progress?.let { p ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = Dimens.SpaceS)
                                        .height(12.dp)
                                        .border(2.dp, Ink, CircleShape)
                                        .padding(2.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((p / 100f).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(
                                                if (position == 1) CtaRed else ActiveYellow,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                    // Badge posisi sirkular overlap tepi kiri (Figma: racing number)
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-10).dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(positionColor(position))
                            .border(3.dp, Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "#$position",
                            style = PositionStyle,
                            color = when (position) {
                                1 -> PureWhite
                                2 -> Ink
                                else -> Paper
                            },
                        )
                    }
                }
            }

            // ── Figma: row Storage dashed + ikon fan + chip Wi-Fi Only ──
            // Ikon fan MISSING di export → wifi figma_ic_wifi (merah, designs).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpaceM)
                    .drawWithContent {
                        drawContent()
                        val r = 8.dp.toPx()
                        val path = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                        }
                        drawPath(
                            path, Ink,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            ),
                        )
                    }
                    .padding(horizontal = Dimens.SpaceM, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(R.drawable.figma_ic_wifi),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    storageText,
                    style = StorageStyle,
                    color = Ink,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceS),
                )
                if (wifiOnly) {
                    Box(
                        Modifier
                            .background(ActiveYellow, RoundedCornerShape(Dimens.RadiusBadge))
                            .border(2.dp, Ink, RoundedCornerShape(Dimens.RadiusBadge))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Wi-Fi Only", style = StorageStyle, color = ChipBrown)
                    }
                }
            }

            // ── Figma: "Aside - Shōnen Premium Ink-Fill" + tombol Upgrade ──
            PremiumInkBanner(
                title = "Shōnen Premium: queue up to 20 downloads at once!",
                subtitle = "",
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpaceXL),
                ctaLabel = "Upgrade",
                onCtaClick = onUpgradeClick,
            )
            HalftoneFade(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.HalftoneHeight),
                maxAlpha = 0.15f,
            )
        }

        // ── Figma 1:5 "BottomNavBar" (y882, 55dp): tab Downloads aktif ──
        BottomNavBar(
            items = defaultNavItems(),
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavSelected,
        )
    }
}

/**
 * Figma 1:7 header Queue: menu + TUBENIME + search 40dp + strip bawah.
 * Pola sama seperti BrowserHeader (kotak cream + shadow + strip 6dp).
 */
@Composable
private fun QueueHeader(onMenuClick: () -> Unit, onSearchClick: () -> Unit) {
    val shadow = inkShadowColor()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Paper),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceL, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueHeaderButton(R.drawable.figma_browser_menu, "Menu", onMenuClick, shadow)
            Text(
                "TUBENIME",
                style = WordmarkStyle,
                color = TitleRed,
                maxLines = 1,
                modifier = Modifier.padding(start = Dimens.SpaceM),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            QueueHeaderButton(R.drawable.figma_browser_search, "Search", onSearchClick, shadow)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(shadow),
        )
    }
}

@Composable
private fun QueueHeaderButton(iconRes: Int, desc: String, onClick: () -> Unit, shadow: Color) {
    Box(
        Modifier
            .size(40.dp)
            .drawBehind {
                val s = size
                drawRect(shadow, Offset(2.dp.toPx(), 2.dp.toPx()), s)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(iconRes),
            contentDescription = desc,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** Tombol aksi per-item: kotak putih r8 border 2dp + shadow + glyph 18dp. */
@Composable
private fun ItemActionBox(iconRes: Int, desc: String, onClick: () -> Unit, shadow: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .drawBehind {
                val s = size
                drawRect(shadow, Offset(2.dp.toPx(), 2.dp.toPx()), s)
            }
            .background(PureWhite, RoundedCornerShape(8.dp))
            .border(2.dp, Ink, RoundedCornerShape(8.dp))
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

@Preview(name = "QueueScreen", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 900)
@Composable
private fun QueueScreenPreview() {
    TubeNimeTheme {
        QueueScreen(items = sampleQueue())
    }
}
