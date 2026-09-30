package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/**
 * Tiket kualitas gaya manga (Figma Inspect 1:351 "Radiogroup - Video Quality
 * Options"): kartu putih + border tinta + hard shadow + radio circle + teks
 * kualitas Baloo + detail Rubik + badge tag opsional (BEST/DATA/MOBILE).
 * Stateless — seleksi dikontrol parent.
 */
@Composable
fun QualityTicket(
    quality: String,
    detail: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tag: String? = null,
    onClick: () -> Unit = {},
) {
    val ink = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(Dimens.RadiusTile)
    val shadowColor = inkShadowColor()

    Box(modifier) {
        // Hard offset shadow 2dp
        Box(
            Modifier
                .matchParentSize()
                .offset(x = Dimens.HardShadowOffset, y = Dimens.HardShadowOffset)
                .background(shadowColor, shape)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clipThenBorder(shape, ink, selected)
                .background(if (selected) MaterialTheme.colorScheme.surface else Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
        ) {
            // Radio circle
            Box(
                Modifier
                    .size(20.dp)
                    .border(3.dp, ink, CircleShape)
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            Spacer(Modifier.width(Dimens.SpaceM))
            Column(Modifier.weight(1f)) {
                Text(
                    quality,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tag != null) {
                StickerBadge(tag, rotation = -2f)
            }
        }
    }
}

/** Border lebih tebal saat terpilih (2dp default → 4dp tinta). */
private fun Modifier.clipThenBorder(shape: RoundedCornerShape, ink: Color, selected: Boolean): Modifier =
    this.then(
        Modifier.border(if (selected) Dimens.BorderInk else 2.dp, ink, shape)
    )

@Preview(name = "QualityTicket", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun QualityTicketPreview() {
    TubeNimeTheme {
        Column(Modifier.padding(16.dp)) {
            QualityTicket("1080p FHD 60fps", "MP4 • VIDEO", selected = true, tag = "BEST")
            Spacer(Modifier.padding(4.dp))
            QualityTicket("720p HD", "MP4 • VIDEO")
            Spacer(Modifier.padding(4.dp))
            QualityTicket("480p SD", "MP4 • VIDEO", tag = "DATA")
        }
    }
}
