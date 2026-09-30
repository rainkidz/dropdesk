package com.tubenime.app.ui.screens.cookie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.components.InkButton
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.SpeechBubble
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Brief-derived (Cookie Login prompt), versi layar penuh (host butuh WebView
 * utk capture cookies): panel manga + stiker platform kuning + status line +
 * slot [webViewSlot] (host memasukkan WebView) + progress loading +
 * tombol "Save Cookies" & "Skip".
 * Semua state di-hoist ke host (status, progress, save visibility).
 */
@Composable
fun CookieLoginScreen(
    platform: String,
    modifier: Modifier = Modifier,
    statusText: String = "Login to platform...",
    loadingProgress: Float? = null, // null = sembunyikan progress bar
    saveEnabled: Boolean = false,
    onBack: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onSkipClick: () -> Unit = {},
    webViewSlot: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Header: back + stiker platform + judul ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(4.dp)
                    .size(22.dp),
            )
            StickerBadge(
                platform,
                Modifier.padding(start = Dimens.SpaceS),
                container = HighlightYellow,
                contentColor = Ink,
                rotation = -2f,
            )
            Text(
                "Login for private content",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = Dimens.SpaceM),
            )
        }

        // ── Status + progress ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.weight(1f),
            )
        }
        loadingProgress?.let { p ->
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
                color = Ink,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        // ── Slot WebView (host) di dalam panel manga ──
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM)) {
            MangaCard(Modifier.fillMaxSize(), showHalftone = false) {
                Box(Modifier.fillMaxSize()) {
                    webViewSlot()
                }
            }
        }

        // ── Speech bubble + aksi Save/Skip ──
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL)) {
            SpeechBubble(
                "Log in above, then tap SAVE COOKIES!",
                Modifier.align(Alignment.CenterHorizontally),
            )
            InkButton(
                text = if (saveEnabled) "Save Cookies" else "Login First…",
                onClick = onSaveClick,
                enabled = saveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceM),
            )
            Text(
                "Skip — download public content only",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted,
                modifier = Modifier
                    .padding(vertical = Dimens.SpaceM)
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onSkipClick),
            )
        }
    }
}

@Preview(name = "CookieLoginScreen", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 844)
@Composable
private fun CookieLoginScreenPreview() {
    TubeNimeTheme {
        CookieLoginScreen(
            platform = "Instagram",
            statusText = "Login successful! Tap Save to capture cookies.",
            loadingProgress = null,
            saveEnabled = true,
            webViewSlot = {
                Box(Modifier.background(Color(0x11000000))) {
                    Text("WebView slot", Modifier.align(Alignment.Center))
                }
            },
        )
    }
}
