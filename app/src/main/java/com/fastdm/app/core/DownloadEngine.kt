package com.fastdm.app.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.fastdm.app.R
import com.fastdm.app.service.DownloadService
import com.fastdm.app.service.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class HttpException(val code: Int) : IOException("HTTP $code")

/**
 * Segmented, resumable download engine.
 *
 * - Every file is split into N byte ranges downloaded in parallel (HTTP Range).
 * - Progress per segment is persisted so pause / resume / app restart continue exactly where they stopped.
 * - A queue limits how many files download at the same time.
 * - The source URL of an unfinished item can be replaced ("refresh link") as long as the file is the same size.
 */
class DownloadEngine(private val context: Context, val settings: Settings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = DownloadStore(context)

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val speeds: StateFlow<Map<String, Long>> = _speeds.asStateFlow()

    /** One-shot user messages (shown as snackbars). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Id of the item waiting for a fresh link captured from the browser. */
    val refreshTarget = MutableStateFlow<String?>(null)

    private val jobs = ConcurrentHashMap<String, Job>()
    private val live = ConcurrentHashMap<String, Array<AtomicLong>>()
    private val speedMap = ConcurrentHashMap<String, Long>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // HTTP/1.1 gives every segment its own TCP connection, which is what makes parallel parts faster.
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .build()

    private val partsDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
        "parts"
    ).apply { mkdirs() }

    init {
        _items.value = store.load().map {
            if (it.status == Status.DOWNLOADING || it.status == Status.QUEUED || it.status == Status.SAVING)
                it.copy(status = Status.PAUSED) else it
        }
    }

    // ------------------------------------------------------------------ public API

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun add(req: DownloadRequest): Boolean {
        if (req.url.toHttpUrlOrNull() == null) {
            messages.tryEmit(context.getString(R.string.msg_invalid_link))
            return false
        }
        if (_items.value.any { it.url == req.url && it.status != Status.COMPLETED }) {
            messages.tryEmit(context.getString(R.string.msg_already_added))
            return false
        }
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = req.url,
            fileName = req.fileName.trim(),
            mime = req.mime,
            totalBytes = if (req.size > 0) req.size else -1,
            status = Status.QUEUED,
            segments = emptyList(),
            resumable = false,
            createdAt = System.currentTimeMillis(),
            error = null,
            savedUri = null,
            maxSegments = settings.segments.value,
            userAgent = req.userAgent.ifBlank { DEFAULT_UA },
            referer = req.referer,
            cookie = req.cookie
        )
        _items.update { listOf(item) + it }
        persist()
        schedule()
        return true
    }

    fun pause(id: String) {
        update(id) {
            if (it.status == Status.DOWNLOADING || it.status == Status.QUEUED) it.copy(status = Status.PAUSED) else it
        }
        jobs[id]?.cancel()
        persist()
    }

    fun resume(id: String) {
        update(id) {
            if (it.status == Status.PAUSED || it.status == Status.FAILED) it.copy(status = Status.QUEUED, error = null) else it
        }
        persist()
        schedule()
    }

    fun pauseAll() {
        _items.value.filter { it.status == Status.DOWNLOADING || it.status == Status.QUEUED }
            .forEach { pause(it.id) }
    }

    fun resumeAll() {
        _items.value.filter { it.status == Status.PAUSED || it.status == Status.FAILED }
            .forEach { resume(it.id) }
    }

    fun delete(id: String, deleteFile: Boolean) {
        jobs[id]?.cancel()
        val item = get(id)
        _items.update { list -> list.filterNot { it.id == id } }
        File(partsDir, "$id.part").delete()
        if (deleteFile && item?.savedUri != null) {
            runCatching { context.contentResolver.delete(Uri.parse(item.savedUri), null, null) }
        }
        persist()
        schedule()
    }

    /**
     * Replace the source of an unfinished download with a fresh link.
     * The new link must point to a file of the same size, otherwise the parts would be corrupted.
     */
    fun refreshLink(id: String, newUrl: String, userAgent: String, referer: String, cookie: String) {
        val item = get(id) ?: return
        if (newUrl.toHttpUrlOrNull() == null) {
            messages.tryEmit(context.getString(R.string.msg_invalid_link))
            return
        }
        if (item.status == Status.DOWNLOADING || item.status == Status.QUEUED) pause(id)
        scope.launch(Dispatchers.IO) {
            try {
                val candidate = item.copy(
                    url = newUrl,
                    userAgent = userAgent.ifBlank { item.userAgent },
                    referer = referer.ifBlank { item.referer },
                    cookie = cookie
                )
                val probed = probe(candidate)
                if (item.totalBytes > 0 && probed.totalBytes > 0 && probed.totalBytes != item.totalBytes) {
                    messages.tryEmit(context.getString(R.string.err_size_changed))
                    return@launch
                }
                if (item.resumable && !probed.resumable) {
                    messages.tryEmit(context.getString(R.string.err_not_resumable))
                    return@launch
                }
                update(id) {
                    it.copy(
                        url = newUrl,
                        userAgent = candidate.userAgent,
                        referer = candidate.referer,
                        cookie = candidate.cookie,
                        status = Status.QUEUED,
                        error = null
                    )
                }
                persist()
                schedule()
                messages.tryEmit(context.getString(R.string.msg_link_updated))
            } catch (e: Exception) {
                messages.tryEmit(describe(e))
            }
        }
    }

    // ------------------------------------------------------------------ scheduling

    @Synchronized
    private fun schedule() {
        val max = settings.maxConcurrent.value.coerceAtLeast(1)
        var active = _items.value.count { it.status == Status.DOWNLOADING || it.status == Status.SAVING }
        val ready = _items.value
            .filter { it.status == Status.QUEUED && !jobs.containsKey(it.id) }
            .sortedBy { it.createdAt }
        for (item in ready) {
            if (active >= max) break
            update(item.id) { it.copy(status = Status.DOWNLOADING) }
            start(item.id)
            active++
        }
        if (_items.value.any { it.isActive }) ensureService()
    }

    private fun start(id: String) {
        if (jobs.containsKey(id)) return
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                execute(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e)
            } finally {
                flushProgress(id)
                live.remove(id)
                speedMap.remove(id)
                publishSpeeds()
                jobs.remove(id)
                persist()
                schedule()
            }
        }
        jobs[id] = job
        job.start()
    }

    private fun ensureService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ download pipeline

    private suspend fun execute(id: String) {
        var item = get(id) ?: return
        if (item.segments.isEmpty()) {
            val probed = probe(item)
            update(id) {
                it.copy(
                    fileName = probed.fileName,
                    mime = probed.mime,
                    totalBytes = probed.totalBytes,
                    segments = probed.segments,
                    resumable = probed.resumable
                )
            }
            item = get(id) ?: return
        }
        currentCoroutineContext().ensureActive()

        val file = File(partsDir, "$id.part")
        if (!item.resumable || !file.exists()) {
            // Cannot continue: start from byte 0 again.
            item = item.copy(segments = item.segments.map { it.copy(downloaded = 0) })
            file.delete()
            RandomAccessFile(file, "rw").use { if (item.totalBytes > 0) it.setLength(item.totalBytes) }
        }

        val cur = item
        val segs = cur.segments
        val counters = Array(segs.size) { AtomicLong(segs[it].downloaded) }
        live[id] = counters

        coroutineScope {
            val ticker = launch { tickLoop(id, counters) }
            val workers = segs.indices
                .filter { i -> segs[i].end < 0 || counters[i].get() < segs[i].length }
                .map { i -> async { downloadSegment(cur, i, counters[i], file) } }
            workers.awaitAll()
            ticker.cancel()
        }

        val downloaded = counters.sumOf { it.get() }
        update(id) {
            it.copy(
                segments = it.segments.mapIndexed { i, s -> s.copy(downloaded = counters[i].get()) },
                totalBytes = if (it.totalBytes > 0) it.totalBytes else downloaded
            )
        }
        if (cur.totalBytes > 0 && downloaded < cur.totalBytes) throw IOException("Incomplete download")

        update(id) { it.copy(status = Status.SAVING) }
        val name = get(id)?.fileName ?: cur.fileName
        val uri = moveToDownloads(file, name, cur.mime.ifBlank { "application/octet-stream" })
        update(id) { it.copy(status = Status.COMPLETED, savedUri = uri.toString(), error = null, segments = emptyList()) }
        persist()
        get(id)?.let { Notifier.complete(context, it) }
    }

    private suspend fun tickLoop(id: String, counters: Array<AtomicLong>) {
        var last = counters.sumOf { it.get() }
        var lastT = System.nanoTime()
        var smooth = 0.0
        var n = 0
        while (true) {
            delay(500)
            val now = System.nanoTime()
            val cur = counters.sumOf { it.get() }
            val inst = (cur - last) * 1e9 / (now - lastT).coerceAtLeast(1)
            smooth = if (smooth == 0.0) inst else smooth * 0.6 + inst * 0.4
            last = cur
            lastT = now
            speedMap[id] = smooth.toLong()
            publishSpeeds()
            update(id) { item ->
                if (item.segments.size == counters.size)
                    item.copy(segments = item.segments.mapIndexed { i, s -> s.copy(downloaded = counters[i].get()) })
                else item
            }
            if (++n % 6 == 0) persist()
        }
    }

    private suspend fun downloadSegment(item: DownloadItem, index: Int, counter: AtomicLong, file: File) {
        val seg = item.segments[index]
        var attempt = 0
        val buf = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!item.resumable) counter.set(0)
            val from = seg.start + counter.get()
            try {
                val rb = Request.Builder()
                    .url(item.url)
                    .header("User-Agent", item.userAgent)
                    .header("Accept-Encoding", "identity")
                if (item.referer.isNotBlank()) rb.header("Referer", item.referer)
                if (item.cookie.isNotBlank()) rb.header("Cookie", item.cookie)
                if (item.resumable) {
                    rb.header("Range", "bytes=$from-" + (if (seg.end >= 0) seg.end.toString() else ""))
                }
                val call = client.newCall(rb.build())
                val handle = currentCoroutineContext()[Job]!!.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) throw HttpException(resp.code)
                        if (item.resumable && resp.code != 206) throw IOException("Server ignored range request")
                        val body = resp.body ?: throw IOException("Empty response")
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.seek(from)
                            val input = body.byteStream()
                            while (true) {
                                val want = if (seg.end >= 0)
                                    minOf(buf.size.toLong(), seg.end + 1 - (seg.start + counter.get())).toInt()
                                else buf.size
                                if (want <= 0) break
                                val n = input.read(buf, 0, want)
                                if (n < 0) break
                                raf.write(buf, 0, n)
                                counter.addAndGet(n.toLong())
                            }
                        }
                    }
                } finally {
                    handle.dispose()
                }
                if (seg.end >= 0 && seg.start + counter.get() <= seg.end) throw IOException("Connection closed early")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
                if (e is HttpException && e.code in 400..499 && e.code != 408 && e.code != 429) throw e
                attempt++
                if (attempt > 6) throw e
                delay(1000L * attempt)
            }
        }
    }

    /** Asks the server for byte 0 only to learn size, range support, mime type and file name. */
    private fun probe(item: DownloadItem): DownloadItem {
        val rb = Request.Builder()
            .url(item.url)
            .header("User-Agent", item.userAgent)
            .header("Range", "bytes=0-0")
            .header("Accept-Encoding", "identity")
        if (item.referer.isNotBlank()) rb.header("Referer", item.referer)
        if (item.cookie.isNotBlank()) rb.header("Cookie", item.cookie)
        return client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            var total = -1L
            var resumable = false
            if (resp.code == 206) {
                total = resp.header("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
                resumable = total > 0
            } else {
                total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            val mime = (resp.header("Content-Type") ?: item.mime)
                .substringBefore(';').trim().ifBlank { "application/octet-stream" }
            val serverName = fileNameFromDisposition(resp.header("Content-Disposition"))
            var name = item.fileName.ifBlank { serverName ?: nameFromUrl(resp.request.url.toString()) }
            name = ensureExtension(sanitizeFileName(name), mime)
            val segs = if (resumable) split(total, item.maxSegments)
            else listOf(Segment(0, if (total > 0) total - 1 else -1, 0))
            item.copy(
                fileName = name,
                mime = mime,
                totalBytes = total,
                resumable = resumable,
                segments = segs
            )
        }
    }

    private fun split(total: Long, maxSegments: Int): List<Segment> {
        val minPart = 1024L * 1024L
        val n = (total / minPart).toInt().coerceIn(1, maxSegments.coerceAtLeast(1))
        val size = total / n
        return (0 until n).map { i ->
            val s = i * size
            val e = if (i == n - 1) total - 1 else s + size - 1
            Segment(s, e, 0)
        }
    }

    private fun moveToDownloads(src: File, name: String, mime: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FastDM")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create file in Downloads")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                FileInputStream(src).use { it.copyTo(out, 256 * 1024) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        src.delete()
        return uri
    }

    // ------------------------------------------------------------------ helpers

    private fun update(id: String, f: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun flushProgress(id: String) {
        val counters = live[id] ?: return
        update(id) { item ->
            if (item.segments.size == counters.size)
                item.copy(segments = item.segments.mapIndexed { i, s -> s.copy(downloaded = counters[i].get()) })
            else item
        }
    }

    private fun publishSpeeds() {
        _speeds.value = HashMap(speedMap)
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) { store.save(_items.value) }
    }

    private fun fail(id: String, e: Exception) {
        val msg = describe(e)
        update(id) { it.copy(status = Status.FAILED, error = msg) }
        get(id)?.let { Notifier.failed(context, it) }
    }

    private fun describe(e: Throwable): String = when {
        e is HttpException && e.code in listOf(401, 403, 404, 410) -> context.getString(R.string.err_expired, e.code)
        e is HttpException -> context.getString(R.string.err_http, e.code)
        else -> e.message?.take(120) ?: context.getString(R.string.err_network)
    }
}
