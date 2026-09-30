package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.InkSoft
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.Rubik
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/**
 * Banner premium ink-fill (Figma Queue 1:5 "Aside - Shōnen Premium Ink-Fill"):
 * panel tinta gelap + teks paper + tombol Upgrade kuning di kanan.
 */
@Composable
fun PremiumInkBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ctaLabel: String = "Upgrade",
    onCtaClick: () -> Unit = {},
) {
    val ink = MaterialTheme.colorScheme.outline
    val bannerShape = RoundedCornerShape(Dimens.RadiusTile)
    val ctaShape = RoundedCornerShape(10.dp)

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = Dimens.HardShadowOffset, y = Dimens.HardShadowOffset)
                .background(inkShadowColor(), bannerShape)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(InkSoft, bannerShape)
                .border(3.dp, ink, bannerShape)
                .padding(start = Dimens.SpaceL, top = Dimens.SpaceM, bottom = Dimens.SpaceM, end = Dimens.SpaceM),
        ) {
            Column(Modifier.weight(1f)) {
                if (subtitle.isBlank()) {
                    // Figma Queue: teks satu-baris Rubik w800 12 ls0.3 paper
                    Text(
                        title,
                        style = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.3.sp, lineHeight = 15.sp),
                        color = Paper,
                    )
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite.copy(alpha = 0.75f),
                    )
                }
            }
            // Tombol Upgrade kuning (Figma: Button f:#fdc73e, teks #705400)
            Box(
                Modifier
                    .background(ActiveYellow, ctaShape)
                    .border(2.dp, ink, ctaShape)
                    .clickable(onClick = onCtaClick)
                    .padding(horizontal = Dimens.SpaceL, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ctaLabel, // Figma 1:5 verbatim "Upgrade" (Rubik w800 12 ls0.4)
                    style = MaterialTheme.typography.labelMedium,
                    color = OnYellow,
                )
            }
        }
    }
}

@Preview(name = "PremiumInkBanner", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun PremiumInkBannerPreview() {
    TubeNimeTheme {
        PremiumInkBanner(
            title = "Go Shōnen Premium",
            subtitle = "Parallel downloads · no ads · 4K",
            Modifier.padding(16.dp),
        )
    }
}
