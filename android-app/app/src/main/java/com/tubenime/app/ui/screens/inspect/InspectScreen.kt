package com.tubenime.app.ui.screens.inspect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.InkButton
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.CtaRed
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.EspressoBrown
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.InkSoft
import com.tubenime.app.ui.theme.Inter
import com.tubenime.app.ui.theme.LabelTiny
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TitleRed
import com.tubenime.app.ui.theme.TubeNimeTheme

/** Data satu opsi format (stateless — UI menerima daftar jadi). */
data class FormatOption(
    val quality: String,
    val detail: String,
    val size: String,
    val tag: String,
)

data class InspectUiModel(
    val platform: String,
    val title: String,
    val duration: String,
    val resolution: String,
    val formats: List<FormatOption>,
)

fun sampleInspect(): InspectUiModel = InspectUiModel(
    platform = "YouTube",
    title = "Chainsaw Man - Kick Back (Official Anime Opening 4K)",
    duration = "3:14",
    resolution = "4K Ultra HD",
    formats = listOf(
        FormatOption("1080p FHD 60fps", "MP4 • H.264 • High Bitrate", "148 MB", "BEST"),
        FormatOption("720p HD", "MP4 • Standard Dynamic Range", "64 MB", "STREAM"),
        FormatOption("1440p 2K", "WEBM • VP9 Opus", "310 MB", "STUDIO"),
        FormatOption("480p SD", "MP4 • Low Bandwidth Saver", "28 MB", "DATA"),
    ),
)

// ── Text styles presisi dari node Figma (audit TEXT_SPEC.txt) ──
private val WordmarkStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.55).sp) // Baloo w800 22 ls-0.55 #B7131A
private val InspectLabel = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.45.sp) // Rubik w900 9 ls0.45
private val MetaStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp) // Inter w500 12 #5B403D
private val QualityChip = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 15.sp) // Inter w700 10
private val ChooseFormatStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = (-0.45).sp) // Baloo w800 18 ls-0.45
private val ToggleStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 0.4.sp) // Baloo w800 16 ls0.4
private val QualityStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 22.sp) // Rubik w900 18
private val DetailStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp) // Inter w500 16 #5B403D
private val SizeStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 17.sp) // Baloo w900 17
private val TagStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 9.sp, lineHeight = 13.5.sp) // Rubik w400 9
private val CtaStyle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 0.55.sp) // Baloo w900 22 ls0.55 putih

/**
 * Figma 1:351 "TubeNime - Inspect Result" (spec presisi): wordmark merah gelap
 * #B7131A → kartu preview (thumbnail −1.5° + chip platform #DB322F + tag durasi
 * putih) → toggle VIDEO/AUDIO (aktif #FDC73E) → tiket format (Rubik w900 18,
 * detail Inter 16 #5B403D, tag BEST/STREAM/MOBILE/STUDIO/DATA) → tombol
 * DOWNLOAD VIDEO (#DB322F, Baloo w900 22) + footer "AD-FREE DIRECT INKING
 * STREAM" + ribbon premium.
 * [selectedType]/[selectedFormatIndex] di-hoist; tanpa logika download.
 */
