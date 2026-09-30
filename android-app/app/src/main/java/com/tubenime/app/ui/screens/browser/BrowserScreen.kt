package com.tubenime.app.ui.screens.browser

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.BottomNavBar
import com.tubenime.app.ui.components.BubbleTail
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.InkButton
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.SpeechBubble
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.components.defaultNavItems
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.BubbleCream
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.EspressoBrown
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.InkSoft
import com.tubenime.app.ui.theme.Inter
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TitleRed
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/**
 * Satu kartu platform di homepage browser (stateless).
 * iconRes = tile utuh dari SVG designs (sama dengan matrix Home):
 * icon_1_222/237/246/256/267/276 -> figma_tile_* (jangan generate sendiri).
 */
data class BrowserPlatformCard(val name: String, val url: String, val iconRes: Int = 0)

/**
 * Browser full sesuai Figma 17:2744 "TubeNime - Browser":
 * - 17:2796 header TOP APP BAR (menu TUBENIME WEB search, icon_17_2798/2808)
 * - 17:2811 speech-bubble URL omnibar (ekor UP, highlighter domain, reload)
 * - 17:2766 toolbar tinta 5 tombol (back/forward/pageInfo/share/bookmark)
 * - 17:2783 pill merah DOWNLOAD THIS VIDEO (glyph 17:2790 + burst HalftoneFade + MP4)
 * Tab-count 17:2832 disembunyikan sesuai keputusan.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    url: String,
    modifier: Modifier = Modifier,
    isHomepage: Boolean = true,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    showDownloadPill: Boolean = false,
    platforms: List<BrowserPlatformCard> = defaultBrowserPlatforms(),
    selectedNavIndex: Int = 1, // Figma: tab Browser aktif
    onNavSelected: (Int) -> Unit = {},
    onUrlChange: (String) -> Unit = {},
    onUrlSubmit: () -> Unit = {},
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onShare: () -> Unit = {},
    onReload: () -> Unit = {},
    onPageInfo: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlatformClick: (BrowserPlatformCard) -> Unit = {},
    onDownloadClick: () -> Unit = {},
    webSlot: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── 17:2796 Header TOP APP BAR ──
        BrowserHeader(
            onMenuClick = onMenuClick,
            onSearchClick = onSearchClick,
        )

        // ── 17:2811 Speech-bubble URL omnibar + reload ──
        BrowserUrlBar(
            url = url,
            onUrlChange = onUrlChange,
            onUrlSubmit = onUrlSubmit,
            onReload = onReload,
        )

        // ── Konten: slot WebView atau homepage grid platform ──
        if (isHomepage) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.SpaceL),
            ) {
                SpeechBubble("Pick a platform to start browsing!", tail = BubbleTail.DOWN)
                Spacer(Modifier.height(Dimens.SpaceL))
                platforms.forEach { card ->
                    MangaCard(
                        shape = MaterialTheme.shapes.medium,
                        showHalftone = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimens.SpaceM),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlatformClick(card) }
                                .padding(Dimens.SpaceL),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (card.iconRes != 0) {
                                Image(
                                    painterResource(card.iconRes),
                                    contentDescription = card.name,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Text(
                                card.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = Dimens.SpaceM),
                            )
                            StickerBadge("BROWSE", container = ActionRed, contentColor = Color.White)
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                webSlot()
            }
        }

        // ── 17:2783 Floating pill DOWNLOAD THIS VIDEO (di atas toolbar) ──
        if (showDownloadPill && !isHomepage) {
            DownloadPill(
                onDownloadClick = onDownloadClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
            )
        }

        // ── 17:2766 Toolbar tinta (verbatim: strip 390x39.5 #1E1B14 + shadow
        // (0,-2), padding 16, 5 tombol ~31.5dp gap 34.8, glyph paper ~15.5).
        // Hanya saat browsing: di homepage tidak ada halaman → tombol mati
        // (desain pun tidak punya homepage browser).
        if (!isHomepage) {
            ToolbarStrip(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = onBack,
                onForward = onForward,
                onPageInfo = onPageInfo,
                onShare = onShare,
                onBookmark = onBookmark,
            )
        }

        // ── Bottom nav (tab Browser aktif) ──
        BottomNavBar(
            items = defaultNavItems(),
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavSelected,
        )
    }
}

/**
 * Strip toolbar tinta 17:2766: 5 tombol sentuh 32dp (glyph paper 16dp),
 * gap 34dp, padding horizontal 16dp / vertikal 4dp, shadow (0,-2) ke atas.
 * Glyph back/forward/share dari SVG designs; pageInfo/bookmark reuse.
 */
