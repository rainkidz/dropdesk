package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.theme.BubbleCream
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Task-count comic bubble (Figma Queue 1:5 "Task Count Comic Bubble Tag"):
 * bubble cream + outline tinta + ekor bawah + angka besar Baloo + label Rubik.
 * Memakai [speechBubbleShape] dengan ekor DOWN.
 */
@Composable
fun ComicCountBubble(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.outline
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .background(BubbleCream, speechBubbleShape(BubbleTail.DOWN))
                .border(Dimens.BorderBubble, ink, speechBubbleShape(BubbleTail.DOWN))
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "ComicCountBubble", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun ComicCountBubblePreview() {
    TubeNimeTheme {
        ComicCountBubble(12, "in queue", Modifier.padding(start = 32.dp, top = 16.dp))
    }
}
