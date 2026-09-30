package com.tubenime.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/**
 * Stiker/badge manga: fill kuning highlighter (default), merah aksi, atau tinta;
 * border tinta, teks Baloo 12sp uppercase. [rotation] untuk aksen miring.
 * [leadingIconRes] opsional: glyph kecil di kiri teks (mis. flame #E53935 di
 * badge "RAW V3.4" Figma 1:160). [hardShadow] = drop shadow ink offset 2dp
 * (Figma badge RAW punya DROP_SHADOW 2,2 tanpa blur — gaya comic sticker).
 *
 * PERINGATAN UKUR: inner memakai fillMaxHeight — jangan pakai badge ini langsung
 * di Row tak-terbatas tanpa height tetap di modifier luar (pernah meledak
 * setinggi layar di header Browser). Kunci Modifier.height(24.dp) bila parent
 * Row membungkus konten (lihat badge WEB di BrowserScreen).
 */
@Composable
fun StickerBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = HighlightYellow,
    contentColor: Color = Ink,
    rotation: Float = 0f,
    leadingIconRes: Int? = null,
    hardShadow: Boolean = false,
    textWeight: FontWeight = FontWeight.ExtraBold,
    textSize: TextUnit = 10.sp,
    textSpacing: TextUnit = 0.6.sp,
    radius: Dp = 8.dp, // verbatim per node: RAW V3.4 r8, PANEL 01 r2, PASTE LINK r6
    border: Dp = Dimens.BorderSticker, // default 2dp; PASTE LINK 3dp (node 1:197)
    shadowOffset: Dp = 2.dp, // RAW V3.4 (2,2); PASTE LINK (2.5,2.5)
    fontFamily: FontFamily = Rubik,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 3.dp,
) {
    val shape = RoundedCornerShape(radius)
    val ink = MaterialTheme.colorScheme.outline
    Box(modifier, contentAlignment = Alignment.Center) {
        if (hardShadow) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(shadowOffset, shadowOffset)
                    .background(inkShadowColor(), shape)
            )
        }
        Box(
            Modifier
                .rotate(rotation)
                .fillMaxHeight() // badge fixed-height (RAW V3.4 24dp) → isi penuh,
                // shadow matchParentSize tidak "menonjol" di bawah
                .background(container, shape)
                .border(border, ink, shape)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIconRes?.let {
                    Image(
                        painterResource(it),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(width = 10.dp, height = 12.dp),
                    )
                }
                Text(
                    text.uppercase(),
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = textWeight,
                        fontSize = textSize,
                        lineHeight = 18.sp,
                        letterSpacing = textSpacing,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(name = "StickerBadge", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun StickerBadgePreview() {
    TubeNimeTheme {
        StickerBadge("Trending", Modifier.padding(16.dp), rotation = -2f)
    }
}
