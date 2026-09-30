package com.tubenime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Tab filter gaya sticker (Figma Downloads 1:501 "Subheader - Filter Tabs"):
 * tab aktif = fill ActiveYellow + border tinta 2dp, nonaktif = putih outline.
 */
@Composable
fun FilterTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val ink = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .background(if (selected) ActiveYellow else Color.White, shape)
            .border(if (selected) Dimens.BorderChip else 1.dp, ink, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpaceM, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Desain 1:501: tab aktif (All) punya dot merah di kiri label.
            if (selected) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(8.dp)
                        .background(ActionRed, CircleShape),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) OnYellow else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Pill counter storage (Figma 1:501 "Storage Counter Pill"): fill paper-deep
 * + border tinta tipis + teks Rubik 12.
 */
@Composable
fun StoragePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .width(72.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, ink.copy(alpha = 0.6f), shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Baris filter lengkap: tabs kiri + storage pill kanan. */
@Composable
fun FilterTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    storageText: String,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { i, tab ->
            FilterTab(tab, i == selectedIndex, Modifier.padding(end = Dimens.SpaceS)) {
                onTabSelected(i)
            }
        }
        Box(Modifier.weight(1f))
        StoragePill(storageText)
    }
}

@Preview(name = "FilterTabRow", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun FilterTabRowPreview() {
    TubeNimeTheme {
        FilterTabRow(
            tabs = listOf("All", "Videos", "Audio"),
            selectedIndex = 0,
            storageText = "128 MB",
            Modifier.padding(16.dp),
        )
    }
}
