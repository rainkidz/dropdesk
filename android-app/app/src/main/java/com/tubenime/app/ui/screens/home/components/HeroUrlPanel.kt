package com.tubenime.app.ui.screens.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.InkButton
import com.tubenime.app.ui.components.HalftoneBlob
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Figma 1:160 "Section - HERO URL INPUT PANEL" (spec presisi):
 * chip ink "PANEL 01" (Rubik w800 10 putih) + "EXTRACT STREAM" (Rubik w900 16)
 * → input (stroke 3 ink r12, placeholder Inter 13 #5D5C5B) → tombol merah
 * #E53935 "INSPECT LINK" (Baloo w800 18 ls0.45 putih) → stiker "PASTE LINK".
 * Stateless: nilai URL di-hoist via [value]/[onValueChange].
 */
@Composable
fun HeroUrlPanel(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onInspectClick: () -> Unit = {},
    onPasteClick: () -> Unit = {},
) {
    Box(modifier.fillMaxWidth()) {
        Column {
            // Figma: panel putih r16 (node 1:176) MEMBUNGKUS label screamer —
            // "PANEL 01" + "EXTRACT STREAM" berada di dalam kartu (label y136
            // vs panel y112), bukan di atasnya.
            // Halftone band node 1:202 ada DI DALAM panel (y294 < panel bottom
            // y314) → card bawaan tidak boleh menambah halftone sendiri di kaki.
            MangaCard(shape = RoundedCornerShape(Dimens.RadiusHero), showHalftone = false) {
                Column(Modifier.padding(Dimens.SpaceL)) {
                    // Label screamer gaya komic — Rubik w800/w900 per node Figma
                    Row(
                        Modifier.padding(start = 4.dp, bottom = Dimens.SpaceS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    ) {
                        // Figma node 1:178/1:179 "Background": chip ink r2 (KOTAK,
                        // bukan pill) 67.7x16 dengan teks putih
                        StickerBadge("PANEL 01", container = Ink, contentColor = PureWhite, radius = 2.dp)
                        Text(
                            "EXTRACT STREAM",
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Rubik, fontWeight = FontWeight.Black),
                            color = Ink,
                        )
                    }
                    // Figma node 1:185/1:186 "Input Box Field": 318x48, fill paper
                    // #FAF3E7, stroke ink 3dp, r12; glyph link #5D5C5B 16.7x8.3
                    // (node 1:189/1:191) di kolom kiri; placeholder Inter 13 #5D5C5B
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Paper, RoundedCornerShape(Dimens.RadiusTile))
                            .border(3.dp, Ink, RoundedCornerShape(Dimens.RadiusTile))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Ink),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Figma node 1:190: chain-link #5D5C5B 16.7×8.3 di kiri teks
                                    Image(
                                        painterResource(R.drawable.figma_ic_link),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = Dimens.SpaceS)
                                            .size(width = 17.dp, height = 8.dp),
                                    )
                                    Box(Modifier.weight(1f)) {
                                        if (value.isEmpty()) {
                                            Text(
                                                "Paste video URL (YouTube, TikTok, IG...)",
                                                style = MaterialTheme.typography.bodySmall, // Inter 13 #5D5C5B
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        inner()
                                    }
                                }
                            },
                        )
                    }
                    // Figma 1:192 "Action Red CTA Button": 318×62, r12, border 3,
                    // ikon lingkaran-download putih (node 1:193/1:194) + label Baloo
                    // w800 18 ls0.45, hard shadow 4,4 (JSON effects)
                    InkButton(
                        text = "INSPECT LINK", // uppercase verbatim node
                        onClick = onInspectClick,
                        container = ActionRed, // Home hero CTA = #E53935 (bukan CtaRed)
                        leadingIconRes = R.drawable.figma_ic_download_hero,
                        height = 62.dp,
                        shadowOffset = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpaceM),
                    )
                    // Figma node 1:202 — glyph-audit device: elips lembut di bawah
                    // teks dari huruf N (x144) sampai L (x221.5) — kalibrasi device W=370
                    HalftoneBlob(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.SpaceS)
                            .height(16.dp),
                        centerXFraction = 0.461f,
                        centerYFraction = 0.47f,
                        radiusXFraction = 0.12f,
                        radiusYFraction = 0.11f,
                        alpha = 0.21f,
                    )
                }
            }
        }
        // Stiker overlap di luar clip kartu (sudut kanan-bawah)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-12).dp, y = (-2).dp)
                .clickable(onClick = onPasteClick)
        ) {
            // Figma node 1:197 "Highlighter Yellow PASTE LINK Sticker": 112.7x31.8,
            // r6, border ink 3dp, DROP_SHADOW (2.5,2.5), icon clipboard ink 10.4x11.4
            // (node 1:198/1:199) di kiri teks "PASTE LINK" Baloo w900 12 ls0.6.
            StickerBadge(
                "PASTE LINK",
                container = ActiveYellow,
                radius = 6.dp,
                border = 3.dp,
                shadowOffset = 2.5.dp,
                leadingIconRes = R.drawable.figma_ic_clipboard,
                hardShadow = true,
                rotation = -3.88f,
                fontFamily = Baloo2,
                textWeight = FontWeight.ExtraBold,
                textSize = 12.sp,
                horizontalPadding = 12.dp,
                verticalPadding = 2.dp,
            )
        }
    }
}

@Preview(name = "HeroUrlPanel", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun HeroUrlPanelPreview() {
    TubeNimeTheme {
        HeroUrlPanel(
            value = "",
            onValueChange = {},
            Modifier.padding(24.dp),
        )
    }
}
