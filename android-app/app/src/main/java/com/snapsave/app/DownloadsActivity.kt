package com.snapsave.app

import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import com.google.android.material.card.MaterialCardView
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsActivity : AppCompatActivity() {

    private lateinit var fileList: LinearLayout
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        fileList = findViewById(R.id.fileList)
        emptyText = findViewById(R.id.emptyText)

        // Back button
        findViewById<ImageButton>(R.id.backButton)?.setOnClickListener {
            finish()
        }

        loadFiles()
    }

    private fun loadFiles() {
        fileList.removeAllViews()
        val files = getDownloadedFiles()

        if (files.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            fileList.visibility = View.GONE
            return
        }

        emptyText.visibility = View.GONE
        fileList.visibility = View.VISIBLE

        files.forEach { file ->
            val card = createFileCard(file)
            fileList.addView(card)
        }
    }

    private fun getDownloadedFiles(): List<DownloadedFile> {
        val files = mutableListOf<DownloadedFile>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — query MediaStore
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.MIME_TYPE
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%SnapSave%")
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "unknown"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000
                    val mime = cursor.getString(mimeCol) ?: ""
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)

                    files.add(DownloadedFile(name, size, date, mime, uri))
                }
            }
        } else {
            // Android 9 and below — direct file access
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapSave")
            if (dir.exists()) {
                dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                    val ext = file.extension.lowercase()
                    val mime = when (ext) {
                        "mp4" -> "video/mp4"
                        "webm" -> "video/webm"
                        "mkv" -> "video/x-matroska"
                        "mp3" -> "audio/mpeg"
                        "m4a" -> "audio/mp4"
                        "ogg" -> "audio/ogg"
                        "opus" -> "audio/opus"
                        else -> "application/octet-stream"
                    }
                    files.add(DownloadedFile(file.name, file.length(), file.lastModified(), mime, Uri.fromFile(file)))
                }
            }
        }

        return files
    }

    private fun createFileCard(file: DownloadedFile): View {
        val density = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            radius = 12 * density
            cardElevation = 2 * density
            setCardBackgroundColor(Color.WHITE)
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }

        // File name
        val nameText = TextView(this).apply {
            text = file.name
            textSize = 14f
            setTextColor(Color.parseColor("#1a1a1a"))
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }
        layout.addView(nameText)

        // Info row
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        // Format badge
        val formatBadge = TextView(this).apply {
            val ext = file.name.substringAfterLast('.', "").uppercase()
            text = ext
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            setBackgroundColor(when {
                ext == "MP4" || ext == "WEBM" || ext == "MKV" -> Color.parseColor("#4CAF50")
                else -> Color.parseColor("#2196F3")
            })
        }
        infoLayout.addView(formatBadge)

        // Size
        val sizeText = TextView(this).apply {
            text = "  ${formatSize(file.size)}"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
        }
        infoLayout.addView(sizeText)

        // Date
        if (file.dateModified > 0) {
            val dateText = TextView(this).apply {
                text = "  •  ${formatDate(file.dateModified)}"
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
            }
            infoLayout.addView(dateText)
        }

        layout.addView(infoLayout)

        // Play/Open button
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        val playButton = android.widget.Button(this).apply {
            text = if (file.mimeType.startsWith("video/")) "▶ Play" else "🔊 Play"
            textSize = 12f
            setTextColor(Color.parseColor("#2196F3"))
            setBackgroundColor(Color.TRANSPARENT)
            isAllCaps = false
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(file.uri, file.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@DownloadsActivity, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        buttonLayout.addView(playButton)

        val shareButton = android.widget.Button(this).apply {
            text = "↗ Share"
            textSize = 12f
            setTextColor(Color.parseColor("#4CAF50"))
            setBackgroundColor(Color.TRANSPARENT)
            isAllCaps = false
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = file.mimeType
                        putExtra(Intent.EXTRA_STREAM, file.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share file"))
                } catch (e: Exception) {
                    Toast.makeText(this@DownloadsActivity, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        buttonLayout.addView(shareButton)

        layout.addView(buttonLayout)
        card.addView(layout)

        return card
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    data class DownloadedFile(
        val name: String,
        val size: Long,
        val dateModified: Long,
        val mimeType: String,
        val uri: Uri
    )
}
