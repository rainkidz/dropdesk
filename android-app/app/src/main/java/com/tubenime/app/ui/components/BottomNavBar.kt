package com.tubenime.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.R
import com.tubenime.app.ui.theme.ActiveYellow
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.InkSoft
import com.tubenime.app.ui.theme.OnYellow
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.inkShadowColor

data class BottomNavItem(val label: String, val iconRes: Int)

/** Tab bawaan — glyph verbatim dari Figma (node "Button - Tab 1..4", Home 1:160). */
fun defaultNavItems(): List<BottomNavItem> = listOf(
    BottomNavItem("Home", R.drawable.figma_nav_home),
    BottomNavItem("Browser", R.drawable.figma_nav_browser),
    BottomNavItem("Downloads", R.drawable.figma_nav_downloads),
    BottomNavItem("Settings", R.drawable.figma_nav_settings),
)

private val ActivePillShape = RoundedCornerShape(Dimens.RadiusInner) // Figma r8

/**
 * Bottom nav presisi Figma node 1:328 (diverifikasi JSON + node_1_160.png):
 * bar paper + border atas ink-soft + drop shadow ke atas (0,−4). Tab aktif =
 * pill kuning #FDC73E r8 DENGAN border ink 2dp + hard shadow 2dp (node 1:329),
 * label #705400 Rubik w700 + garis bawah stabilo 20×4 (node 1:334/1:335);
 * tab lain = glyph ink redup. Ikon = vector drawable hasil konversi SVG Figma.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onItemSelected: (Int) -> Unit = {},
) {
    val barShadow = inkShadowColor()
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Drop shadow ke atas (Figma 1:328: offset 0,−4) — di belakang bar
                drawRect(
                    color = barShadow,
                    topLeft = Offset(0f, -4.dp.toPx()),
                    size = Size(size.width, 4.dp.toPx()),
                )
            }
            .background(MaterialTheme.colorScheme.background)
            .border(2.dp, InkSoft)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            // Impact Cut: tab yang baru aktif goyang −2°→0 + burst halftone.
            val tilt = remember { Animatable(0f) }
            LaunchedEffect(selected) {
                if (selected) {
                    tilt.snapTo(-2f)
                    tilt.animateTo(0f, tween(220))
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = { onItemSelected(index) }),
            ) {
                // Burst halftone di belakang pill aktif (umpan Impact Cut).
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        HalftoneFade(
                            Modifier
                                .size(width = 64.dp, height = 14.dp)
                                .align(Alignment.BottomCenter),
                            maxAlpha = 0.35f,
                        )
                    }
                    // Pill kuning di belakang ikon+label tab aktif (Figma 1:329:
                    // r8, border ink 2dp, hard shadow 2,2)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .rotate(tilt.value)
                            .inkActivePill(selected)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Image(
                            painterResource(item.iconRes),
                            contentDescription = item.label,
                            modifier = Modifier
                                .size(20.dp)
                                .alpha(if (selected) 1f else 0.45f),
                        )
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) OnYellow else InkSoft.copy(alpha = 0.45f),
                        )
                        if (selected) {
                            // Garis bawah stabilo kuning 20×4 (node 1:334/1:335)
                            Box(
                                Modifier
                                    .padding(top = 2.dp)
                                    .width(20.dp)
                                    .height(4.dp)
                                    .background(ActiveYellow, RoundedCornerShape(50)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pill tab aktif: kuning + border ink + hard shadow; transparan bila tak aktif. */
@Composable
private fun Modifier.inkActivePill(selected: Boolean): Modifier =
    if (selected) {
        val shadow = inkShadowColor()
        this
            .drawBehind {
                drawRoundRect(
                    color = shadow,
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }
            .background(ActiveYellow, ActivePillShape)
            .border(2.dp, InkSoft, ActivePillShape)
    } else {
        this
    }

@Preview(name = "BottomNavBar", showBackground = true, backgroundColor = 0xFFFAF3E7)
@Composable
private fun BottomNavBarPreview() {
    TubeNimeTheme {
        BottomNavBar(defaultNavItems(), selectedIndex = 0)
    }
}
