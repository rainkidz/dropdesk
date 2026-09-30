package com.tubenime.app.ui.screens.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.R
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TitleRed
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

data class ShortcutItem(
    val title: String,
    val subtitle: String,
    val iconRes: Int, // kotak ikon putih 32dp hasil konversi SVG Figma (icon_1_294/306/318)
    val fill: Color = HighlightYellow, // Figma 1:292/1:304/1:316: ketiganya #FFC940
)

/**
 * Isi dari [AnimeChannels.quickChannels]: 2 saran acak lintas platform
 * (berganti tiap hari) + 1 spotlight anime harian "Today:".
 */
fun defaultShortcuts(): List<ShortcutItem> = AnimeChannels.quickChannels()

// Judul Baloo w900 16; sub Rubik w700 10 #705400; header Rubik w800 12 ls0.6
private val StripTitle = TextStyle(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 20.sp)
private val StripSubtitle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 18.sp)
private val SectionHeader = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.6.sp)

/**
 * Figma 1:160 "Section - SHORTCUT CARDS" (spec presisi): ketiga strip kuning
 * highlighter #FFC940 + border tinta r12 + drop shadow; ikon kotak putih 32dp
 * (headphone / clapperboard / trending) + panah lingkaran ink 24dp di ujung
 * kanan (node 1:301/1:313/1:325). Header "QUICK CHANNELS" + glyph bookmark
 * merah (node 1:287) + "VIEW ALL" Rubik w900 10 ls0.6 #B7131A.
 */
@Composable
fun ShortcutCards(
    modifier: Modifier = Modifier,
    items: List<ShortcutItem> = defaultShortcuts(),
    onViewAllClick: () -> Unit = {},
    onItemClick: (ShortcutItem) -> Unit = {},
) {
    val iconBoxShape = RoundedCornerShape(Dimens.RadiusSticker) // Figma kotak ikon r6
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.figma_ic_glyph_b), // bookmark merah node 1:287
                contentDescription = null,
                modifier = Modifier.padding(end = Dimens.SpaceS).size(width = 10.dp, height = 12.dp),
            )
            Text(
                "QUICK CHANNELS",
                style = SectionHeader,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                "VIEW ALL",
                style = SectionHeader.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
                color = TitleRed,
                modifier = Modifier
                    .padding(start = Dimens.SpaceM)
                    .clickable(onClick = onViewAllClick),
            )
        }
        items.forEach { item ->
            MangaCard(
                shape = RoundedCornerShape(Dimens.RadiusTile),
                showHalftone = false,
                fill = item.fill,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(bottom = Dimens.SpaceS),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Kotak ikon putih 32dp + hard shadow 1.5dp (Figma icon_1_294/306/318)
                    Box(Modifier.size(32.dp)) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .offset(1.5.dp, 1.5.dp)
                                .background(inkShadowColor(), iconBoxShape),
                        )
                        Image(
                            painterResource(item.iconRes),
                            contentDescription = item.title,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Dimens.SpaceM),
                    ) {
                        Text(item.title, style = StripTitle, color = Ink)
                        Text(item.subtitle, style = StripSubtitle, color = OnYellow)
                    }
                    // Panah lingkaran ink di ujung kanan (node 1:301/1:313/1:325)
                    Image(
                        painterResource(R.drawable.figma_strip_arrow),
                        contentDescription = null,
                        modifier = Modifier.padding(start = Dimens.SpaceS).size(24.dp),
                    )
                }
            }
        }
    }
}

@Preview(name = "ShortcutCards", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun ShortcutCardsPreview() {
    TubeNimeTheme {
        ShortcutCards(Modifier.padding(16.dp))
    }
}
