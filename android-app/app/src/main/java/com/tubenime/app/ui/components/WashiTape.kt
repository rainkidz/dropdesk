package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.WashiMint
import com.tubenime.app.ui.theme.WashiYellow

/**
 * Strip washi tape (khusus kartu Downloads): rotasi ±15°, opacity 0.85,
 * label opsional kecil (mis. "★ TAPE_01" dari Figma). Pasang overlap di sudut
 * kartu dengan offset negatif.
 */
@Composable
fun WashiTape(
    modifier: Modifier = Modifier,
    color: Color = WashiYellow,
    label: String? = null,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .rotate(Dimens.WashiRotation)
            .alpha(0.85f)
            .background(color, shape)
            .border(1.dp, Ink.copy(alpha = 0.25f), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.75f),
            )
        }
    }
}

@Preview(name = "WashiTape", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun WashiTapePreview() {
    TubeNimeTheme {
        WashiTape(Modifier.padding(24.dp), WashiMint, "★ TAPE_02")
    }
}
