package com.snapsave.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class DownloadsQueueActivity : AppCompatActivity(), DownloadQueue.QueueListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var queueAdapter: QueueAdapter
    private lateinit var downloadQueue: DownloadQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads_queue)

        recyclerView = findViewById(R.id.queueRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        downloadQueue = DownloadQueue(this)
        downloadQueue.addListener(this)

        queueAdapter = QueueAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = queueAdapter

        // Load current queue
        queueAdapter.submitList(downloadQueue.getQueue())
        updateEmptyView()
    }

    override fun onResume() {
        super.onResume()
        downloadQueue.addListener(this)
        queueAdapter.submitList(downloadQueue.getQueue())
        updateEmptyView()
    }

    override fun onPause() {
        super.onPause()
        downloadQueue.removeListener(this)
    }

    private fun updateEmptyView() {
        if (downloadQueue.getQueue().isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    // QueueListener implementations
    override fun onItemAdded(item: DownloadQueue.QueueItem) {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
            updateEmptyView()
        }
    }

    override fun onItemUpdated(item: DownloadQueue.QueueItem) {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
        }
    }

    override fun onItemRemoved(item: DownloadQueue.QueueItem) {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
            updateEmptyView()
        }
    }

    override fun onQueueComplete() {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
        }
    }

    override fun onItemStarted(item: DownloadQueue.QueueItem) {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
        }
    }

    override fun onItemPaused(item: DownloadQueue.QueueItem) {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
        }
    }

    override fun onItemResumed(item: DownloadQueue.QueueItem) {
        runOnUiThread {
            queueAdapter.submitList(downloadQueue.getQueue())
        }
    }

    // RecyclerView Adapter
    inner class QueueAdapter : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

        private var items = listOf<DownloadQueue.QueueItem>()

        fun submitList(newItems: List<DownloadQueue.QueueItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_queue, parent, false)
            return QueueViewHolder(view)
        }

        override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleText: TextView = itemView.findViewById(R.id.itemTitle)
            private val statusText: TextView = itemView.findViewById(R.id.itemStatus)
            private val progressText: TextView = itemView.findViewById(R.id.itemProgress)
            private val progressBar: ProgressBar = itemView.findViewById(R.id.itemProgressBar)
            private val pauseResumeButton: ImageButton = itemView.findViewById(R.id.pauseResumeButton)
            private val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)
            private val card: MaterialCardView = itemView.findViewById(R.id.card)

            fun bind(item: DownloadQueue.QueueItem) {
                titleText.text = item.title
                statusText.text = item.status.replaceFirstChar { it.uppercase() }
                progressText.text = "${item.progress}%"

                // Update progress bar
                progressBar.progress = item.progress

                // Update status color
                val statusColor = when (item.status) {
                    "completed" -> R.color.success
                    "failed" -> R.color.error
                    "downloading" -> R.color.primary
                    "paused" -> R.color.warning
                    else -> R.color.text_secondary
                }
                statusText.setTextColor(getColor(statusColor))

                // Update card stroke
                when (item.status) {
                    "completed" -> {
                        card.strokeColor = getColor(R.color.success)
                        card.strokeWidth = 2
                    }
                    "failed" -> {
                        card.strokeColor = getColor(R.color.error)
                        card.strokeWidth = 2
                    }
                    "downloading" -> {
                        card.strokeColor = getColor(R.color.primary)
                        card.strokeWidth = 2
                    }
                    else -> {
                        card.strokeWidth = 0
                    }
                }

                // Pause/Resume button
                when (item.status) {
                    "downloading" -> {
                        pauseResumeButton.setImageResource(android.R.drawable.ic_media_pause)
                        pauseResumeButton.visibility = View.VISIBLE
                        pauseResumeButton.setOnClickListener {
                            downloadQueue.pauseItem(item.id)
                        }
                    }
                    "paused" -> {
                        pauseResumeButton.setImageResource(android.R.drawable.ic_media_play)
                        pauseResumeButton.visibility = View.VISIBLE
                        pauseResumeButton.setOnClickListener {
                            downloadQueue.resumeItem(item.id)
                        }
                    }
                    else -> {
                        pauseResumeButton.visibility = View.GONE
                    }
                }

                // Remove button
                removeButton.setOnClickListener {
                    downloadQueue.removeItem(item.id)
                }

                // Click to open file (if completed)
                if (item.status == "completed" && item.filePath != null) {
                    itemView.setOnClickListener {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        intent.setDataAndType(
                            android.net.Uri.parse(item.filePath),
                            getMimeType(item.filename ?: "")
                        )
                        startActivity(intent)
                    }
                }
            }

            private fun getMimeType(filename: String): String {
                return when {
                    filename.endsWith(".mp4") -> "video/mp4"
                    filename.endsWith(".webm") -> "video/webm"
                    filename.endsWith(".mp3") -> "audio/mpeg"
                    filename.endsWith(".m4a") -> "audio/mp4"
                    else -> "application/octet-stream"
                }
            }
        }
    }
}
