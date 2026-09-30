package com.tubenime.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tubenime.app.ui.components.InkButton
import com.tubenime.app.ui.components.MangaCard
import com.tubenime.app.ui.components.NavAnim
import com.tubenime.app.ui.screens.home.HomeScreen
import com.tubenime.app.ui.screens.home.components.AnimeChannels
import com.tubenime.app.ui.screens.inspect.FormatOption
import com.tubenime.app.ui.screens.inspect.InspectScreen
import com.tubenime.app.ui.screens.inspect.InspectUiModel
import com.tubenime.app.ui.theme.Dimens
import com.tubenime.app.ui.theme.TextMuted
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Home utama TubeNime — UI Compose "Shōnen Jump Ink" (menggantikan UI XML lama).
 *
 * Alur: paste URL → inspect via [InspectEngine] → [InspectScreen] (pilih format)
 * → download dengan progress → complete/error. Semua state alur di-hoist ke
 * activity ([MutableState]); semua screen Compose tetap stateless.
 *
 * Juga menerima URL dari: share sheet OS (ACTION_SEND) dan BrowserActivity
 * ([EXTRA_BROWSER_URL], dipakai tombol download dalam browser).
 */
class ComposeHomeActivity : ComponentActivity() {

    private lateinit var engine: InspectEngine

    // ── State alur inspect/download (hoisted) ─────────────────
    private val urlState = mutableStateOf("")
    private val phase = mutableStateOf(PHASE_HOME) // home | inspecting | result | downloading | complete | error
    private val statusText = mutableStateOf("")
    private val inspectModel = mutableStateOf<InspectUiModel?>(null)
    private val selectedType = mutableStateOf("VIDEO") // InspectScreen memakai label uppercase
    private val selectedFormatIndex = mutableStateOf(0)
    private val downloadPercent = mutableStateOf(0)
    private val errorMessage = mutableStateOf<String?>(null)
    private val completeFile = mutableStateOf("")

    private var currentInfo: PlatformInfo? = null
    private var visibleChoices: List<FormatChoice> = emptyList()
    private var currentUrl: String = ""
    private var pendingType: String? = null // dari BrowserActivity ("video"/"audio")

