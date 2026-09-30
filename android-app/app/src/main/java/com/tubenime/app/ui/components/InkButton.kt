package com.tubenime.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tubenime.app.R
import com.tubenime.app.ui.theme.CtaRed
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.PureWhite
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

private val ButtonRadius = 12.dp // Figma r12 (CTA nodes)
private val ButtonShape = RoundedCornerShape(ButtonRadius)

/**
 * Tombol aksi gaya shōnen: background [container] (default CTA red #DB322F —
 * warna tombol Download/Inspect dari export) + teks Baloo 2 + border tinta 3dp
 * + hard offset shadow [shadowOffset] dp down-right, solid, TANPA blur.
 *
 * Shadow digambar via [drawBehind] pada tombol itu sendiri — ukurannya selalu
 * persis ukuran tombol, jadi aman untuk modifier apa pun ([Modifier.fillMaxWidth]
 * dsb.). Implementasi lama (Box wrapper + matchParentSize) "bocor": saat caller
 * mengirim fillMaxWidth, shadow membentang selebar parent sedangkan tombol hanya
 * wrap teks → muncul "papan hitam" di samping CTA hero Home.
 *
 * [height] mengunci tinggi tombol (Figma hero INSPECT LINK = 62dp); null = wrap
 * konten. [leadingIconRes] digambar di kiri teks (Figma node 1:193/1:194 —
 * lingkaran download putih 22dp). [enabled] = false membuat tombol terlihat
 * redup & tak bisa ditekan.
 */
@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = CtaRed,
    contentColor: Color = PureWhite,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    leadingIconRes: Int? = null,
    height: Dp? = null,
    shadowOffset: Dp = Dimens.HardShadowOffset,
    onClick: () -> Unit = {},
) {
    val shadowColor = inkShadowColor()
    val sizedModifier = if (height != null) modifier.height(height) else modifier
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = sizedModifier
            // Hard offset shadow — di belakang tombol, ukuran = ukuran tombol.
            // Digambar sebelum border/background Button, jadi terlihat hanya
            // strip bawah-kanan setebal [shadowOffset] (efek stiker komik).
            .drawBehind {
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(shadowOffset.toPx(), shadowOffset.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(ButtonRadius.toPx(), ButtonRadius.toPx()),
                )
            }
            .border(3.dp, Ink, ButtonShape),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIconRes?.let {
                Image(
                    painterResource(it),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = Dimens.SpaceS)
                        .size(22.dp), // Figma node 1:193/1:194 (22×22)
                )
            }
            Text(
                text,
                style = textStyle ?: MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Preview(name = "InkButton", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun InkButtonPreview() {
    TubeNimeTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // full-width: shadow harus ikut selebar tombol (regresi leak lama)
            InkButton("INSPECT LINK", Modifier.fillMaxWidth())
            InkButton(
                "INSPECT LINK",
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                leadingIconRes = R.drawable.figma_ic_download_hero,
                height = 62.dp,
                shadowOffset = 4.dp,
            )
        }
    }
}