@Composable
private fun ToolbarStrip(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onPageInfo: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
) {
    val shadow = inkShadowColor()
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = size.width
                drawRect(shadow, Offset(0f, -2.dp.toPx()), androidx.compose.ui.geometry.Size(w, 2.dp.toPx()))
            }
            .background(InkSoft)
            .padding(horizontal = Dimens.SpaceL, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        ToolbarButton(R.drawable.figma_glyph_back, "Back", active = canGoBack, onClick = onBack)
        ToolbarButton(R.drawable.figma_glyph_back, "Forward", active = canGoForward, mirror = true, onClick = onForward)
        ToolbarButton(R.drawable.figma_ic_safety, "Page info", active = true, onClick = onPageInfo)
        ToolbarButton(R.drawable.figma_glyph_share, "Share", active = true, onClick = onShare)
        ToolbarButton(R.drawable.figma_ic_star, "Bookmarks", active = true, onClick = onBookmark)
    }
}

@Composable
private fun ToolbarButton(
    iconRes: Int,
    desc: String,
    active: Boolean,
    onClick: () -> Unit,
    mirror: Boolean = false,
) {
    Box(
        Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes), desc,
            tint = if (active) Paper else Paper.copy(alpha = 0.4f),
            modifier = Modifier
                .then(if (mirror) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier)
                .size(16.dp),
        )
    }
}

/**
 * 17:2796 Header TOP APP BAR: leading menu (icon_17_2798) + TUBENIME + WEB badge
 * + trailing search (icon_17_2808). Kedua ikon dari SVG designs, jangan buat baru.
 */
/**
 * 17:2796 Header TOP APP BAR (verbatim JSON):
 * bar paper #FAF3E7 + shadow tinta (0,4); tombol 40x40 cream border 2dp +
 * shadow (2,2); TUBENIME = Rubik-Black 22sp ls+1.1 #B7131A + shadow tinta
 * (2,2); badge WEB = kuning #FDC73E border 1dp SIKU (r0) + shadow (1,1),
 * teks Rubik-Black 9sp ls-0.45. Gap TUBENIME→WEB 2.64dp.
 */
@Composable
private fun BrowserHeader(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val shadow = inkShadowColor()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textShadowPx = with(density) { 2.dp.toPx() }
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
            HeaderButton(R.drawable.figma_browser_menu, "Menu", onMenuClick, shadow)
            Text(
                "TUBENIME",
                style = TextStyle(
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    letterSpacing = 1.1.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Ink,
                        offset = Offset(textShadowPx, textShadowPx),
                        blurRadius = 0f,
                    ),
                ),
                color = TitleRed,
                maxLines = 1,
                modifier = Modifier.padding(start = Dimens.SpaceM),
            )
            StickerBadge(
                "WEB",
                Modifier
                    .padding(start = 3.dp)
                    .height(20.dp),
                container = ActiveYellow,
                contentColor = Ink,
                radius = 0.dp,
                border = 1.dp,
                shadowOffset = 1.dp,
                hardShadow = true,
                fontFamily = Rubik,
                textWeight = FontWeight.Black,
                textSize = 9.sp,
                textSpacing = (-0.45).sp,
                horizontalPadding = 4.dp,
                verticalPadding = 2.dp,
            )
            Spacer(Modifier.weight(1f))
            HeaderButton(R.drawable.figma_browser_search, "Search", onSearchClick, shadow)
        }
        // Shadow tinta (0,4) di bawah bar — pola sama seperti BottomNavBar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(shadow),
        )
    }
}

