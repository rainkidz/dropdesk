package com.tubenime.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * "Halftone dot baseline fade" — Figma node 1:167 & 1:202: GRADIENT_RADIAL
 * ink dengan stop 0.15→0.16 (band tajam) yang TERUKUR di render sebagai elips
 * lembut kecil di tengah node (bukan garis): title 212×12 → elips rx≈23 ry≈1.4
 * alpha 0.37; CTA 350×16 → rx≈13 ry≈1.75 alpha 0.21. Posisi pusat & radius
 * diberikan dalam fraksi ukuran node (hasil pixel-audit render Figma 2x).
 */
@Composable
fun HalftoneBlob(
    modifier: Modifier = Modifier,
    centerXFraction: Float = 0.573f,
    centerYFraction: Float = 0.5f,
    radiusXFraction: Float = 0.108f,
    radiusYFraction: Float = 0.117f,
    alpha: Float = 0.37f,
    color: Color = Color.Black,
) {
    Box(
        modifier
            .drawBehind {
                val cx = size.width * centerXFraction
                val cy = size.height * centerYFraction
                val rx = size.width * radiusXFraction
                val ry = size.height * radiusYFraction
                // radial gradient lingkaran (radius rx) di-scale vertikal → elips
                withTransform({ scale(1f, ry / rx, pivot = Offset(cx, cy)) }) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to color.copy(alpha = alpha),
                                0.9f to color.copy(alpha = alpha),
                                1f to color.copy(alpha = 0f),
                            ),
                            center = Offset(cx, cy),
                            radius = rx,
                        ),
                        topLeft = Offset(cx - rx, cy - rx),
                        size = androidx.compose.ui.geometry.Size(rx * 2f, rx * 2f),
                    )
                }
            },
    )
}

@Preview(name = "HalftoneBlob", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun HalftoneBlobPreview() {
    TubeNimeTheme {
        HalftoneBlob(
            Modifier
                .size(width = 212.dp, height = 12.dp),
        )
    }
}
