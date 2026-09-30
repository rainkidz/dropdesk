package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Headline layar miring (Figma Downloads 1:501 "Headline with -2deg tilt &
 * Halftone accent", Queue 1:5 "Header Title"). Tilt -2° dan aksen halftone
 * di belakang — elemen miring pertama dari maksimal 2 per layar.
 */
@Composable
fun TiltedHeadline(
    text: String,
    modifier: Modifier = Modifier,
    tilt: Float = Dimens.TiltHeadline,
    accent: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
) {
    Box(modifier.fillMaxWidth()) {
        // Aksen halftone dekoratif di belakang teks
        if (accent != Color.Unspecified) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp, top = 14.dp)
                    .height(14.dp)
                    .fillMaxWidth(0.55f)
                    .background(accent)
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .rotate(tilt)
                .padding(vertical = Dimens.SpaceS),
        )
    }
}

@Preview(name = "TiltedHeadline", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun TiltedHeadlinePreview() {
    TubeNimeTheme {
        TiltedHeadline("Downloads", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