/** Tombol header 40x40 (cream + border ink sudah di drawable) + shadow (2,2). rev2 */
@Composable
private fun HeaderButton(iconRes: Int, desc: String, onClick: () -> Unit, shadow: Color) {
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

/**
 * 17:2811 URL omnibar (verbatim JSON):
 * bubble cream #FFF8EF border tinta 3dp r8 + shadow (3,3); gembok merah
 * #B7131A 12x16 (17:2818 MISSING di export → shield figma_ic_safety tint
 * merah, bentuk terdekat yang ada); domain Rubik-800 12sp + highlighter
 * #FDC73E (17:2820); path Inter-400 11sp #5D5C5B; clear X #5B403D 10dp;
 * ekor diamond 12x12 di atas; tombol reload 36dp cream r8 border 3dp.
 */
@Composable
private fun BrowserUrlBar(
    url: String,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = MaterialTheme.colorScheme.outline
        val bubbleShadow = inkShadowColor()
        val bubbleBody = RoundedCornerShape(8.dp)
        val domainRange = domainRange(url)
        Column(Modifier.weight(1f)) {
            // Ekor diamond 12x12 (17:2828): separuh bawah menumpuk di balik badan.
            androidx.compose.foundation.Canvas(
                Modifier
                    .padding(start = 8.dp)
                    .size(12.dp)
                    .offset(y = 6.dp),
            ) {
                val d = 2.dp.toPx()
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(0f, size.height / 2f)
                    close()
                }
                withTransform({ translate(d, d) }) { drawPath(p, bubbleShadow) }
                drawPath(p, BubbleCream)
                drawPath(p, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
            }
            MangaCard(
                shape = bubbleBody,
                fill = BubbleCream,
                showHalftone = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.figma_ic_safety),
                    contentDescription = null,
                    tint = TitleRed,
                    modifier = Modifier.size(width = 12.dp, height = 16.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = Inter,
                        fontSize = 11.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.outline),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onUrlSubmit() }),
                    visualTransformation = DomainHighlightTransformation(domainRange),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (url.isBlank()) {
                                Text(
                                    "Search or type URL",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (url.isNotBlank()) {
                    Icon(
                        painterResource(R.drawable.figma_ic_dismiss),
                        contentDescription = "Clear",
                        tint = EspressoBrown,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(10.dp)
                            .clickable { onUrlChange("") },
                    )
                }
            }
            }
        }
        // Tombol reload 36dp cream r8 border 3dp + shadow (3,3), glyph ink.
        // Glyph panah-melingkar tidak ada di export mana pun → ic_refresh repo.
        val reloadShadow = inkShadowColor()
        val reloadShape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .padding(start = Dimens.SpaceM)
                .size(36.dp)
                .drawBehind {
                    val s = size
                    val ro = 3.dp.toPx()
                    val rr = 8.dp.toPx()
                    withTransform({ translate(ro, ro) }) {
                        drawRoundRect(reloadShadow, Offset.Zero, s, androidx.compose.ui.geometry.CornerRadius(rr))
                    }
                }
                .background(BubbleCream, reloadShape)
                .border(3.dp, Ink, reloadShape)
                .clickable(onClick = onReload),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_refresh), "Reload",
                tint = Ink,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** Rentang domain dalam URL mentah (untuk highlighter 17:2820). */
private fun domainRange(raw: String): IntRange? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val start = t.indexOf("://").let { if (it < 0) 0 else it + 3 }
    val slash = t.indexOf('/', start)
    val end = if (slash < 0) t.length else slash
    if (end <= start || start >= t.length) return null
    return start until end
}

/**
 * Highlighter domain (17:2820 Highlighter Sweep): domain = Rubik ExtraBold
 * 12sp tinta + bg kuning; sisa path = Inter 11sp muted. Satu inner() —
 * aman untuk ukur (VisualTransformation, bukan overlay ganda).
 */