    override fun attachBaseContext(newBase: Context) {

        super.attachBaseContext(newBase)

        ThemeEngine.applyTheme(this)

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = InspectEngine(this)
        AdsManager.init(this) // idempoten: init bila Home di-launch langsung (share/adb)
        com.tubenime.app.ui.theme.initDarkModeOverride(this) // fallback bila Home di-launch langsung (share/adb)
        setContent {
            TubeNimeTheme { TubeNimeApp() }
        }
        handleIncomingUrl(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingUrl(intent)
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }

    // ── Incoming URL (share sheet / BrowserActivity) ──────────

    private fun handleIncomingUrl(intent: Intent?) {
        val shared = intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.takeIf { isUrl(it) }
            ?: return
        pendingType = intent.getStringExtra("download_type")
        urlState.value = shared
        startInspect(shared)
    }

    private fun isUrl(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be") ||
            lower.contains("tiktok.com") || lower.contains("vm.tiktok.com") ||
            lower.contains("facebook.com") || lower.contains("fb.watch") ||
            lower.contains("instagram.com") || lower.contains("threads.net") ||
            lower.contains("x.com") || lower.contains("twitter.com") ||
            lower.contains("bilibili.com")
    }

    // ── Alur inspect ──────────────────────────────────────────

    private fun startInspect(url: String) {
        currentUrl = url
        phase.value = PHASE_INSPECTING
        statusText.value = "Inspecting link..."
        engine.inspect(
            url,
            onStatus = { statusText.value = it },
            onDone = { info ->
                currentInfo = info
                selectedType.value = pendingType?.uppercase()
                    ?: if (info.formats.any { it.type == "video" }) "VIDEO" else "AUDIO"
                pendingType = null
                rebuildModel()
                selectedFormatIndex.value = 0
                phase.value = PHASE_RESULT
            },
            onError = { err ->
                errorMessage.value = engine.friendlyError(err)
                phase.value = PHASE_ERROR
            },
        )
    }

    /** Bangun ulang model UI dari [currentInfo] sesuai tipe terpilih (VIDEO/AUDIO). */
    private fun rebuildModel() {
        val info = currentInfo ?: return
        val typeLower = selectedType.value.lowercase()
        val tier = TierRules.filterFormats(PremiumManager.isPremium(), info.formats)
        visibleChoices = tier.filter { it.type == typeLower }
        inspectModel.value = InspectUiModel(
            platform = info.platform.displayName,
            title = info.title ?: "Unknown title",
            duration = formatDuration(info.duration),
            resolution = visibleChoices.firstOrNull()?.quality ?: "",
            formats = visibleChoices.mapIndexed { i, f ->
                FormatOption(
                    quality = f.quality ?: f.label,
                    detail = "${f.ext.uppercase()} • ${f.type.replaceFirstChar { it.uppercase() }}",
                    size = f.sizeBytes?.let { formatFileSize(it) } ?: "",
                    tag = when {
                        i == 0 -> "BEST"
                        i == visibleChoices.lastIndex -> "DATA"
                        else -> "MOBILE"
                    },
                )
            },
        )
    }

    // ── Alur download ─────────────────────────────────────────

    private fun startDownload() {
        val choice = visibleChoices.getOrNull(selectedFormatIndex.value) ?: return
        phase.value = PHASE_DOWNLOADING
        downloadPercent.value = 0
        statusText.value = "Preparing download..."
        engine.download(
            currentUrl,
            selectedType.value.lowercase(),
            choice,
            title = currentInfo?.title ?: "Video",
            onProgress = { percent, indeterminate, status ->
                if (!indeterminate) downloadPercent.value = percent
                statusText.value = status
            },
            onComplete = { _, filename ->
                completeFile.value = filename
                phase.value = PHASE_COMPLETE
                // Momen natural untuk interstitial (user gratis, max 1x/90 dtk).
                AdsManager.showInterstitialIfReady(this)
            },
            onError = { err ->
                errorMessage.value = engine.friendlyError(err)
                phase.value = PHASE_ERROR
            },
        )
    }

    private fun backToHome(clearUrl: Boolean = false) {
        if (clearUrl) urlState.value = ""
        phase.value = PHASE_HOME
    }

    // ── Root Compose: satu switch fase ────────────────────────

    @Composable
    private fun TubeNimeApp() {
        when (phase.value) {
            PHASE_INSPECTING -> StatusScreen(
                title = "Inspecting",
                status = statusText.value,
                showSpinner = true,
                onBack = { backToHome() },
            )
            PHASE_RESULT -> inspectModel.value?.let { model ->
                InspectScreen(
                info = model,
                selectedType = selectedType.value,
                onTypeChange = {
                    selectedType.value = it
                    rebuildModel()
                    selectedFormatIndex.value = 0
                },
                selectedFormatIndex = selectedFormatIndex.value,
                onFormatSelect = { selectedFormatIndex.value = it },
                onBack = { backToHome() },
                onDownloadClick = { startDownload() },
                onShareClick = {
                    if (currentUrl.isNotEmpty()) {
                        try {
                            startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    },
                                    "Share URL",
                                )
                            )
                        } catch (_: Exception) {}
                    }
                },
                )
            }
            PHASE_DOWNLOADING -> StatusScreen(
                title = "Downloading",
                status = statusText.value,
                showSpinner = false,
                percent = downloadPercent.value,
                onBack = null, // unduhan berjalan — biarkan selesai
            )
            PHASE_COMPLETE -> StatusScreen(
                title = "Download complete!",
                status = "📁 ${completeFile.value}\nSaved to: Downloads/TubeNime/",
                showSpinner = false,
                backLabel = "New Download",
                onBack = { backToHome(clearUrl = true) },
            )
            PHASE_ERROR -> StatusScreen(
                title = "Something went wrong",
                status = errorMessage.value ?: "Unknown error",
                showSpinner = false,
                backLabel = "Try Again",
                onBack = { backToHome() },
            )
            else -> HomeScreen(
                url = urlState.value,
                onUrlChange = { urlState.value = it },
                onInspectClick = {
                    val url = urlState.value.trim()
                    if (url.isEmpty()) {
                        Toast.makeText(this, "Paste a video URL first", Toast.LENGTH_SHORT).show()
                    } else {
                        startInspect(url)
                    }
                },
                onPasteClick = {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    if (text.isNotBlank()) urlState.value = text.trim()
                },
                onPlatformClick = ::openPlatform,
                onShortcutClick = ::openShortcut,
                onNavSelected = ::openNav,
            )
        }
    }

    // ── Layar status sederhana (inspecting / downloading / dst) ──

    @Composable
    private fun StatusScreen(
        title: String,
        status: String,
        showSpinner: Boolean,
        percent: Int = 0,
        backLabel: String = "Back",
        onBack: (() -> Unit)?,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(Dimens.SpaceXL),
            contentAlignment = Alignment.Center,
        ) {
            MangaCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(Dimens.SpaceXL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showSpinner) {
                        CircularProgressIndicator(Modifier.padding(top = Dimens.SpaceL))
                    } else if (percent in 1..99) {
                        // Progress bar bergaya manga: track paper-deep + fill merah
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Dimens.SpaceL)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(percent / 100f)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = Dimens.SpaceS),
                        )
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(top = Dimens.SpaceM),
                    )
                    onBack?.let { action ->
                        InkButton(
                            text = backLabel,
                            onClick = action,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Dimens.SpaceXL),
                        )
                    }
                }
            }
        }
    }

    // ── Navigasi (platform tile / shortcut / bottom nav) ──────

    private fun openPlatform(name: String) {
        // Tiap tile membuka feed trending anime platform itu.
        val target = AnimeChannels.platformTrendingUrl(name) ?: return
        openInBrowser(target)
    }

    private fun openShortcut(title: String) {
        // Strip acak / Today / VIEW ALL ("Trending Now" lama) → selesaikan URL-nya.
        openInBrowser(
            AnimeChannels.resolveShortcutUrl(title) ?: AnimeChannels.ANIME_TRENDING_URL,
        )
    }

    private fun openNav(index: Int) {
        // Impact Cut maju dari Home (tab 0) ke tab kanan.
        when (index) {
            1 -> NavAnim.go(this, Intent(this, ComposeBrowserActivity::class.java), NavAnim.TAB_HOME, NavAnim.TAB_BROWSER)
            2 -> NavAnim.go(this, Intent(this, ComposeDownloadsActivity::class.java), NavAnim.TAB_HOME, NavAnim.TAB_DOWNLOADS)
            3 -> NavAnim.go(this, Intent(this, ComposeSettingsActivity::class.java), NavAnim.TAB_HOME, NavAnim.TAB_SETTINGS)
            // 0 = Home (sudah di sini)
        }
    }

    private fun openInBrowser(url: String) {
        NavAnim.go(
            this,
            Intent(this, ComposeBrowserActivity::class.java).putExtra(ComposeBrowserActivity.EXTRA_URL, url),
            NavAnim.TAB_HOME,
            NavAnim.TAB_BROWSER,
        )
    }

    // ── Utilities (dipindah dari MainActivity) ────────────────

    private fun formatDuration(seconds: Double?): String {
        if (seconds == null || seconds <= 0) return ""
        val totalSeconds = seconds.toInt()
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        return "%d:%02d".format(minutes, secs)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    companion object {
        const val EXTRA_BROWSER_URL = "extra_browser_url"

        private const val PHASE_HOME = "home"
        private const val PHASE_INSPECTING = "inspecting"
        private const val PHASE_RESULT = "result"
        private const val PHASE_DOWNLOADING = "downloading"
        private const val PHASE_COMPLETE = "complete"
        private const val PHASE_ERROR = "error"
    }
}
