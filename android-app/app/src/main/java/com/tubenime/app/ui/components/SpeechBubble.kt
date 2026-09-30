package com.tubenime.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.ui.theme.Baloo2
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TextOnPaper
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

/** Posisi ekor balon ucapan. */
enum class BubbleTail { DOWN, UP }

/**
 * Bentuk balon ucapan: rounded rect + ekor segitiga kecil menyatu di outline
 * (border 3dp ikut menggambar ekor). Ekor berada DI DALAM bounds shape, jadi
 * komponen menambahkan padding di sisi ekor.
 */
fun speechBubbleShape(tail: BubbleTail): Shape = object : Shape {
    // Signature compose-ui 1.6 (BOM 2024.02.01): size langsung sebagai parameter
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        with(density) {
            val r = 12.dp.toPx() // Figma node 1:205: corner r12
            val tailW = 20.dp.toPx() // Figma 1:211: w=20
            val tailH = 13.dp.toPx() // Figma 1:211: h=13
            val w = size.width
            val h = size.height
            val path = Path()
            if (tail == BubbleTail.DOWN) {
                val bodyH = h - tailH
                val tailLeft = 8.dp.toPx()
                val tailRight = tailLeft + tailW
                path.moveTo(r, 0f)
                path.lineTo(w - r, 0f)
                path.quadraticBezierTo(w, 0f, w, r)
                path.lineTo(w, bodyH - r)
                path.quadraticBezierTo(w, bodyH, w - r, bodyH)
                path.lineTo(tailRight, bodyH)
                path.lineTo(tailRight, h)
                path.lineTo(tailLeft, h)
                path.lineTo(tailLeft, bodyH)
                path.quadraticBezierTo(0f, bodyH, 0f, bodyH - r)
                path.lineTo(0f, r)
                path.quadraticBezierTo(0f, 0f, r, 0f)
                path.close()
            } else {
                val bodyTop = tailH
                val tailLeft = 8.dp.toPx()
                val tailRight = tailLeft + tailW
                path.moveTo(tailLeft, 0f)
                path.lineTo(tailRight, 0f)
                path.lineTo(tailRight, bodyTop)
                path.lineTo(w - r, bodyTop)
                path.quadraticBezierTo(w, bodyTop, w, bodyTop + r)
                path.lineTo(w, h - r)
                path.quadraticBezierTo(w, h, w - r, h)
                path.lineTo(r, h)
                path.quadraticBezierTo(0f, h, 0f, h - r)
                path.lineTo(0f, bodyTop + r)
                path.quadraticBezierTo(0f, bodyTop, r, bodyTop)
                path.close()
            }
            return Outline.Generic(path)
        }
    }
}

/**
 * Tooltip gaya manga (node 1:205/1:206): bubble putih r12 + outline tinta 3dp
 * + hard drop shadow ink (3,3) + teks Baloo 12sp. [leadingIconRes] opsional:
 * glyph kecil di kiri teks (mis. bohlam merah node 1:207/1:208).
 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tail: BubbleTail = BubbleTail.DOWN,
    fill: Color = Color.White,
    contentColor: Color = TextOnPaper,
    leadingIconRes: Int? = null,
) {
    val ink = MaterialTheme.colorScheme.outline
    val shape = remember(tail) { speechBubbleShape(tail) }
    val tailPad = Dimens.HalftoneHeight // ruang untuk ekor di sisi keluar
    val shadowColor = inkShadowColor()
    Box(
        modifier
            .drawBehind {
                // Figma node 1:205: DROP_SHADOW ink (3,3) r0 — hard offset di belakang bubble
                val off = Offset(3.dp.toPx(), 3.dp.toPx())
                val path = Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawBehind))
                }
                withTransform({ translate(off.x, off.y) }) {
                    drawPath(path, shadowColor)
                }
            }
            .background(fill, shape)
            .border(Dimens.BorderBubble, ink, shape)
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = if (tail == BubbleTail.UP) 10.dp + tailPad else 10.dp,
                bottom = if (tail == BubbleTail.DOWN) 10.dp + tailPad else 10.dp,
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIconRes?.let {
                Image(
                    painterResource(it),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(width = 13.dp, height = 13.dp), // Figma node 1:207/1:208
                )
            }
            Text(
                text,
                style = TextStyle(
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = contentColor,
            )
        }
    }
}

@Preview(name = "SpeechBubble", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun SpeechBubblePreview() {
    TubeNimeTheme {
        SpeechBubble(
            "Tap a platform to browse!",
            Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp),
        )
    }
}