@Composable
fun InspectScreen(
    info: InspectUiModel,
    modifier: Modifier = Modifier,
    selectedType: String = "video",
    onTypeChange: (String) -> Unit = {},
    selectedFormatIndex: Int = 0,
    onFormatSelect: (Int) -> Unit = {},
    onBack: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceL)
                .padding(bottom = 96.dp),
        ) {
            // ── Figma: header wordmark + label INSPECT ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Figma: "Button - Go back" 40x40 (hasil konversi SVG)
                Image(
                    painterResource(R.drawable.figma_btn_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .size(40.dp),
                )
                Text(
                    "TubeNime",
                    style = WordmarkStyle,
                    color = TitleRed, // Figma: #B7131A
                    modifier = Modifier.padding(start = Dimens.SpaceS),
                )
                Text(
                    "INSPECT",
                    style = InspectLabel,
                    color = InkSoft,
                    modifier = Modifier.padding(start = Dimens.SpaceM),
                )
                Box(Modifier.weight(1f))
                // Figma: tombol share kotak putih 40 r8 di kanan header.
                Image(
                    painterResource(R.drawable.figma_btn_share),
                    contentDescription = "Share",
                    modifier = Modifier
                        .clickable(onClick = onShareClick)
                        .size(40.dp),
                )
            }

            // ── Figma: preview card — thumbnail miring + chip platform + tag durasi ──
            MangaCard(Modifier.fillMaxWidth().padding(top = Dimens.SpaceXL)) {
                Column(Modifier.padding(Dimens.SpaceL)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .rotate(-1.5f), // "Thumbnail Frame with -1.5deg Manga Cel Tilt"
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            // Figma: "Play button overlay" (hasil konversi SVG) —
                            // placeholder sampai thumbnail nyata (Coil AsyncImage)
                            Image(
                                painterResource(R.drawable.figma_play_overlay),
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).size(56.dp),
                            )
                        }
                        // Chip platform overlap — #DB322F, teks putih Rubik w900 10.
                        // Tinggi dikunci (inner badge fillMaxHeight!) + glyph play.
                        StickerBadge(
                            info.platform,
                            Modifier
                                .align(Alignment.TopStart)
                                .offset(y = (-6).dp, x = (-4).dp)
                                .height(28.dp),
                            container = CtaRed,
                            contentColor = PureWhite,
                            rotation = -2f,
                            leadingIconRes = R.drawable.figma_ic_yt_chip,
                        )
                        // Figma: "Video Duration Tag Right Corner" — teks putih
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(InkSoft, RoundedCornerShape(Dimens.RadiusInner))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                info.duration,
                                style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = (-0.25).sp),
                                color = PureWhite,
                            )
                        }
                    }
                    Text(
                        info.title,
                        style = MaterialTheme.typography.titleLarge, // Baloo w700 20 ls-0.5
                        color = InkSoft,
                        modifier = Modifier.padding(top = Dimens.SpaceM),
                    )
                    // Figma: meta "3:14 • 4K Ultra HD" Inter w500 12 #5B403D + chip "1080p 60fps"
                    Row(
                        Modifier.padding(top = Dimens.SpaceXS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    ) {
                        Text(
                            "${info.duration} • ${info.resolution}",
                            style = MetaStyle,
                            color = EspressoBrown,
                        )
                        Text(
                            "1080p 60fps",
                            style = QualityChip,
                            color = InkSoft,
                        )
                    }
                }
            }

            // ── Figma: "CHOOSE FORMAT" + "FAST EXTRACTION" ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceXL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CHOOSE FORMAT",
                    style = ChooseFormatStyle,
                    color = InkSoft,
                    modifier = Modifier.weight(1f),
                )
                Text("FAST EXTRACTION", style = LabelTiny, color = EspressoBrown)
            }
            // ── Figma: toggle VIDEO / AUDIO — aktif #FDC73E, inactive putih ──
            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.SpaceS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                listOf(
                    "VIDEO" to R.drawable.figma_ic_video_toggle,
                    "AUDIO" to R.drawable.figma_ic_audio_toggle,
                ).forEach { (type, iconRes) ->
                    val active = type == selectedType
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Dimens.RadiusInner))
                            .background(if (active) ActiveYellow else PureWhite)
                            .border(2.dp, Ink, RoundedCornerShape(Dimens.RadiusInner))
                            .clickable { onTypeChange(type) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(width = 18.dp, height = 16.dp),
                            )
                            Text(type, style = ToggleStyle, color = InkSoft)
                        }
                    }
                }
            }

            // ── Figma: format "tickets" dengan tag sisi kanan ──
            info.formats.forEachIndexed { index, format ->
                val selected = index == selectedFormatIndex
                MangaCard(
                    shape = RoundedCornerShape(Dimens.RadiusTile),
                    showHalftone = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onFormatSelect(index) }
                            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Radio tinta (Figma: "Custom Circular Ink Radio Button")
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (selected) Ink else Color.Transparent)
                                .border(2.dp, Ink, CircleShape),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = Dimens.SpaceM),
                        ) {
                            Text(format.quality, style = QualityStyle, color = InkSoft)
                            Text(format.detail, style = DetailStyle, color = EspressoBrown)
                        }
                        // Separator dashed vertikal (desain tiket format).
                        androidx.compose.foundation.Canvas(
                            Modifier
                                .padding(horizontal = Dimens.SpaceS)
                                .size(width = 2.dp, height = 44.dp),
                        ) {
                            drawLine(
                                color = TextMuted.copy(alpha = 0.5f),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(6f, 5f),
                                ),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(format.size, style = SizeStyle, color = InkSoft)
                            // Figma: BEST = teks putih di chip #DB322F; HQ = w900 #705400;
                            // lainnya Rubik w400 9 #5B403D
                            if (format.tag == "BEST") {
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .background(CtaRed, RoundedCornerShape(Dimens.RadiusBadge))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(format.tag, style = TagStyle, color = PureWhite)
                                }
                            } else {
                                Text(
                                    format.tag,
                                    style = if (format.tag == "HQ") TagStyle.copy(fontWeight = FontWeight.Black) else TagStyle,
                                    color = if (format.tag == "HQ") OnYellow else EspressoBrown,
                                )
                            }
                        }
                    }
                }
            }

            // ── Figma: ribbon premium di atas CTA (Rubik w900 10 ls0.5 ink) ──
            StickerBadge(
                "PREMIUM: VIDEO+AUDIO 1080P+ & PLAYLIST",
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Dimens.SpaceXL),
                rotation = -1f,
            )
            // ── Figma: "Main Full-Width Download Action Button" #DB322F, Baloo w900 22 ──
            InkButton(
                text = "DOWNLOAD VIDEO",
                onClick = onDownloadClick,
                textStyle = CtaStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceS),
            )
            // ── Figma: footer "AD-FREE DIRECT INKING STREAM" Rubik w700 10 #5B403D ──
            Text(
                "AD-FREE DIRECT INKING STREAM",
                style = LabelTiny,
                color = EspressoBrown,
                modifier = Modifier
                    .padding(top = Dimens.SpaceS)
                    .align(Alignment.CenterHorizontally),
            )
            HalftoneFade(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.HalftoneHeight)
                    .padding(top = Dimens.SpaceS),
            )
        }
    }
}

// border import dipakai radio tinta & toggle
@Preview(name = "InspectScreen", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 900)
@Composable
private fun InspectScreenPreview() {
    TubeNimeTheme {
        InspectScreen(info = sampleInspect())
    }
}
