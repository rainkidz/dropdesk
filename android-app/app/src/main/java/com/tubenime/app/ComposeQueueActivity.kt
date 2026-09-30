package com.tubenime.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.tubenime.app.ui.components.NavAnim
import com.tubenime.app.ui.screens.queue.QueueItem
import com.tubenime.app.ui.screens.queue.QueueScreen
import com.tubenime.app.ui.theme.TubeNimeTheme

/**
 * Host Compose layar Download Queue (menggantikan DownloadsQueueActivity XML).
 * Logic diport utuh: listener [DownloadQueue] singleton + startProcessing,
 * pause/resume/remove per item, buka file selesai, dan aksi massal
 * Pause All / Clear Failed. UI 100% Compose.
 */
class ComposeQueueActivity : ComponentActivity() {

    private lateinit var queue: DownloadQueue
    private val items = mutableStateOf<List<QueueItem>>(emptyList())
    private val searchQuery = mutableStateOf("")
    private val searchActive = mutableStateOf(false)

    private val listener = object : DownloadQueue.QueueListener {
        override fun onItemAdded(item: DownloadQueue.QueueItem) = refresh()
        override fun onItemUpdated(item: DownloadQueue.QueueItem) = refresh()
        override fun onItemRemoved(item: DownloadQueue.QueueItem) = refresh()
        override fun onQueueComplete() = refresh()
        override fun onItemStarted(item: DownloadQueue.QueueItem) = refresh()
        override fun onItemPaused(item: DownloadQueue.QueueItem) = refresh()
        override fun onItemResumed(item: DownloadQueue.QueueItem) = refresh()
    }

    override fun attachBaseContext(newBase: Context) {

        super.attachBaseContext(newBase)

        ThemeEngine.applyTheme(this)

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queue = DownloadQueue.getInstance(this)
        setContent {
            TubeNimeTheme {
                QueueScreen(
                    items = filteredItems(),
                    onBack = { finish() },
                    searchActive = searchActive.value,
                    searchQuery = searchQuery.value,
                    onSearchToggle = {
                        searchActive.value = !searchActive.value
                        if (!searchActive.value) searchQuery.value = ""
                    },
                    onSearchQueryChange = { searchQuery.value = it },
                    onPauseAll = ::pauseAll,
                    onClearFailed = ::clearFailed,
                    onItemPauseResume = ::pauseResume,
                    onItemRemove = { queue.removeItem(it.id) },
                    onItemClick = ::openIfCompleted,
                    onUpgradeClick = { NavAnim.go(this, Intent(this, ComposePremiumActivity::class.java), NavAnim.TAB_QUEUE, NavAnim.TAB_NONE) },
                    selectedNavIndex = 2, // Figma 1:5: tab Downloads aktif
                    onNavSelected = ::openNav,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        queue.addListener(listener)
        queue.startProcessing()
        refresh()
    }

    override fun onPause() {
        queue.removeListener(listener)
        super.onPause()
    }

    // ── Navigasi bottom nav (Figma 1:5: tab Downloads aktif) ──

    private fun openNav(index: Int) {
        when (index) {
            0 -> NavAnim.go(
                this,
                Intent(this, ComposeHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                NavAnim.TAB_QUEUE,
                NavAnim.TAB_HOME,
            )
            1 -> NavAnim.go(this, Intent(this, ComposeBrowserActivity::class.java), NavAnim.TAB_QUEUE, NavAnim.TAB_BROWSER)
            // 2 = Downloads: kembali ke daftar download
            2 -> NavAnim.go(
                this,
                Intent(this, ComposeDownloadsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                NavAnim.TAB_QUEUE,
                NavAnim.TAB_DOWNLOADS,
            )
            3 -> NavAnim.go(this, Intent(this, ComposeSettingsActivity::class.java), NavAnim.TAB_QUEUE, NavAnim.TAB_SETTINGS)
        }
    }

    private fun filteredItems(): List<QueueItem> {
        val q = searchQuery.value.trim()
        return if (q.isEmpty()) items.value else items.value.filter { it.fileName.contains(q, ignoreCase = true) }
    }

    // ── Mapping QueueItem domain → UI ───────────────────────────

    private fun refresh() {
        items.value = queue.getQueue().map { q ->
            QueueItem(
                id = q.id,
                fileName = q.title,
                meta = listOfNotNull(
                    q.type.replaceFirstChar { it.uppercase() },
                    q.eta.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
                stateLabel = q.status,
                progress = if (q.status == "pending") null else q.progress,
                speed = q.speed.takeIf { it.isNotBlank() },
            )
        }
    }

    // ── Aksi (dipindah dari DownloadsQueueActivity + item_queue) ──

    private fun pauseResume(item: QueueItem) {
        when (item.stateLabel) {
            "downloading" -> queue.pauseItem(item.id)
            "paused" -> queue.resumeItem(item.id)
        }
    }

    private fun pauseAll() {
        val active = queue.getQueue().filter { it.status == "downloading" || it.status == "pending" }
        if (active.isEmpty()) {
            Toast.makeText(this, "Nothing to pause", Toast.LENGTH_SHORT).show()
        }
        active.forEach { queue.pauseItem(it.id) }
    }

    private fun clearFailed() {
        val failed = queue.getQueue().filter { it.status == "failed" }
        if (failed.isEmpty()) {
            Toast.makeText(this, "No failed items", Toast.LENGTH_SHORT).show()
        }
        failed.forEach { queue.removeItem(it.id) }
    }

    private fun openIfCompleted(item: QueueItem) {
        val domain = queue.getQueue().firstOrNull { it.id == item.id } ?: return
        if (domain.status == "completed" && domain.filePath != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(domain.filePath), mimeFor(domain.filename ?: ""))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mimeFor(filename: String): String = when {
        filename.endsWith(".mp4") -> "video/mp4"
        filename.endsWith(".webm") -> "video/webm"
        filename.endsWith(".mp3") -> "audio/mpeg"
        filename.endsWith(".m4a") -> "audio/mp4"
        else -> "application/octet-stream"
    }
}
