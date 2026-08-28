package com.snapsave.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DownloadQueue(private val context: Context) {

    data class QueueItem(
        val id: Int,
        val url: String,
        val title: String,
        val type: String, // "video", "audio", "video_audio", "playlist"
        val formatId: String?,
        val status: String = "pending", // pending, downloading, paused, completed, failed
        val progress: Int = 0,
        val speed: String = "",
        val eta: String = "",
        val filePath: String? = null,
        val filename: String? = null,
        val error: String? = null
    )

    interface QueueListener {
        fun onItemAdded(item: QueueItem)
        fun onItemUpdated(item: QueueItem)
        fun onItemRemoved(item: QueueItem)
        fun onQueueComplete()
        fun onItemStarted(item: QueueItem)
        fun onItemPaused(item: QueueItem)
        fun onItemResumed(item: QueueItem)
    }

    private val queue = CopyOnWriteArrayList<QueueItem>()
    private val listeners = mutableListOf<QueueListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isProcessing = AtomicBoolean(false)
    private val currentItemId = AtomicInteger(0)
    private val downloadManager = DownloadManager(context)
    private var processingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun addListener(listener: QueueListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: QueueListener) {
        listeners.remove(listener)
    }

    fun addToQueue(url: String, title: String, type: String, formatId: String?): QueueItem {
        val item = QueueItem(
            id = currentItemId.incrementAndGet(),
            url = url,
            title = title,
            type = type,
            formatId = formatId
        )
        queue.add(item)
        notifyItemAdded(item)
        return item
    }

    fun startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            processingJob = scope.launch {
                processQueue()
            }
        }
    }

    fun pauseItem(itemId: Int) {
        val item = queue.find { it.id == itemId } ?: return
        if (item.status == "downloading") {
            downloadManager.cancel()
            val updatedItem = item.copy(status = "paused")
            updateItem(updatedItem)
            notifyItemPaused(updatedItem)
        }
    }

    fun resumeItem(itemId: Int) {
        val item = queue.find { it.id == itemId } ?: return
        if (item.status == "paused") {
            val updatedItem = item.copy(status = "pending")
            updateItem(updatedItem)
            notifyItemResumed(updatedItem)
            // Start processing if not already
            startProcessing()
        }
    }

    fun removeItem(itemId: Int) {
        val item = queue.find { it.id == itemId } ?: return
        if (item.status == "downloading") {
            downloadManager.cancel()
        }
        queue.remove(item)
        notifyItemRemoved(item)
    }

    fun clearCompleted() {
        val completed = queue.filter { it.status == "completed" || it.status == "failed" }
        completed.forEach { item ->
            queue.remove(item)
            notifyItemRemoved(item)
        }
    }

    fun getQueue(): List<QueueItem> = queue.toList()

    fun getCurrentItem(): QueueItem? = queue.find { it.status == "downloading" }

    fun getPendingItems(): List<QueueItem> = queue.filter { it.status == "pending" }

    private suspend fun processQueue() {
        while (isProcessing.get()) {
            val nextItem = queue.find { it.status == "pending" }
            if (nextItem != null) {
                downloadItem(nextItem)
            } else {
                // No more pending items
                isProcessing.set(false)
                notifyQueueComplete()
                break
            }
        }
    }

    private suspend fun downloadItem(item: QueueItem) {
        val downloadingItem = item.copy(status = "downloading")
        updateItem(downloadingItem)
        notifyItemStarted(downloadingItem)

        withContext(Dispatchers.IO) {
            try {
                downloadManager.downloadFromUrl(
                    url = item.url,
                    type = item.type,
                    formatId = item.formatId,
                    callback = object : DownloadManager.DownloadCallback {
                        override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int) {
                            mainHandler.post {
                                val currentItem = queue.find { it.id == item.id } ?: return@post
                                val updatedItem = currentItem.copy(
                                    progress = percent,
                                    speed = if (totalBytes > 0) "${bytesDownloaded * 100 / totalBytes}%" else ""
                                )
                                updateItem(updatedItem)
                            }
                        }

                        override fun onStatusUpdate(statusText: String) {
                            mainHandler.post {
                                val currentItem = queue.find { it.id == item.id } ?: return@post
                                val updatedItem = currentItem.copy(speed = statusText)
                                updateItem(updatedItem)
                            }
                        }

                        override fun onComplete(filePath: String, filename: String) {
                            mainHandler.post {
                                val currentItem = queue.find { it.id == item.id } ?: return@post
                                val completedItem = currentItem.copy(
                                    status = "completed",
                                    progress = 100,
                                    filePath = filePath,
                                    filename = filename
                                )
                                updateItem(completedItem)
                                NotificationHelper.showDownloadComplete(
                                    context,
                                    filename,
                                    filePath
                                )
                            }
                        }

                        override fun onError(error: String) {
                            mainHandler.post {
                                val currentItem = queue.find { it.id == item.id } ?: return@post
                                val failedItem = currentItem.copy(
                                    status = "failed",
                                    error = error
                                )
                                updateItem(failedItem)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                mainHandler.post {
                    val currentItem = queue.find { it.id == item.id } ?: return@post
                    val failedItem = currentItem.copy(
                        status = "failed",
                        error = e.message
                    )
                    updateItem(failedItem)
                }
            }
        }
    }

    private fun updateItem(item: QueueItem) {
        val index = queue.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            queue[index] = item
            notifyItemUpdated(item)
        }
    }

    private fun notifyItemAdded(item: QueueItem) {
        listeners.forEach { it.onItemAdded(item) }
    }

    private fun notifyItemUpdated(item: QueueItem) {
        listeners.forEach { it.onItemUpdated(item) }
    }

    private fun notifyItemRemoved(item: QueueItem) {
        listeners.forEach { it.onItemRemoved(item) }
    }

    private fun notifyQueueComplete() {
        listeners.forEach { it.onQueueComplete() }
    }

    private fun notifyItemStarted(item: QueueItem) {
        listeners.forEach { it.onItemStarted(item) }
    }

    private fun notifyItemPaused(item: QueueItem) {
        listeners.forEach { it.onItemPaused(item) }
    }

    private fun notifyItemResumed(item: QueueItem) {
        listeners.forEach { it.onItemResumed(item) }
    }

    fun destroy() {
        scope.cancel()
        downloadManager.cancel()
    }
}
