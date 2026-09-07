package com.tubenime.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared download queue used by the batch feature.
 *
 * - Singleton: state is shared across every screen (MainActivity enqueues,
 *   DownloadsQueueActivity displays/controls).
 * - Worker pool: premium users download up to `max_concurrent` items in
 *   parallel; free users are limited to 1 worker (sequential).
 * - Items are processed to completion before the worker claims the next one
 *   (unlike the old implementation which fired-and-forgot).
 *
 * QueueItem.type is one of: "video", "audio", "video_audio" (merged, premium),
 * "playlist" (premium).
 */
class DownloadQueue private constructor(context: Context) {

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

    companion object {
        @Volatile
        private var instance: DownloadQueue? = null

        fun getInstance(context: Context): DownloadQueue {
            return instance ?: synchronized(this) {
                instance ?: DownloadQueue(context.applicationContext).also { instance = it }
            }
        }

        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
        const val TYPE_MERGED = "video_audio"
        const val TYPE_PLAYLIST = "playlist"
    }

    private val appContext = context.applicationContext

    private val queue = CopyOnWriteArrayList<QueueItem>()
    private val listeners = mutableListOf<QueueListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val currentItemId = AtomicInteger(0)
    private val isProcessing = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Item id -> the DownloadManager that runs it (used to cancel on pause/remove). */
    private val runningManagers = ConcurrentHashMap<Int, DownloadManager>()
    /** Item id -> gate the queue worker waits on (completed by callback or pause/remove). */
    private val pendingGates = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()

    // ── Listener management ────────────────────────────────────────────────────────

    fun addListener(listener: QueueListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: QueueListener) {
        listeners.remove(listener)
    }

