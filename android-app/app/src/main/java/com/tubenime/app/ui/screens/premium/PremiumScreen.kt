package com.tubenime.app.ui.screens.premium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.tubenime.app.ui.components.HalftoneFade
import com.tubenime.app.ui.components.InkButton
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.StickerBadge
import com.tubenime.app.ui.theme.ActionRed
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.HighlightYellow
import com.tubenime.app.ui.theme.Ink
import com.tubenime.app.ui.theme.Paper
import com.tubenime.app.ui.theme.TextOnPaper
import com.tubenime.app.ui.theme.TubeNimeTheme

data class PremiumBenefit(val title: String, val badge: String, val badgeContainer: Color, val badgeContent: Color)

fun sampleBenefits(): List<PremiumBenefit> = listOf(
    PremiumBenefit("Video+Audio merged up to 1080p+", "MERGE", ActionRed, Color.White),
    PremiumBenefit("Playlist & batch downloads", "BATCH", HighlightYellow, Ink),
    PremiumBenefit("Private content & 20-slot queue", "PRIVATE", Ink, Paper),
)

/**
 * Brief-derived (Premium prompt): poster shōnen full-bleed — diagonal split
 * tinta/kertas via Canvas + halftone → headline "GO PREMIUM" kuning miring −3°
 * → panel benefit tilt berselang → price card kuning (monthly/yearly) →
 * tombol Unlock merah + restore + debug toggle (debug builds) + caption.
 * Semua aksi via callback; [isPremium] mengubah panel atas jadi status aktif.
 */
@Composable
fun PremiumScreen(
    benefits: List<PremiumBenefit>,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    statusLabel: String = "Free plan — 720p max, ads shown",
    priceMonthly: String = "Rp 39.000 / bulan",
    priceYearly: String = "Rp 390.000 / tahun",
    debugVisible: Boolean = false,
    debugPremium: Boolean = false,
    // ── Lisensi sideload (QRIS/PayPal manual) ──
    licenseVisible: Boolean = false,
    licenseError: String? = null,
    payLocalTitle: String = "",
    payLocalBody: String = "",
    payIntlTitle: String = "",
    payIntlBody: String = "",
    sellerContact: String = "",
    // ── Bayar otomatis (Midtrans) ──
    autoPayVisible: Boolean = false,
    autoPayBusy: Boolean = false,
    autoPayStatus: String? = null,
    onBack: () -> Unit = {},
    onBuyMonthly: () -> Unit = {},
    onBuyYearly: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRedeem: (String) -> Unit = {},
    onPayAuto: () -> Unit = {},
    onDebugToggle: (Boolean) -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(Paper)) {
        // ── Diagonal ink/paper split + speed lines (Canvas, tanpa asset) ──
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * 0.34f)
                lineTo(0f, size.height * 0.48f)
                close()
            }
            drawPath(path, Ink)
            for (i in 1..6) {
                val y = size.height * 0.05f * i
                drawLine(
                    color = Paper.copy(alpha = 0.10f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y * 1.6f),
                    strokeWidth = 2f,
                )
            }
        }
        HalftoneFade(
            Modifier
                .fillMaxWidth()
                .height(Dimens.HalftoneHeight)
                .align(Alignment.TopCenter),
            color = Paper,
            maxAlpha = 0.25f,
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Header di atas poster ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Paper,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(4.dp)
                        .size(22.dp),
                )
                Text("TUBENIME", style = MaterialTheme.typography.titleMedium, color = Paper, modifier = Modifier.padding(start = Dimens.SpaceS))
            }

            Spacer(Modifier.height(24.dp))
            // ── Brief: "GO PREMIUM" kuning miring −3° ──
            Text(
                "GO PREMIUM",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, color = HighlightYellow),
                modifier = Modifier
                    .padding(horizontal = Dimens.SpaceL)
                    .rotate(-3f),
            )
            Text(
                if (isPremium) statusLabel else "Every episode, every song — no limits, no waiting.",
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
                modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            )

            Spacer(Modifier.height(24.dp))

            // ── Brief: panel benefit tilt berselang ──
            benefits.forEachIndexed { index, benefit ->
                MangaCard(
                    shape = MaterialTheme.shapes.medium,
                    showHalftone = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                        .rotate(if (index % 2 == 0) -1.5f else 1.5f),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Dimens.SpaceL),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StickerBadge(benefit.badge, container = benefit.badgeContainer, contentColor = benefit.badgeContent)
                        Text(
                            benefit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextOnPaper,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = Dimens.SpaceM),
                        )
                    }
                }
            }

            // ── Harga / status premium ──
            if (!isPremium) {
                MangaCard(
                    fill = HighlightYellow,
                    shape = MaterialTheme.shapes.medium,
                    showHalftone = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                ) {
                    Column(Modifier.fillMaxWidth().padding(Dimens.SpaceL)) {
                        Text(priceMonthly, style = MaterialTheme.typography.headlineMedium, color = Ink)
                        Text(priceYearly, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.8f))
                    }
                }
                InkButton(
                    text = "Unlock Premium — Monthly",
                    onClick = onBuyMonthly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceL),
                )
                InkButton(
                    text = "Yearly Plan",
                    onClick = onBuyYearly,
                    container = Ink,
                    contentColor = Paper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
                )
                Text(
                    "Restore purchases",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = Dimens.SpaceM)
                        .align(Alignment.CenterHorizontally)
                        .clip(MaterialTheme.shapes.small)
                        .clickableRestore(onRestore)
                        .padding(Dimens.SpaceS),
                )

                // ── Bayar otomatis (Midtrans: QRIS/e-wallet/VA/kartu) ──
                if (autoPayVisible) {
                    InkButton(
                        text = if (autoPayBusy) "Memproses…" else "Bayar Otomatis",
                        onClick = { if (!autoPayBusy) onPayAuto() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.SpaceL),
                    )
                    Text(
                        autoPayStatus ?: "QRIS • GoPay • DANA • VA bank • kartu",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextOnPaper.copy(alpha = 0.8f),
                        modifier = Modifier
                            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                            .align(Alignment.CenterHorizontally),
                    )
                }

                // ── Redeem kode lisensi (sideload: bayar QRIS/PayPal manual) ──
                if (licenseVisible) {
                    LicenseRedeemCard(
                        licenseError = licenseError,
                        payLocalTitle = payLocalTitle,
                        payLocalBody = payLocalBody,
                        payIntlTitle = payIntlTitle,
                        payIntlBody = payIntlBody,
                        sellerContact = sellerContact,
                        onRedeem = onRedeem,
                    )
                }
            } else {
                MangaCard(
                    fill = HighlightYellow,
                    shape = MaterialTheme.shapes.medium,
                    showHalftone = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                ) {
                    Text(
                        "✅ Premium active — $statusLabel",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        modifier = Modifier.padding(Dimens.SpaceL),
                    )
                }
            }

            // ── Debug toggle (debug builds saja — host mengatur visibilitas) ──
            if (debugVisible) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                        .clip(MaterialTheme.shapes.small)
                        .background(Ink.copy(alpha = 0.08f))
                        .padding(Dimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Debug: test premium", style = MaterialTheme.typography.bodyMedium, color = TextOnPaper, modifier = Modifier.weight(1f))
                    InkLeverSimple(debugPremium, onDebugToggle)
                }
            }

            Text(
                "Cancel anytime",
                style = MaterialTheme.typography.labelSmall,
                color = TextOnPaper.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(vertical = Dimens.SpaceM)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(Dimens.SpaceXL))
        }
    }
}

