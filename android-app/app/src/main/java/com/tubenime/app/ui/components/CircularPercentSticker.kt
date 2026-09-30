package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Stiker sirkular 100%/persen (Figma Downloads 1:501 "Download Complete 100%
 * Circular Sticker"): lingkaran kuning + border tinta 3dp + teks Baloo.
 */
@Composable
fun CircularPercentSticker(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(52.dp)
            .background(ActiveYellow, CircleShape)
            .border(3.dp, Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = OnYellow,
            modifier = Modifier.padding(2.dp),
        )
    }
}

@Preview(name = "CircularPercentSticker", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun CircularPercentStickerPreview() {
    TubeNimeTheme {
        CircularPercentSticker(100, Modifier.padding(20.dp))
    }
}