    // ── Public queue operations ────────────────────────────────────────────────────

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
        startProcessing()
        return item
    }

    /** Number of items waiting or being downloaded. */
    fun activeCount(): Int = queue.size

    fun startProcessing() {
        if (!isProcessing.compareAndSet(false, true)) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    while (hasPending()) {
                        val workerCount = maxConcurrent()
                        val workers = (1..workerCount).map {
                            scope.launch(Dispatchers.IO) { workerLoop() }
                        }
                        workers.forEach { it.join() }
                    }
                }
            } finally {
                isProcessing.set(false)
            }
            if (!hasPending() && !hasActive()) {
                notifyQueueComplete()
            }
        }
    }

    fun pauseItem(itemId: Int) {
        val item = queue.find { it.id == itemId } ?: return
        if (item.status != "downloading") return
        runningManagers.remove(itemId)?.cancel()
        val updatedItem = item.copy(status = "paused")
        updateItem(updatedItem)
        notifyItemPaused(updatedItem)
        // Release the worker waiting on this item.
        pendingGates.remove(itemId)?.complete(Unit)
    }

    fun resumeItem(itemId: Int) {
        val item = queue.find { it.id == itemId } ?: return
        if (item.status != "paused") return
        val updatedItem = item.copy(status = "pending", progress = 0, speed = "", eta = "", error = null)
        updateItem(updatedItem)
        notifyItemResumed(updatedItem)
        startProcessing()
    }

    fun removeItem(itemId: Int) {
        val item = queue.find { it.id == itemId } ?: return
        if (item.status == "downloading") {
            runningManagers.remove(itemId)?.cancel()
            pendingGates.remove(itemId)?.complete(Unit)
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

    // ── Internals ─────────────────────────────────────────────────────────────────

    private fun maxConcurrent(): Int {
        if (!PremiumManager.isPremium()) return 1
        val prefs = appContext.getSharedPreferences(PremiumManager.PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt("max_concurrent", 3)
        return saved.coerceIn(1, TierRules.PREMIUM_MAX_CONCURRENT)
    }

    private fun hasPending(): Boolean = queue.any { it.status == "pending" }

    private fun hasActive(): Boolean = queue.any { it.status == "downloading" }

    private fun jobIdFor(itemId: Int): String = "queue_$itemId"

    private fun defaultMergeVideoFormat(): String =
        "bestvideo[height<=1080][ext=mp4]/bestvideo[height<=1080]"

    /** Atomically claim the next pending item and mark it downloading. */
    private fun claimNext(): QueueItem? {
        synchronized(queue) {
            val index = queue.indexOfFirst { it.status == "pending" }
            if (index < 0) return null
            val claimed = queue[index].copy(status = "downloading")
            queue[index] = claimed
            notifyItemStarted(claimed)
            return claimed
        }
    }

    private suspend fun workerLoop() {
        while (true) {
            val item = claimNext() ?: return
            try {
                processItem(item)
            } catch (e: CancellationException) {
                // Queue shut down or item was removed — stop this worker.
                if (queue.any { it.id == item.id && it.status == "downloading" }) {
                    // Only possible on app-scope cancellation; leave as-is otherwise.
                }
                return
            } catch (e: Exception) {
                markFailed(item.id, e.message ?: "Download failed")
            }
        }
    }

    private suspend fun processItem(item: QueueItem) {
        val gate = CompletableDeferred<Unit>()
        pendingGates[item.id] = gate
        val manager = DownloadManager(appContext)
        runningManagers[item.id] = manager

        val jobId = jobIdFor(item.id)
        val callback = object : DownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percent: Int) {
                mainHandler.post {
                    val current = queue.find { it.id == item.id } ?: return@post
                    if (current.status != "downloading") return@post
                    updateItem(current.copy(progress = percent))
                }
            }

            override fun onStatusUpdate(statusText: String) {
                mainHandler.post {
                    val current = queue.find { it.id == item.id } ?: return@post
                    if (current.status != "downloading") return@post
                    updateItem(current.copy(speed = statusText))
                }
            }

            override fun onComplete(filePath: String, filename: String) {
                mainHandler.post {
                    val current = queue.find { it.id == item.id } ?: return@post
                    if (current.status != "downloading") return@post
                    val done = current.copy(
                        status = "completed",
                        progress = 100,
                        filePath = filePath,
                        filename = filename
                    )
                    updateItem(done)
                    NotificationHelper.showDownloadComplete(appContext, filename, filePath)
                    pendingGates.remove(item.id)?.complete(Unit)
                }
            }

            override fun onError(error: String) {
                mainHandler.post {
                    val current = queue.find { it.id == item.id } ?: return@post
                    if (current.status != "downloading") return@post
                    val failed = current.copy(status = "failed", error = error)
                    updateItem(failed)
                    NotificationHelper.showDownloadError(appContext, item.title, error)
                    pendingGates.remove(item.id)?.complete(Unit)
                }
            }
        }

        try {
            withContext(Dispatchers.IO) {
                when (item.type) {
                    TYPE_MERGED -> manager.downloadMerged(
                        url = item.url,
                        videoFormat = item.formatId ?: defaultMergeVideoFormat(),
                        audioFormat = "bestaudio",
                        callback = callback,
                        jobId = jobId
                    )
                    TYPE_PLAYLIST -> manager.downloadPlaylist(
                        url = item.url,
                        format = item.formatId ?: "bestvideo[height<=720]+bestaudio/best",
                        maxVideos = 50,
                        callback = callback,
                        jobId = jobId
                    )
                    else -> manager.downloadFromUrl(
                        url = item.url,
                        type = item.type,
                        formatId = item.formatId,
                        callback = callback,
                        jobId = jobId
                    )
                }
                gate.await()
            }
        } finally {
            runningManagers.remove(item.id)
            pendingGates.remove(item.id)
        }
    }

    private fun markFailed(itemId: Int, error: String) {
        val current = queue.find { it.id == itemId } ?: return
        if (current.status != "downloading") return
        val failed = current.copy(status = "failed", error = error)
        updateItem(failed)
        notifyItemUpdated(failed)
        pendingGates.remove(itemId)?.complete(Unit)
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
}
