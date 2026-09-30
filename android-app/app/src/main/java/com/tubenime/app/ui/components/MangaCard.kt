package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/**
 * Kartu manga dasar: fill putih + border tinta 4dp + hard offset shadow
 * (down-right, solid, tanpa blur; default 2dp, panel Settings 3dp) +
 * halftone fade di kaki panel. Semua kartu/panel gaya "Shōnen Jump Ink"
 * dibangun dari sini.
 */
@Composable
fun MangaCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.RadiusPanel),
    fill: Color = MaterialTheme.colorScheme.surface,
    showHalftone: Boolean = true,
    shadowOffset: Dp = Dimens.HardShadowOffset,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val ink = MaterialTheme.colorScheme.outline
    Box(modifier) {
        // Hard offset shadow — bukan elevation/blur
        Box(
            Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(inkShadowColor(), shape)
        )
        // Panel: fill + border tinta — konten inilah yang menentukan ukuran kartu
        // (matchParentSize di sini akan membuat kartu 0×0)
        Box(
            Modifier
                .clip(shape)
                .background(fill)
                .border(Dimens.BorderInk, ink, shape)
        ) {
            content()
            if (showHalftone) {
                HalftoneFade(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Dimens.HalftoneHeight)
                )
            }
        }
    }
}

@Preview(name = "MangaCard", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun MangaCardPreview() {
    TubeNimeTheme {
        MangaCard(Modifier.padding(16.dp)) {
            Text(
                "Panel content",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}
