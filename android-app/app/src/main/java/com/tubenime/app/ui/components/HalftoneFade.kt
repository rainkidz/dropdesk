package com.tubenime.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Screentone halftone: grid titik staggered dengan alpha memudar dari atas ke
 * bawah — ditempel di kaki panel (lihat MangaCard) atau sebagai aksen.
 * Pola digambar penuh via Canvas, tanpa asset.
 */
@Composable
fun HalftoneFade(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    maxAlpha: Float = 0.30f,
) {
    Canvas(modifier) {
        val step = 7.dp.toPx()
        val radius = 1.7.dp.toPx()
        var row = 0
        var y = step / 2f
        while (y < size.height) {
            val alpha = maxAlpha * (1f - y / size.height)
            var x = if (row % 2 == 0) step / 2f else step
            while (x < size.width) {
                drawCircle(color = color, radius = radius, center = Offset(x, y), alpha = alpha)
                x += step
            }
            y += step
            row++
        }
    }
}

@Preview(name = "HalftoneFade", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun HalftoneFadePreview() {
    TubeNimeTheme {
        HalftoneFade(Modifier, maxAlpha = 0.5f)
    }
}