private class DomainHighlightTransformation(private val range: IntRange?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val r = range
        if (r == null || r.first >= text.length) return TransformedText(text, OffsetMapping.Identity)
        val end = minOf(r.last + 1, text.length)
        val span = AnnotatedString(
            text.text,
            listOf(
                AnnotatedString.Range(
                    SpanStyle(
                        fontFamily = Rubik,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        background = HighlightYellow,
                    ),
                    r.first,
                    end,
                ),
                AnnotatedString.Range(
                    SpanStyle(fontFamily = Inter, fontSize = 11.sp, color = TextMuted),
                    end,
                    text.length,
                ),
            ),
        )
        return TransformedText(span, OffsetMapping.Identity)
    }
}

/**
 * 17:2783 Pill merah: burst HalftoneFade di belakang (17:2785 mask → reuse
 * komponen, sesuai keputusan) + pill ActionRed + glyph putih 17:2790 +
 * teks Baloo2 + badge MP4 + hard shadow + wiggle -2deg saat muncul/di-tap.
 */
@Composable
private fun DownloadPill(
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tapped by remember { mutableStateOf(false) }
    val wiggle by animateFloatAsState(if (tapped) -2f else 0f, label = "pillWiggle")
    val pillShadow = inkShadowColor()
    Box(modifier) {
        HalftoneFade(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter),
            maxAlpha = 0.35f,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .rotate(wiggle)
                .drawBehind {
                    val off = Offset(2.dp.toPx(), 2.dp.toPx())
                    drawRoundRect(pillShadow, off, size, androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
                }
                .background(ActionRed, RoundedCornerShape(20.dp))
                .border(2.dp, Ink, RoundedCornerShape(20.dp))
                .clickable {
                    tapped = true
                    onDownloadClick()
                }
                .padding(horizontal = Dimens.SpaceL, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.figma_ic_download_white),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "DOWNLOAD THIS VIDEO",
                    style = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
                StickerBadge(
                    "MP4",
                    // WAJIB height tetap: inner StickerBadge memakai fillMaxHeight
                    // yang meledak setinggi layar bila parent Row tak-terbatas
                    // (bar putih raksasa di samping teks = background badge ini).
                    Modifier
                        .padding(start = 8.dp)
                        .height(24.dp),
                    container = PureWhite,
                    contentColor = Ink,
                    textSize = 10.sp,
                )
            }
        }
        LaunchedEffect(tapped) {
            if (tapped) {
                kotlinx.coroutines.delay(220)
                tapped = false
            }
        }
    }
}

fun defaultBrowserPlatforms(): List<BrowserPlatformCard> {
    val anime = com.tubenime.app.ui.screens.home.components.AnimeChannels
    return listOf(
        BrowserPlatformCard("YouTube", anime.platformTrendingUrl("YouTube")!!, R.drawable.figma_tile_youtube),
        BrowserPlatformCard("TikTok", anime.platformTrendingUrl("TikTok")!!, R.drawable.figma_tile_tiktok),
        BrowserPlatformCard("Instagram", anime.platformTrendingUrl("Instagram")!!, R.drawable.figma_tile_instagram),
        BrowserPlatformCard("Bilibili", anime.platformTrendingUrl("Bilibili")!!, R.drawable.figma_tile_bilibili),
        BrowserPlatformCard("Facebook", anime.platformTrendingUrl("Facebook")!!, R.drawable.figma_tile_facebook),
        BrowserPlatformCard("Threads", anime.platformTrendingUrl("Threads")!!, R.drawable.figma_tile_threads),
    )
}

@Preview(name = "BrowserScreen — homepage", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 844)
@Composable
private fun BrowserHomepagePreview() {
    TubeNimeTheme {
        BrowserScreen(url = "", isHomepage = true)
    }
}

@Preview(name = "BrowserScreen — browsing", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 844)
@Composable
private fun BrowserBrowsingPreview() {
    TubeNimeTheme {
        BrowserScreen(
            url = "m.youtube.com/watch?v=kickback",
            isHomepage = false,
            canGoBack = true,
            showDownloadPill = true,
            webSlot = { InkButton("WebView slot") },
        )
    }
}
