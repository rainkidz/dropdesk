package com.tubenime.app

import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.tubenime.app.ui.components.NavAnim
import com.tubenime.app.ui.screens.downloads.DownloadItem
import com.tubenime.app.ui.screens.downloads.DownloadsScreen
import com.tubenime.app.ui.theme.TubeNimeTheme
import com.tubenime.app.ui.theme.WashiMint
import com.tubenime.app.ui.theme.WashiPink
import com.tubenime.app.ui.theme.WashiYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Host Compose layar Downloads (menggantikan DownloadsActivity XML).
 * Logic diport utuh: query MediaStore (A10+) / File API (A9-) ke folder
 * Downloads/TubeNime, intent play & share, plus tambahan delete.
 * UI 100% Compose "Shōnen Jump Ink" — state di-hoist, screen stateless.
 */
class ComposeDownloadsActivity : ComponentActivity() {

    private val files = androidx.compose.runtime.mutableStateOf<List<DownloadItem>>(emptyList())
    private val selectedFilter = androidx.compose.runtime.mutableStateOf(0)
    private val searchQuery = androidx.compose.runtime.mutableStateOf("")
    private val searchActive = androidx.compose.runtime.mutableStateOf(false)
    private val queueCount = androidx.compose.runtime.mutableStateOf(0)
    private val tipDismissed = androidx.compose.runtime.mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {

        super.attachBaseContext(newBase)

        ThemeEngine.applyTheme(this)

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TubeNimeTheme {
                DownloadsScreen(
                    items = filteredItems(),
                    filterLabels = filterLabelsWithCounts(),
                    selectedFilter = selectedFilter.value,
                    onFilterSelected = { selectedFilter.value = it },
                    storageText = formatFreeSize(),
                    onBack = { finish() },
                    onSearchClick = { searchActive.value = !searchActive.value; if (!searchActive.value) searchQuery.value = "" },
                    searchQuery = searchQuery.value,
                    onSearchQueryChange = { searchQuery.value = it },
                    searchActive = searchActive.value,
                    onItemClick = ::playFile,
                    onShareClick = ::shareFile,
                    onDeleteClick = ::confirmDelete,
                    queueCount = queueCount.value,
                    onQueueClick = { NavAnim.go(this, Intent(this, ComposeQueueActivity::class.java), NavAnim.TAB_QUEUE, NavAnim.TAB_NONE) },
                    showTip = !tipDismissed.value,
                    onTipDismiss = { tipDismissed.value = true },
                    selectedNavIndex = 2,
                    onNavSelected = ::openNav,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
        queueCount.value = try {
            DownloadQueue.getInstance(this).getQueue().size
        } catch (_: Exception) {
            0
        }
    }

    // ── Navigasi bottom nav (Figma 1:501: tab Downloads aktif) ──

    private fun openNav(index: Int) {
        when (index) {
            0 -> NavAnim.go(
                this,
                Intent(this, ComposeHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                NavAnim.TAB_DOWNLOADS,
                NavAnim.TAB_HOME,
            )
            1 -> NavAnim.go(this, Intent(this, ComposeBrowserActivity::class.java), NavAnim.TAB_DOWNLOADS, NavAnim.TAB_BROWSER)
            // 2 = Downloads: sudah di sini — no-op (idempoten). Queue punya
            // entry eksplisit via pill LIVE di header, bukan toggle tab.
            2 -> Unit
            3 -> NavAnim.go(this, Intent(this, ComposeSettingsActivity::class.java), NavAnim.TAB_DOWNLOADS, NavAnim.TAB_SETTINGS)
        }
    }

    // ── Data loading (dipindah dari DownloadsActivity) ─────────

    private fun loadFiles() {
        files.value = getDownloadedFiles()
    }

    private data class FoundFile(
        val name: String, val size: Long, val date: Long,
        val mime: String, val uri: Uri,
    )

    private fun getDownloadedFiles(): List<DownloadItem> {
        val found = mutableListOf<FoundFile>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.MIME_TYPE,
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%TubeNime%")
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                while (cursor.moveToNext()) {
                    found.add(
                        FoundFile(
                            cursor.getString(nameCol) ?: "unknown",
                            cursor.getLong(sizeCol),
                            cursor.getLong(dateCol) * 1000,
                            cursor.getString(mimeCol) ?: "",
                            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idCol)),
                        )
                    )
                }
            }
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TubeNime",
            )
            if (dir.exists()) {
                dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                    val mime = mimeForExtension(file.extension.lowercase())
                    found.add(FoundFile(file.name, file.length(), file.lastModified(), mime, Uri.fromFile(file)))
                }
            }
        }

        return found.mapIndexed { i, f ->
            DownloadItem(
                fileName = f.name,
                size = formatSize(f.size),
                date = formatDate(f.date),
                platform = platformFromName(f.name),
                quality = f.name.substringAfterLast('.', "").uppercase(),
                tapeLabel = "★ TAPE_%02d".format((i % 9) + 1),
                tapeColor = when (i % 3) {
                    0 -> WashiYellow
                    1 -> WashiPink
                    else -> WashiMint
                },
                mimeType = f.mime.ifEmpty { mimeForExtension(f.name.substringAfterLast('.', "")) },
                uriString = f.uri.toString(),
            )
        }
    }

    private fun mimeForExtension(ext: String): String = when (ext) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        else -> "application/octet-stream"
    }

    // ── Filter & storage ────────────────────────────────────────

    private fun filteredItems(): List<DownloadItem> {
        val base = when (selectedFilter.value) {
            1 -> files.value.filter { it.mimeType.startsWith("video/") }
            2 -> files.value.filter { it.mimeType.startsWith("audio/") }
            else -> files.value
        }
        val q = searchQuery.value.trim()
        return if (q.isEmpty()) base else base.filter { it.fileName.contains(q, ignoreCase = true) }
    }

    private fun filterLabelsWithCounts(): List<String> {
        val all = files.value
        val videos = all.count { it.mimeType.startsWith("video/") }
        val audios = all.count { it.mimeType.startsWith("audio/") }
        return listOf("All (${all.size})", "Videos ($videos)", "Audio ($audios)")
    }

    /** Desain 1:512: pill menampilkan ruang BEBAS ("1.8 GB FREE"). */
    private fun formatFreeSize(): String {
        val freeBytes = try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            StatFs(dir.path).availableBytes
        } catch (_: Exception) {
            0L
        }
        return when {
            freeBytes >= 1L shl 30 -> "%.0f GB\nFREE".format(freeBytes / 1e9)
            freeBytes >= 1L shl 20 -> "%.0f MB\nFREE".format(freeBytes / 1e6)
            else -> "%.0f KB\nFREE".format(freeBytes / 1e3)
        }
    }

    private fun parseSizeBytes(display: String): Long {
        val v = display.substringBefore(' ').toDoubleOrNull() ?: 0.0
        return when {
            display.endsWith("GB", true) -> (v * 1e9).toLong()
            display.endsWith("MB", true) -> (v * 1e6).toLong()
            display.endsWith("KB", true) -> (v * 1e3).toLong()
            else -> v.toLong()
        }
    }

    // ── Aksi file ───────────────────────────────────────────────

    private fun playFile(item: DownloadItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(item.uriString), item.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(item: DownloadItem) {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share file",
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: DownloadItem) {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_TubeNime)
            .setTitle("Delete file?")
            .setMessage(item.fileName)
            .setPositiveButton("Delete") { _, _ -> deleteFile(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFile(item: DownloadItem) {
        val uri = Uri.parse(item.uriString)
        val deleted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.delete(uri, null, null) > 0
            } else {
                java.io.File(uri.path ?: "").delete()
            }
        } catch (e: Exception) {
            false
        }
        Toast.makeText(this, if (deleted) "File deleted" else "Cannot delete file", Toast.LENGTH_SHORT).show()
        loadFiles()
    }

    // ── Format helpers ──────────────────────────────────────────

    private fun platformFromName(name: String): String = when {
        name.contains("youtube", true) -> "YouTube"
        name.contains("tiktok", true) -> "TikTok"
        name.contains("instagram", true) -> "Instagram"
        name.contains("facebook", true) -> "Facebook"
        name.contains("bilibili", true) -> "Bilibili"
        else -> "Direct"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    private fun formatDate(timestamp: Long): String = try {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}