/** Kartu redeem kode lisensi + info bayar QRIS/PayPal (sideload). */
@Composable
private fun LicenseRedeemCard(
    licenseError: String?,
    payLocalTitle: String,
    payLocalBody: String,
    payIntlTitle: String,
    payIntlBody: String,
    sellerContact: String,
    onRedeem: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    MangaCard(
        shape = MaterialTheme.shapes.medium,
        showHalftone = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
    ) {
        Column(Modifier.fillMaxWidth().padding(Dimens.SpaceL)) {
            Text("PUNYA KODE AKTIVASI?", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                "Bayar dulu, terima kode dari penjual, tempel di bawah.",
                style = MaterialTheme.typography.bodySmall,
                color = TextOnPaper.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp, bottom = Dimens.SpaceS),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("TN1-XXXXXX-XXXX-XXXXXXXX") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (licenseError != null) {
                Text(
                    licenseError,
                    style = MaterialTheme.typography.bodySmall,
                    color = ActionRed,
                    modifier = Modifier.padding(top = Dimens.SpaceS),
                )
            }
            InkButton(
                text = "Aktifkan Premium",
                onClick = { onRedeem(code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceM),
            )
            if (payLocalTitle.isNotEmpty()) {
                Text(payLocalTitle, style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.padding(top = Dimens.SpaceM))
                Text(payLocalBody, style = MaterialTheme.typography.bodySmall, color = TextOnPaper.copy(alpha = 0.8f))
            }
            if (payIntlTitle.isNotEmpty()) {
                Text(payIntlTitle, style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.padding(top = Dimens.SpaceS))
                Text(payIntlBody, style = MaterialTheme.typography.bodySmall, color = TextOnPaper.copy(alpha = 0.8f))
            }
            if (sellerContact.isNotEmpty()) {
                Text(sellerContact, style = MaterialTheme.typography.bodySmall, color = TextOnPaper.copy(alpha = 0.8f), modifier = Modifier.padding(top = Dimens.SpaceS))
            }
        }
    }
}

/** Lever kecil untuk debug toggle (stateless, klik = toggle). */
@Composable
private fun InkLeverSimple(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (checked) Ink else Paper)
            .border(2.dp, Ink, MaterialTheme.shapes.extraLarge)
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(if (checked) HighlightYellow else Ink)
        )
    }
}

private fun Modifier.clickableRestore(onRestore: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onRestore))

@Preview(name = "PremiumScreen — free", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 844)
@Composable
private fun PremiumFreePreview() {
    TubeNimeTheme {
        PremiumScreen(benefits = sampleBenefits(), debugVisible = true)
    }
}

@Preview(name = "PremiumScreen — premium", showBackground = true, backgroundColor = 0xFFFAF3E7, widthDp = 390, heightDp = 844)
@Composable
private fun PremiumActivePreview() {
    TubeNimeTheme {
        PremiumScreen(benefits = sampleBenefits(), isPremium = true, statusLabel = "Monthly plan")
    }
}
