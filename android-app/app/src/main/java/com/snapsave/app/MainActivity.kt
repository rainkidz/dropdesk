package com.snapsave.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var serverCard: MaterialCardView
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private lateinit var urlCard: MaterialCardView
    private lateinit var urlInput: TextInputEditText
    private lateinit var inspectButton: MaterialButton
    private lateinit var loadingCard: MaterialCardView
    private lateinit var loadingText: TextView
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var videoInfoCard: MaterialCardView
    private lateinit var platformChip: Chip
    private lateinit var videoTitle: TextView
    private lateinit var videoDuration: TextView
    private lateinit var downloadTypeCard: MaterialCardView
    private lateinit var videoOnlyButton: MaterialButton
    private lateinit var audioOnlyButton: MaterialButton
    private lateinit var formatCard: MaterialCardView
    private lateinit var formatTypeLabel: TextView
    private lateinit var formatRadioGroup: RadioGroup
    private lateinit var downloadButton: MaterialButton
    private lateinit var progressCard: MaterialCardView
    private lateinit var downloadProgress: LinearProgressIndicator
    private lateinit var downloadStatus: TextView
    private lateinit var completeCard: MaterialCardView
    private lateinit var completeFileName: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var newDownloadButton: MaterialButton
    private lateinit var historyHeader: TextView
    private lateinit var historyList: LinearLayout

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentInspect: InspectResponse? = null
    private var selectedFormatId: String? = null
    private var selectedType: String = "video" // "video" or "audio"
    private var lastJobId: String? = null
    private var lastDownloadUrl: String? = null
    private var lastFilename: String? = null
    private var lastContentType: String? = null

    // ── Platform colors ──────────────────────────────────────
    private val platformColors = mapOf(
        "youtube" to 0xFFFF0000.toInt(),
        "tiktok" to 0xFFEE1D52.toInt(),
        "facebook" to 0xFF1877F2.toInt(),
        "instagram" to 0xFFE4405F.toInt(),
        "threads" to 0xFF000000.toInt(),
        "unknown" to 0xFF757575.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()

        // Load saved server URL
        val savedUrl = getServerUrl()
        if (savedUrl != null) {
            showMainUI()
            ApiClient.configure(savedUrl)
            serverUrlInput.setText(savedUrl)
        } else {
            showServerSetup()
        }

        // Handle shared intent (URL from other apps)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null && isUrl(sharedText)) {
                urlInput.setText(sharedText.trim())
                // Auto-inspect if server is connected
                if (ApiClient.getBaseUrl().isNotEmpty()) {
                    doInspect(sharedText.trim())
                }
            }
        }
    }

    private fun isUrl(text: String): Boolean {
        return text.contains("youtube.com") || text.contains("youtu.be") ||
                text.contains("tiktok.com") || text.contains("facebook.com") ||
                text.contains("instagram.com") || text.contains("threads.net") ||
                text.startsWith("http://") || text.startsWith("https://")
    }

    private fun bindViews() {
        serverCard = findViewById(R.id.serverCard)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        connectButton = findViewById(R.id.connectButton)
        urlCard = findViewById(R.id.urlCard)
        urlInput = findViewById(R.id.urlInput)
        inspectButton = findViewById(R.id.inspectButton)
        loadingCard = findViewById(R.id.loadingCard)
        loadingText = findViewById(R.id.loadingText)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorText)
        videoInfoCard = findViewById(R.id.videoInfoCard)
        platformChip = findViewById(R.id.platformChip)
        videoTitle = findViewById(R.id.videoTitle)
        videoDuration = findViewById(R.id.videoDuration)
        downloadTypeCard = findViewById(R.id.downloadTypeCard)
        videoOnlyButton = findViewById(R.id.videoOnlyButton)
        audioOnlyButton = findViewById(R.id.audioOnlyButton)
        formatCard = findViewById(R.id.formatCard)
        formatTypeLabel = findViewById(R.id.formatTypeLabel)
        formatRadioGroup = findViewById(R.id.formatRadioGroup)
        downloadButton = findViewById(R.id.downloadButton)
        progressCard = findViewById(R.id.progressCard)
        downloadProgress = findViewById(R.id.downloadProgress)
        downloadStatus = findViewById(R.id.downloadStatus)
        completeCard = findViewById(R.id.completeCard)
        completeFileName = findViewById(R.id.completeFileName)
        saveButton = findViewById(R.id.saveButton)
        newDownloadButton = findViewById(R.id.newDownloadButton)
        historyHeader = findViewById(R.id.historyHeader)
        historyList = findViewById(R.id.historyList)
    }

    private fun setupListeners() {
        // Server connect
        connectButton.setOnClickListener {
            val url = serverUrlInput.text.toString().trim()
            if (url.isEmpty()) {
                serverUrlInput.error = "Enter server URL"
                return@setOnClickListener
            }
            connectButton.isEnabled = false
            connectButton.text = "Connecting..."

            mainScope.launch {
                try {
                    ApiClient.configure(url)
                    val health = ApiClient.getApi().healthCheck()
                    if (health["status"] == "ok") {
                        saveServerUrl(url)
                        showMainUI()
                        Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_SHORT).show()
                    } else {
                        showError("Server returned unexpected response")
                    }
                } catch (e: Exception) {
                    showError("Cannot connect to server: ${e.message}")
                } finally {
                    connectButton.isEnabled = true
                    connectButton.text = "Connect & Start"
                }
            }
        }

        // Inspect button
        inspectButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                urlInput.error = "Enter a URL"
                return@setOnClickListener
            }
            doInspect(url)
        }

        // Keyboard action
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                inspectButton.performClick()
                true
            } else false
        }

        // Download type buttons
        videoOnlyButton.setOnClickListener { selectType("video") }
        audioOnlyButton.setOnClickListener { selectType("audio") }

        // Download button
        downloadButton.setOnClickListener {
            val formatId = selectedFormatId
            val url = urlInput.text.toString().trim()
            if (formatId == null) {
                Toast.makeText(this, "Select a format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            doDownload(url, formatId)
        }

        // Save button
        saveButton.setOnClickListener {
            lastDownloadUrl?.let { url ->
                lastFilename?.let { name ->
                    lastContentType?.let { ct ->
                        saveToDevice(url, name, ct)
                    }
                }
            }
        }

        // New download
        newDownloadButton.setOnClickListener { resetUI() }
    }

    private fun doInspect(url: String) {
        hideAll()
        loadingCard.visibility = View.VISIBLE
        loadingText.text = "Inspecting link..."
        inspectButton.isEnabled = false

        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)

        mainScope.launch {
            try {
                val result = ApiClient.getApi().inspect(InspectRequest(url))
                currentInspect = result
                showResult(result)
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            } finally {
                loadingCard.visibility = View.GONE
                inspectButton.isEnabled = true
            }
        }
    }

    private fun showResult(result: InspectResponse) {
        hideAll()

        // Platform chip
        val platform = result.platform.lowercase()
        val color = platformColors[platform] ?: platformColors["unknown"]!!
        platformChip.text = platform.replaceFirstChar { it.uppercase() }
        platformChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(color)
        platformChip.setTextColor(Color.WHITE)

        // Title & duration
        videoTitle.text = result.title ?: "Unknown title"
        videoDuration.text = formatDuration(result.duration)

        videoInfoCard.visibility = View.VISIBLE

        // Check if platform needs cookies
        if (result.formats.isEmpty()) {
            showError("No downloadable formats found. This platform may require login (cookies).")
            return
        }

        // Show download type selection
        val hasVideoFormats = result.formats.any { it.formatId.startsWith("video_") || it.formatId == "bestvideo" }
        val hasAudioFormats = result.formats.any { it.formatId.startsWith("audio_") || it.formatId == "bestaudio" }

        if (hasVideoFormats && hasAudioFormats) {
            downloadTypeCard.visibility = View.VISIBLE
            selectType("video")
        } else if (hasVideoFormats) {
            selectType("video")
        } else if (hasAudioFormats) {
            selectType("audio")
        } else {
            // TikTok format - show all
            downloadTypeCard.visibility = View.VISIBLE
            selectType("video")
        }
    }

    private fun selectType(type: String) {
        selectedType = type
        val result = currentInspect ?: return

        // Highlight selected button
        videoOnlyButton.strokeWidth = if (type == "video") 3 else 1
        audioOnlyButton.strokeWidth = if (type == "audio") 3 else 1

        // Filter formats
        val filtered = when (type) {
            "video" -> result.formats.filter {
                it.formatId.startsWith("video_") || it.formatId == "bestvideo" ||
                it.formatId == "sd" || it.formatId == "hd" ||
                (it.label.lowercase().contains("video") && !it.label.lowercase().contains("audio"))
            }
            "audio" -> result.formats.filter {
                it.formatId.startsWith("audio_") || it.formatId == "bestaudio" ||
                it.formatId == "mp3" || it.formatId.contains("audio") ||
                it.label.lowercase().contains("audio")
            }
            else -> result.formats
        }

        val formats = if (filtered.isEmpty()) result.formats else filtered
        showFormats(formats, type)
    }

    private fun showFormats(formats: List<FormatOption>, type: String) {
        formatRadioGroup.removeAllViews()
        selectedFormatId = null

        val typeLabel = if (type == "audio") "Audio formats" else "Video formats"
        formatTypeLabel.text = typeLabel

        formats.forEachIndexed { index, format ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                text = buildString {
                    append(format.label)
                    format.filesizeBytes?.let {
                        append(" • ")
                        append(formatFileSize(it))
                    }
                    if (format.requiresPremium) append(" ⭐")
                }
                textSize = 14f
                setPadding(0, 12, 0, 12)
                tag = format.formatId
            }
            formatRadioGroup.addView(radioButton)

            // Select first by default
            if (index == 0) {
                radioButton.isChecked = true
                selectedFormatId = format.formatId
            }

            radioButton.setOnClickListener {
                selectedFormatId = format.formatId as String
            }
        }

        formatCard.visibility = View.VISIBLE
    }

    private fun doDownload(url: String, formatId: String) {
        val platform = currentInspect?.platform?.lowercase() ?: "youtube"

        hideAll()
        progressCard.visibility = View.VISIBLE
        downloadProgress.progress = 0
        downloadStatus.text = "Starting download..."
        downloadButton.isEnabled = false

        mainScope.launch {
            try {
                val response = ApiClient.getApi().startDownload(
                    DownloadRequest(url = url, formatId = formatId, platform = platform)
                )
                lastJobId = response.jobId
                pollDownload(response.jobId)
            } catch (e: Exception) {
                showError("Download failed: ${e.message}")
                progressCard.visibility = View.GONE
                downloadButton.isEnabled = true
            }
        }
    }

    private fun pollDownload(jobId: String) {
        mainScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 2 minutes max

            while (attempts < maxAttempts) {
                try {
                    val status = ApiClient.getApi().getDownloadStatus(jobId)
                    val progressPct = ((status.progress ?: 0.0) * 100).toInt()

                    downloadProgress.progress = progressPct
                    downloadStatus.text = when (status.status) {
                        "pending" -> "Queued..."
                        "running" -> if (progressPct > 0) "Downloading... $progressPct%" else "Downloading..."
                        "completed" -> "Download complete!"
                        "failed" -> "Failed: ${status.error}"
                        else -> status.status
                    }

                    when (status.status) {
                        "completed" -> {
                            lastDownloadUrl = status.downloadUrl
                            lastFilename = status.filename
                            lastContentType = status.contentType
                            showComplete(status)
                            return@launch
                        }
                        "failed" -> {
                            showError(status.error ?: "Download failed")
                            progressCard.visibility = View.GONE
                            return@launch
                        }
                    }

                    delay(1000)
                    attempts++
                } catch (e: Exception) {
                    delay(2000)
                    attempts++
                }
            }

            showError("Download timed out")
            progressCard.visibility = View.GONE
        }
    }

    private fun showComplete(status: DownloadStatus) {
        hideAll()
        completeCard.visibility = View.VISIBLE
        completeFileName.text = status.filename ?: "downloaded_file"
    }

    private fun saveToDevice(downloadUrl: String, filename: String, contentType: String) {
        saveButton.isEnabled = false
        saveButton.text = "Saving..."

        mainScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val fullUrl = if (downloadUrl.startsWith("http")) {
                        downloadUrl
                    } else {
                        "${ApiClient.getBaseUrl()}$downloadUrl"
                    }

                    val url = URL(fullUrl)
                    val connection = url.openConnection()
                    connection.connect()

                    val inputStream = connection.getInputStream()
                    val ext = filename.substringAfterLast('.', "bin")
                    val mimeType = contentType ?: "application/octet-stream"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ — use MediaStore
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, filename)
                            put(MediaStore.Downloads.MIME_TYPE, mimeType)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SnapSave")
                        }
                        val resolver = contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            ?: throw Exception("Cannot create file")

                        resolver.openOutputStream(uri)?.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } else {
                        // Android 9 and below — direct file write
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SnapSave")
                        dir.mkdirs()
                        val file = File(dir, filename)
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    inputStream.close()
                }

                Toast.makeText(this@MainActivity, "Saved to Downloads/SnapSave/", Toast.LENGTH_LONG).show()
                saveButton.text = "✅ Saved!"
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                saveButton.isEnabled = true
                saveButton.text = "💾 Save to Downloads"
            }
        }
    }

    // ── UI Helpers ───────────────────────────────────────────

    private fun showServerSetup() {
        serverCard.visibility = View.VISIBLE
        urlCard.alpha = 0.5f
        urlCard.isEnabled = false
    }

    private fun showMainUI() {
        serverCard.visibility = View.GONE
        urlCard.alpha = 1.0f
        urlCard.isEnabled = true
        urlInput.requestFocus()
    }

    private fun showError(message: String) {
        hideAll()
        errorCard.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun hideAll() {
        loadingCard.visibility = View.GONE
        errorCard.visibility = View.GONE
        videoInfoCard.visibility = View.GONE
        downloadTypeCard.visibility = View.GONE
        formatCard.visibility = View.GONE
        progressCard.visibility = View.GONE
        completeCard.visibility = View.GONE
    }

    private fun resetUI() {
        hideAll()
        urlInput.text?.clear()
        currentInspect = null
        selectedFormatId = null
        lastJobId = null
        lastDownloadUrl = null
        lastFilename = null
        lastContentType = null
        downloadButton.isEnabled = true
        saveButton.isEnabled = true
        saveButton.text = "💾 Save to Downloads"
        urlInput.requestFocus()
    }

    // ── Persistence ──────────────────────────────────────────

    private fun getServerUrl(): String? {
        return getSharedPreferences("snapsave", MODE_PRIVATE).getString("server_url", null)
    }

    private fun saveServerUrl(url: String) {
        getSharedPreferences("snapsave", MODE_PRIVATE).edit().putString("server_url", url).apply()
    }

    // ── Utilities ────────────────────────────────────────────

    private fun formatDuration(seconds: Double?): String {
        if (seconds == null) return ""
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

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
